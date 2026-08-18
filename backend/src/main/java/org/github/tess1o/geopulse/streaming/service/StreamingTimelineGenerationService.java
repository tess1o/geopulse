package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.engine.StreamingTimelineProcessor;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.streaming.iterator.StreamingGpsIterable;
import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.service.trips.StreamingTripPostProcessor;
import org.github.tess1o.geopulse.streaming.service.boat.BoatSetupService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.merge.MovementTimelineMerger;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.trips.service.TripVisitAutoMatchService;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.insight.service.BadgeRecalculationService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class StreamingTimelineGenerationService {

    public static final Instant DEFAULT_START_DATE = Instant.parse("1970-01-01T00:00:00Z");

    @Inject
    TimelineConfigurationProvider configurationProvider;

    @Inject
    StreamingTimelineProcessor processor;

    @Inject
    StreamingPersistenceManager persistenceManager;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    MovementTimelineMerger timelineMerger;

    @Inject
    StreamingTripPostProcessor tripPostProcessor;

    @Inject
    StreamingDataGapService dataGapService;

    @Inject
    TripMovementTypeOverrideService tripMovementTypeOverrideService;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineNoteService timelineNoteService;

    @Inject
    BadgeRecalculationService badgeRecalculationService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    BoatSetupService boatSetupService;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    TripVisitAutoMatchService tripVisitAutoMatchService;

    @Inject
    Event<TimelineDataChangedEvent> timelineDataChangedEvent;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @ConfigProperty(name = "geopulse.trip.visit-matching.auto-apply-on-timeline-regeneration", defaultValue = "true")
    boolean autoApplyVisitMatchingOnRegeneration;

    /**
     * Regenerates timeline starting from the earliest affected GPS point timestamp.
     * This method finds the latest stay before the affected timestamp, deletes all timeline
     * events after that point, and regenerates the timeline.
     *
     * @param userId                    The user ID
     * @param earliestAffectedTimestamp The timestamp of the earliest GPS point that was affected (edited/deleted)
     */
    @Transactional
    public void generateTimelineFromTimestamp(UUID userId, Instant earliestAffectedTimestamp) {
        generateTimelineFromTimestamp(userId, earliestAffectedTimestamp, null, "unknown");
    }

    /**
     * Regenerates timeline starting from the earliest affected GPS point timestamp with progress tracking.
     * This method finds the latest stay before the affected timestamp, deletes all timeline
     * events after that point, and regenerates the timeline.
     *
     * @param userId                    The user ID
     * @param earliestAffectedTimestamp The timestamp of the earliest GPS point that was affected (edited/deleted)
     * @param jobId                     Optional job ID for progress tracking
     */
    @Transactional
    public void generateTimelineFromTimestamp(UUID userId, Instant earliestAffectedTimestamp, UUID jobId) {
        generateTimelineFromTimestamp(userId, earliestAffectedTimestamp, jobId, "unknown");
    }

    @Transactional
    public void generateTimelineFromTimestamp(UUID userId, Instant earliestAffectedTimestamp, String trigger) {
        generateTimelineFromTimestamp(userId, earliestAffectedTimestamp, null, trigger);
    }

    @Transactional
    public void generateTimelineFromTimestamp(UUID userId, Instant earliestAffectedTimestamp, UUID jobId, String trigger) {
        log.info("Starting timeline regeneration for user {} from timestamp {}", userId, earliestAffectedTimestamp);
        long startTime = System.currentTimeMillis();
        long metricsStart = metricsStart();
        String result = "success";

        // Step 1: Acquiring lock (5%)
        updateProgress(jobId, "Acquiring timeline lock", 1, 5, null);

        long stageStart = metricsStart();
        if (!acquireLock(userId)) {
            result = "locked";
            recordTimelineStage(stageStart, trigger, "lock", result);
            log.warn("Could not acquire lock for user {}. Timeline regeneration already in progress.", userId);
            failJob(jobId, "Timeline regeneration already in progress");
            countTimelineRun(trigger, result);
            recordTimelineDuration(metricsStart, trigger, result);
            throw new TimelineGenerationLockException(userId);
        }
        recordTimelineStage(stageStart, trigger, "lock", "success");

        try {
            // Step 2: Cleaning up old data (10%)
            updateProgress(jobId, "Cleaning up old timeline data", 2, 10, null);

            // Find latest stay before the affected timestamp and clean up from that point
            stageStart = metricsStart();
            Instant regenerationStartTime = deleteFromStayBeforeTimestampAndCleanup(userId, earliestAffectedTimestamp);
            recordTimelineStage(stageStart, trigger, "cleanup", "success");

            stageStart = metricsStart();
            TimelineConfig config = configurationProvider.getConfigurationForUser(userId);
            recordTimelineStage(stageStart, trigger, "config", "success");

            // Step 3: Prepare GPS data processing (check count and create streaming iterator)
            updateProgress(jobId, "Preparing GPS data processing", 3, 25, null);

            stageStart = metricsStart();
            Long estimatedCount = gpsPointRepository.estimatePointCount(userId, regenerationStartTime);
            recordTimelineStage(stageStart, trigger, "count_points", "success");

            if (estimatedCount == null || estimatedCount == 0) {
                result = "no_points";
                log.debug("No points to process for user {} from timestamp {}", userId, regenerationStartTime);
                updateProgress(jobId, "No GPS data to process", 9, 100, null);
                completeJob(jobId);
                // Even if no new points, check for ongoing data gap
                dataGapService.checkAndCreateOngoingDataGap(userId, config);
                return;
            }

            log.info("Estimated {} GPS points to process for user {} using streaming iterator", estimatedCount, userId);
            countTimelineGpsPoints(trigger, estimatedCount);

            stageStart = metricsStart();
            String environmentDatasetVersion = prepareBoatEvidence(userId, config, jobId);
            recordTimelineStage(stageStart, trigger, "boat_prepare", "success");

            // Create streaming iterable (memory-efficient - no loading all points!)
            StreamingGpsIterable gpsStream = new StreamingGpsIterable(
                    gpsPointRepository,
                    userId,
                    regenerationStartTime,
                    10_000,
                    environmentDatasetVersion
            );

            updateProgress(jobId, "Ready to process " + estimatedCount + " GPS points", 3, 35,
                    Map.of("totalGpsPoints", estimatedCount));

            // Step 4: Process GPS points using streaming approach (loads and processes in chunks)
            updateProgress(jobId, "Processing GPS points through state machine", 4, 40, null);

            // Process points using streaming iterator - loads data lazily in 10K chunks
            long stateMachineStartNanos = System.nanoTime();
            List<TimelineEvent> rawEvents = processor.processPoints(gpsStream, config, userId, jobId);
            recordTimelineStage(stateMachineStartNanos, trigger, "state_machine", "success");
            countTimelineEvents(trigger, "raw", rawEvents.size());
            log.info("Timeline state-machine processing completed for user {} in {} ms (rawEvents={})",
                    userId, elapsedMillis(stateMachineStartNanos), rawEvents.size());
            // Note: processor.processPoints() calls finalizationService.populateStayLocations(jobId)
            // which does the reverse geocoding! Progress updates happen inside LocationPointResolver.

            // Step 5: Post-processing trips (70%)
            updateProgress(jobId, "Post-processing trips and validating detections", 5, 70, null);

            long tripPostProcessingStartNanos = System.nanoTime();
            List<TimelineEvent> events = tripPostProcessor.postProcessTrips(
                    userId,
                    rawEvents,
                    config,
                    environmentDatasetVersion
            );
            recordTimelineStage(tripPostProcessingStartNanos, trigger, "trip_postprocess", "success");
            countTimelineEvents(trigger, "postprocessed", events.size());
            log.info("Timeline trip post-processing completed for user {} in {} ms (events={})",
                    userId, elapsedMillis(tripPostProcessingStartNanos), events.size());

            if (!events.isEmpty()) {
                // Create RawTimeline from events to preserve rich GPS data
                RawTimeline rawTimeline = RawTimeline.fromEvents(userId, events);

                // Step 6: Merging and simplification (75%)
                // Apply optional merge pass on raw objects (includes same-location integrity merge behavior)
                // when merge is enabled by configuration.
                if (config.getIsMergeEnabled()) {
                    updateProgress(jobId, "Merging timeline", 6, 75, null);
                    stageStart = metricsStart();
                    rawTimeline = timelineMerger.mergeSameNamedLocations(config, rawTimeline);
                    recordTimelineStage(stageStart, trigger, "merge", "success");
                }

                // Step 7: Persisting timeline to database (80%)
                updateProgress(jobId, "Persisting timeline events to database", 7, 80, null);

                // Persist raw timeline with GPS statistics calculation
                long persistenceStartNanos = System.nanoTime();
                persistenceManager.persistRawTimeline(userId, rawTimeline);
                recordTimelineStage(persistenceStartNanos, trigger, "persist", "success");
                log.info("Timeline persistence completed for user {} in {} ms (stays={}, trips={}, gaps={}, events={})",
                        userId,
                        elapsedMillis(persistenceStartNanos),
                        rawTimeline.getStays().size(),
                        rawTimeline.getTrips().size(),
                        rawTimeline.getDataGaps().size(),
                        rawTimeline.getTotalEventCount());

                // Re-attach manual movement-type overrides to regenerated trips.
                stageStart = metricsStart();
                tripMovementTypeOverrideService.reapplyManualOverrides(userId);
                recordTimelineStage(stageStart, trigger, "movement_overrides", "success");
                // Re-attach manual Data Gap -> Stay conversions to regenerated gaps.
                stageStart = metricsStart();
                dataGapStayOverrideService.reapplyManualOverrides(userId);
                recordTimelineStage(stageStart, trigger, "data_gap_overrides", "success");
                // Re-attach durable GeoPulse notes to regenerated stays/trips where possible.
                stageStart = metricsStart();
                timelineNoteService.reattachAnchoredNotes(userId);
                recordTimelineStage(stageStart, trigger, "notes", "success");
            } else {
                log.info("Timeline persistence skipped for user {} because post-processing returned no events", userId);
            }

            // Step 8: Data gap detection (90%)
            updateProgress(jobId, "Detecting data gaps", 8, 90, null);

            stageStart = metricsStart();
            dataGapService.checkAndCreateOngoingDataGap(userId, config);
            recordTimelineStage(stageStart, trigger, "data_gap", "success");

            // Step 9: Finalizing (95%)
            updateProgress(jobId, "Finalizing timeline generation", 9, 95, null);

            if (autoApplyVisitMatchingOnRegeneration) {
                stageStart = metricsStart();
                try {
                    updateProgress(jobId, "Auto-matching planned visits", 9, 97, null);
                    tripVisitAutoMatchService.evaluateAllTrips(userId, true);
                    recordTimelineStage(stageStart, trigger, "visit_matching", "success");
                } catch (Exception ex) {
                    recordTimelineStage(stageStart, trigger, "visit_matching", "error");
                    log.error("Failed to auto-match trip plan items for user {} after timeline regeneration: {}",
                            userId, ex.getMessage(), ex);
                }
            }

            log.info("Successfully completed timeline regeneration for user {} " + "from timestamp {} in {} seconds",
                    userId, earliestAffectedTimestamp, (System.currentTimeMillis() - startTime) / 1000.0d);
            stageStart = metricsStart();
            fireTimelineDataChanged(userId, regenerationStartTime, Instant.now(), jobId);
            recordTimelineStage(stageStart, trigger, "weather_event", "success");

        } catch (Exception e) {
            result = "error";
            failJob(jobId, "Timeline generation failed: " + e.getMessage());
            throw new RuntimeException("Timeline generation failed", e);
        } finally {
            countTimelineRun(trigger, result);
            recordTimelineDuration(metricsStart, trigger, result);
            releaseLock(userId);
        }
    }

    @Transactional
    public boolean regenerateFullTimeline(UUID userId) {
        return regenerateFullTimeline(userId, null);
    }

    public boolean regenerateFullTimeline(UUID userId, UUID jobId) {
        // Note: generateTimelineFromTimestamp() is @Transactional
        // Completion logic (badge recalc, completeJob) must run AFTER that transaction commits
        this.generateTimelineFromTimestamp(userId, DEFAULT_START_DATE, jobId);

        // Complete the job OUTSIDE the transactional method
        // Badge recalculation and job completion must happen after transaction commits
        if (jobId != null) {
            try {
                updateProgress(jobId, "Recalculating achievement badges", 9, 99, null);
                badgeRecalculationService.recalculateAllBadgesForUser(userId);
                log.info("Triggered badge recalculation for user {} after timeline regeneration", userId);
            } catch (Exception e) {
                log.error("Failed to recalculate badges for user {} after timeline regeneration: {}",
                        userId, e.getMessage(), e);
                // Don't fail the timeline generation if badge calculation fails
            }

            // Mark job as completed (100%)
            updateProgress(jobId, "Timeline generation completed", 9, 100, null);
            completeJob(jobId);
        }

        return true;
    }

    /**
     * Finds the latest stay before the specified timestamp, deletes it and all timeline events
     * (stays, trips, gaps) after that timestamp, and returns the starting point for regeneration.
     *
     * @param userId            The user ID
     * @param affectedTimestamp The timestamp before which to find the latest stay
     * @return The timestamp from which to start timeline regeneration
     */
    private Instant deleteFromStayBeforeTimestampAndCleanup(UUID userId, Instant affectedTimestamp) {
        // Find latest stay before the affected timestamp
        Optional<TimelineStayEntity> stayBeforeAffected = timelineStayRepository.findLatestByUserIdBeforeTimestamp(userId, affectedTimestamp);

        if (stayBeforeAffected.isPresent()) {
            Instant stayStartTime = stayBeforeAffected.get().getTimestamp();
            log.debug("Deleting stay for user {} at {} and cleaning up all events after this time", userId, stayStartTime);

            // Delete all stays from this timestamp forward (including the anchor stay)
            long deletedStays = timelineStayRepository.delete("user.id = :userId and timestamp >= :timestamp",
                    Parameters.with("userId", userId).and("timestamp", stayStartTime));
            if (deletedStays > 0) {
                log.debug("Cleaned up {} stays starting from timestamp {}", deletedStays, stayStartTime);
            }

            // Delete all trips from this timestamp forward
            long deletedTrips = timelineTripRepository.delete("user.id = :userId and timestamp >= :timestamp",
                    Parameters.with("userId", userId).and("timestamp", stayStartTime));
            if (deletedTrips > 0) {
                log.debug("Cleaned up {} trips starting from timestamp {}", deletedTrips, stayStartTime);
            }

            // Delete all data gaps from this timestamp forward
            long deletedGaps = timelineDataGapRepository.delete("user.id = :userId and startTime >= :timestamp",
                    Parameters.with("userId", userId).and("timestamp", stayStartTime));
            if (deletedGaps > 0) {
                log.debug("Cleaned up {} data gaps starting from timestamp {}", deletedGaps, stayStartTime);
            }

            return stayStartTime;
        } else {
            // No stays found before the affected timestamp - fallback to complete regeneration
            log.debug("No stays found for user {} before timestamp {}, falling back to complete regeneration", userId, affectedTimestamp);
            return deleteAllTimelineEventsAndStartFromScratch(userId);
        }
    }

    /**
     * Fallback method when no stays are found before the affected timestamp.
     * Deletes all timeline events and starts regeneration from the beginning.
     */
    private Instant deleteAllTimelineEventsAndStartFromScratch(UUID userId) {
        log.debug("Fallback: clearing all timeline data for user {} and starting from scratch", userId);

        // Delete all stays for this user
        long deletedStays = timelineStayRepository.delete("user.id = :userId", Parameters.with("userId", userId));
        if (deletedStays > 0) {
            log.debug("Deleted {} stays for user {}", deletedStays, userId);
        }

        // Delete all trips for this user
        long deletedTrips = timelineTripRepository.delete("user.id = :userId", Parameters.with("userId", userId));
        if (deletedTrips > 0) {
            log.debug("Deleted {} trips for user {}", deletedTrips, userId);
        }

        // Delete all data gaps for this user
        long deletedGaps = timelineDataGapRepository.delete("user.id = :userId", Parameters.with("userId", userId));
        if (deletedGaps > 0) {
            log.debug("Deleted {} data gaps for user {}", deletedGaps, userId);
        }

        // Return early date to ensure we process all GPS points from the beginning
        return DEFAULT_START_DATE;
    }

    private boolean acquireLock(UUID userId) {
        int updatedRows = UserEntity.update("timelineStatus = :status where id = :userId and timelineStatus = :idleStatus",
                Parameters.with("status", TimelineStatus.PROCESSING)
                        .and("userId", userId)
                        .and("idleStatus", TimelineStatus.IDLE));
        return updatedRows > 0;
    }

    private String prepareBoatEvidence(UUID userId,
                                       TimelineConfig config,
                                       UUID jobId) {
        long boatEvidenceStartNanos = System.nanoTime();
        if (boatSetupService == null || !boatSetupService.shouldUseBoatSetup(config)) {
            log.info("Boat evidence preparation skipped for user {} in {} ms", userId, elapsedMillis(boatEvidenceStartNanos));
            return null;
        }

        updateProgress(jobId, "Preparing Boat setup", 3, 25,
                Map.of("boatEvidenceAvailable", false));
        String environmentDatasetVersion = boatSetupService.ensureReadyForUser(
                userId,
                config,
                (phase, percentage) -> updateProgress(jobId, phase, 3, percentage,
                        Map.of("boatEvidenceAvailable", true))
        );
        updateProgress(jobId, "Boat water evidence ready", 3, 35,
                Map.of("boatEvidenceAvailable", true));
        log.info("Boat evidence preparation completed for user {} in {} ms (datasetVersionAvailable={})",
                userId,
                elapsedMillis(boatEvidenceStartNanos),
                environmentDatasetVersion != null);
        return environmentDatasetVersion;
    }

    private void releaseLock(UUID userId) {
        UserEntity.update("timelineStatus = :status where id = :userId",
                Parameters.with("status", TimelineStatus.IDLE).and("userId", userId));
    }

    private void fireTimelineDataChanged(UUID userId, Instant affectedFrom, Instant affectedTo, UUID jobId) {
        if (userId == null || affectedFrom == null || affectedTo == null) {
            return;
        }
        timelineDataChangedEvent.fire(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, jobId));
    }

    /**
     * Helper method to update job progress if job tracking is enabled
     */
    private void updateProgress(UUID jobId, String step, int stepIndex, int percentage, Map<String, Object> details) {
        if (jobId != null) {
            jobProgressService.updateProgress(jobId, step, stepIndex, percentage, details);
        }
    }

    /**
     * Helper method to complete a job if job tracking is enabled
     */
    private void completeJob(UUID jobId) {
        if (jobId != null) {
            jobProgressService.completeJob(jobId);
        }
    }

    /**
     * Helper method to fail a job if job tracking is enabled
     */
    private void failJob(UUID jobId, String errorMessage) {
        if (jobId != null) {
            jobProgressService.failJob(jobId, errorMessage);
        }
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordTimelineDuration(long startedAtNanos, String trigger, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.timeline.regeneration.duration", startedAtNanos,
                "component", "timeline",
                "trigger", normalizeTrigger(trigger),
                "result", result);
    }

    private void recordTimelineStage(long startedAtNanos, String trigger, String stage, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.timeline.regeneration.stage.duration", startedAtNanos,
                "component", "timeline",
                "trigger", normalizeTrigger(trigger),
                "stage", stage,
                "result", result);
    }

    private void countTimelineRun(String trigger, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.increment("geopulse.timeline.regeneration.runs",
                "component", "timeline",
                "trigger", normalizeTrigger(trigger),
                "result", result);
    }

    private void countTimelineGpsPoints(String trigger, long count) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.timeline.regeneration.gps_points", count,
                "component", "timeline",
                "trigger", normalizeTrigger(trigger),
                "result", "processed");
    }

    private void countTimelineEvents(String trigger, String eventStage, long count) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.timeline.regeneration.events", count,
                "component", "timeline",
                "trigger", normalizeTrigger(trigger),
                "stage", eventStage,
                "result", "processed");
    }

    private String normalizeTrigger(String trigger) {
        return trigger == null || trigger.isBlank() ? "unknown" : trigger;
    }
}
