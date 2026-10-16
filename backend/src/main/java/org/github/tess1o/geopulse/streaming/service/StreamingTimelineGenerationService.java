package org.github.tess1o.geopulse.streaming.service;

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
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.TimelineRebuildRequest;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitResponse;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.service.trips.StreamingTripPostProcessor;
import org.github.tess1o.geopulse.streaming.service.boat.BoatSetupService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.merge.MovementTimelineMerger;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.trips.service.TripVisitAutoMatchService;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.insight.service.BadgeRecalculationService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class StreamingTimelineGenerationService {

    public static final Instant DEFAULT_START_DATE = Instant.parse("1970-01-01T00:00:00Z");

    /** Where a partial regeneration restarts, relative to the earliest affected time. */
    public enum RestartPoint {
        /** At the latest stay before the affected time; everything before it is kept. */
        LATEST_STAY,
        /**
         * At the stay before the latest stay before the affected time, keeping that stay's start; everything before
         * is kept. With fewer stays, everything is regenerated.
         * <p>
         * The parts a regeneration keeps are never revisited, and a restart at the latest stay drifts from a full
         * rebuild in two ways, both seen when replaying recorded and generated days:
         * <ul>
         *     <li>Whether a trip between two stays is real, or noise between two stays at the same place that a full
         *     rebuild merges, often only becomes clear once the second stay has grown. Keeping the stay before it in
         *     the regenerated part leaves that decision open.</li>
         *     <li>Generation starts blank at the restart. A stay's first points can be the last, still moving fixes
         *     of the arrival; a full rebuild reaches them in trip mode and counts them to the stay, a blank start may
         *     not. Everything new lies after the restart stay, and its start was decided when it was the latest stay,
         *     with the trip into it in view, so that start is kept.</li>
         * </ul>
         * The restart stay itself is where the regenerated part begins: post-processing keeps a short trip only when a
         * stay comes before it.
         * <p>
         * Used by the real-time job, which regenerates the same recent stretch again and again, and by the safety net,
         * which compares the result with what was stored.
         */
        BEFORE_PREVIOUS_STAY
    }


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
    TripStaySplitOverrideService tripStaySplitOverrideService;

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
    TimelineRebuildRequestRepository rebuildRequestRepository;

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

    @Transactional
    public Optional<TripStaySplitResponse> resetTripStaySplitOverride(UUID userId, Long overrideId) {
        Optional<TripStaySplitResponse> result = tripStaySplitOverrideService.removeManualOverride(userId, overrideId);
        result.map(TripStaySplitResponse::regenerationStartTime)
                .ifPresent(timestamp -> generateTimelineFromTimestamp(userId, timestamp));
        return result;
    }

    @Transactional
    public Optional<DataGapStayOverrideResponseDTO> resetDataGapStayOverride(UUID userId, Long overrideId) {
        Optional<DataGapStayOverrideResponseDTO> result = dataGapStayOverrideService.removeManualOverride(userId, overrideId);
        result.map(DataGapStayOverrideResponseDTO::regenerationStartTime)
                .ifPresent(timestamp -> generateTimelineFromTimestamp(userId, timestamp));
        return result;
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
        generateTimelineFromTimestamp(userId, earliestAffectedTimestamp, jobId, trigger, true, RestartPoint.LATEST_STAY);
    }

    @Transactional
    public void generateTimelineFromTimestamp(UUID userId, Instant requestedFrom, UUID jobId, String trigger,
                                              boolean includePendingRebuild) {
        generateTimelineFromTimestamp(userId, requestedFrom, jobId, trigger, includePendingRebuild,
                RestartPoint.LATEST_STAY);
    }

    /**
     * Regenerates the timeline from {@code restartPoint} relative to {@code requestedFrom}.
     * <p>
     * With {@code includePendingRebuild}, the user's pending rebuild request (see
     * {@link TimelineRebuildRequestRepository}) is covered too: generation starts from whichever is earlier, and the
     * request is cleared on success unless it changed while this generation ran. Only the real-time job passes
     * false, to keep the latest stays updating while a request keeps failing.
     */
    @Transactional
    public void generateTimelineFromTimestamp(UUID userId, Instant requestedFrom, UUID jobId, String trigger,
                                              boolean includePendingRebuild, RestartPoint restartPoint) {
        log.info("Starting timeline regeneration for user {} from timestamp {}", userId, requestedFrom);
        long startTime = System.currentTimeMillis();
        long metricsStart = metricsStart();
        String result = "success";

        // Step 1: Acquiring lock (5%)
        updateProgress(jobId, "acquiringLock", "Acquiring timeline lock", 1, 5, null);

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

        // Read after taking the lock, so a generation that finished just before is already reflected.
        TimelineRebuildRequest pendingRebuild = includePendingRebuild
                ? rebuildRequestRepository.find(userId).orElse(null)
                : null;
        boolean extendedByPendingRebuild = pendingRebuild != null && pendingRebuild.rebuildFrom().isBefore(requestedFrom);
        Instant earliestAffectedTimestamp = extendedByPendingRebuild ? pendingRebuild.rebuildFrom() : requestedFrom;
        if (extendedByPendingRebuild) {
            log.info("Timeline regeneration for user {} also covers its pending rebuild from {}", userId,
                    earliestAffectedTimestamp);
        }
        boolean pendingRebuildFinished = false;

        try {
            // Step 2: Cleaning up old data (10%)
            updateProgress(jobId, "cleaningUp", "Cleaning up old timeline data", 2, 10, null);

            // Clean up from the restart point (see RestartPoint)
            stageStart = metricsStart();
            Restart restart = deleteFromRestartPoint(userId, earliestAffectedTimestamp, restartPoint);
            Instant regenerationStartTime = restart.from();
            recordTimelineStage(stageStart, trigger, "cleanup", "success");

            stageStart = metricsStart();
            TimelineConfig config = configurationProvider.getConfigurationForUser(userId);
            recordTimelineStage(stageStart, trigger, "config", "success");

            // Step 3: Prepare GPS data processing (check count and create streaming iterator)
            updateProgress(jobId, "preparingGpsProcessing", "Preparing GPS data processing", 3, 25, null);

            stageStart = metricsStart();
            Long estimatedCount = gpsPointRepository.estimatePointCount(userId, regenerationStartTime);
            recordTimelineStage(stageStart, trigger, "count_points", "success");

            if (estimatedCount == null || estimatedCount == 0) {
                result = "no_points";
                log.debug("No points to process for user {} from timestamp {}", userId, regenerationStartTime);
                updateProgress(jobId, "noGpsData", "No GPS data to process", 9, 100, null);
                completeJob(jobId);
                // Even if no new points, check for ongoing data gap
                dataGapService.checkAndCreateOngoingDataGap(userId, config);
                pendingRebuildFinished = true;
                finishPendingRebuild(userId, pendingRebuild, regenerationStartTime);
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

            updateProgress(jobId, "readyToProcess", "Ready to process " + estimatedCount + " GPS points", 3, 35,
                    Map.of("totalGpsPoints", estimatedCount));

            // Step 4: Process GPS points using streaming approach (loads and processes in chunks)
            updateProgress(jobId, "processingStateMachine", "Processing GPS points through state machine", 4, 40, null);

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
            updateProgress(jobId, "postProcessingTrips", "Post-processing trips and validating detections", 5, 70, null);

            long tripPostProcessingStartNanos = System.nanoTime();
            List<TimelineEvent> events = keepRestartStayStart(userId, tripPostProcessor.postProcessTrips(
                    userId,
                    rawEvents,
                    config,
                    environmentDatasetVersion
            ), restart);
            recordTimelineStage(tripPostProcessingStartNanos, trigger, "trip_postprocess", "success");
            countTimelineEvents(trigger, "postprocessed", events.size());
            log.info("Timeline trip post-processing completed for user {} in {} ms (events={})",
                    userId, elapsedMillis(tripPostProcessingStartNanos), events.size());

            RawTimeline generatedTimeline = null;

            if (!events.isEmpty()) {
                // Create RawTimeline from events to preserve rich GPS data
                RawTimeline rawTimeline = RawTimeline.fromEvents(userId, events);

                // Step 6: Merging and simplification (75%)
                // Apply optional merge pass on raw objects (includes same-location integrity merge behavior)
                // when merge is enabled by configuration.
                if (config.getIsMergeEnabled()) {
                    updateProgress(jobId, "mergingTimeline", "Merging timeline", 6, 75, null);
                    stageStart = metricsStart();
                    rawTimeline = timelineMerger.mergeSameNamedLocations(config, rawTimeline);
                    recordTimelineStage(stageStart, trigger, "merge", "success");
                }

                generatedTimeline = rawTimeline;

                // Step 7: Persisting timeline to database (80%)
                updateProgress(jobId, "persistingTimeline", "Persisting timeline events to database", 7, 80, null);

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

                // Re-apply manual Trip -> Stay -> Trip splits before movement overrides reattach.
                stageStart = metricsStart();
                tripStaySplitOverrideService.reapplyManualOverrides(userId);
                recordTimelineStage(stageStart, trigger, "trip_split_overrides", "success");
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
            updateProgress(jobId, "detectingDataGaps", "Detecting data gaps", 8, 90, null);

            stageStart = metricsStart();
            dataGapService.checkAndCreateOngoingDataGap(userId, config);
            recordTimelineStage(stageStart, trigger, "data_gap", "success");

            // Step 9: Finalizing (95%)
            updateProgress(jobId, "finalizing", "Finalizing timeline generation", 9, 95, null);

            if (autoApplyVisitMatchingOnRegeneration) {
                stageStart = metricsStart();
                try {
                    updateProgress(jobId, "autoMatchingVisits", "Auto-matching planned visits", 9, 97, null);
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
            TimelineDataRange timelineDataRange = calculateTimelineDataRange(regenerationStartTime, Instant.now(), generatedTimeline);
            fireTimelineDataChanged(userId, timelineDataRange.affectedFrom(), timelineDataRange.affectedTo(), jobId,
                    hasGeneratedTrips(generatedTimeline));
            recordTimelineStage(stageStart, trigger, "timeline_changed_event", "success");

            pendingRebuildFinished = true;
            finishPendingRebuild(userId, pendingRebuild, regenerationStartTime);
        } catch (Exception e) {
            result = "error";
            // Once the request row has been touched by this transaction, counting the failure in a separate
            // transaction would wait on our own lock; the request is then simply rolled back to how it was.
            if (pendingRebuild != null && !pendingRebuildFinished) {
                recordPendingRebuildFailure(userId, e);
            }
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
                updateProgress(jobId, "recalculatingBadges", "Recalculating achievement badges", 9, 99, null);
                badgeRecalculationService.recalculateAllBadgesForUser(userId);
                log.info("Triggered badge recalculation for user {} after timeline regeneration", userId);
            } catch (Exception e) {
                log.error("Failed to recalculate badges for user {} after timeline regeneration: {}",
                        userId, e.getMessage(), e);
                // Don't fail the timeline generation if badge calculation fails
            }

            // Mark job as completed (100%)
            updateProgress(jobId, "completed", "Timeline generation completed", 9, 100, null);
            completeJob(jobId);
        }

        return true;
    }

    /**
     * Where a regeneration for changes from {@code affectedTimestamp} restarts; empty when it has to start from
     * scratch.
     */
    public Optional<Instant> findRestartPoint(UUID userId, Instant affectedTimestamp, RestartPoint restartPoint) {
        return findRestart(userId, affectedTimestamp, restartPoint).map(Restart::from);
    }

    /**
     * Where to restart, and for {@link RestartPoint#BEFORE_PREVIOUS_STAY} where the restart stay ended (otherwise null).
     */
    private record Restart(Instant from, Instant restartStayEnd) {
    }

    private Optional<Restart> findRestart(UUID userId, Instant affectedTimestamp, RestartPoint restartPoint) {
        Optional<TimelineStayEntity> latestStay =
                timelineStayRepository.findLatestByUserIdBeforeTimestamp(userId, affectedTimestamp);
        if (restartPoint == RestartPoint.LATEST_STAY || latestStay.isEmpty()) {
            return latestStay.map(stay -> new Restart(stay.getTimestamp(), null));
        }
        // The restart stay must be one generation produces itself, or it cannot come out as a stay again.
        return timelineStayRepository.findLatestGeneratedStayBefore(userId, latestStay.get().getTimestamp())
                .map(previousStay -> new Restart(previousStay.getTimestamp(),
                        previousStay.getTimestamp().plusSeconds(previousStay.getStayDuration())));
    }

    /**
     * Gives the restart stay back the start it had (see {@link RestartPoint#BEFORE_PREVIOUS_STAY}). A blank start may
     * leave its first arrival fixes to a short trip in front of it, or start it a few fixes later; both are undone.
     * Changes nothing when something else than trips comes before the first stay.
     */
    private List<TimelineEvent> keepRestartStayStart(UUID userId, List<TimelineEvent> events, Restart restart) {
        if (restart.restartStayEnd() == null) {
            return events;
        }
        int firstStayIndex = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof Stay) {
                firstStayIndex = i;
                break;
            }
            if (!(events.get(i) instanceof Trip)) {
                return events;
            }
        }
        if (firstStayIndex < 0) {
            return events;
        }
        Stay stay = (Stay) events.get(firstStayIndex);
        if (!stay.getStartTime().isBefore(restart.restartStayEnd())) {
            // The restart stay did not come out as a stay at all; the kept part no longer fits. A full regeneration
            // on the next real-time pass repairs it.
            log.warn("Restart stay of user {} at {} was not regenerated as a stay; requesting a full regeneration",
                    userId, restart.from());
            rebuildRequestRepository.request(userId, DEFAULT_START_DATE);
            return events;
        }
        if (!stay.getStartTime().isAfter(restart.from())) {
            return events;
        }
        log.debug("Restart stay of user {} kept at {} (regenerated from {})", userId, restart.from(), stay.getStartTime());
        stay.setDuration(stay.getDuration().plus(Duration.between(restart.from(), stay.getStartTime())));
        stay.setStartTime(restart.from());
        return new ArrayList<>(events.subList(firstStayIndex, events.size()));
    }

    /**
     * Deletes the stays, trips and data gaps from the restart point on and returns where to regenerate from.
     */
    private Restart deleteFromRestartPoint(UUID userId, Instant affectedTimestamp, RestartPoint restartPoint) {
        Optional<Restart> restart = findRestart(userId, affectedTimestamp, restartPoint);
        if (restart.isEmpty()) {
            log.debug("No restart point for user {} before {}, falling back to complete regeneration", userId,
                    affectedTimestamp);
            return new Restart(deleteAllTimelineEventsAndStartFromScratch(userId), null);
        }
        Instant from = restart.get().from();
        log.debug("Cleaning up timeline of user {} from {}", userId, from);
        long deletedStays = timelineStayRepository.delete("user.id = :userId and timestamp >= :timestamp",
                Map.of("userId", userId, "timestamp", from));
        long deletedTrips = timelineTripRepository.delete("user.id = :userId and timestamp >= :timestamp",
                Map.of("userId", userId, "timestamp", from));
        long deletedGaps = timelineDataGapRepository.delete("user.id = :userId and startTime >= :timestamp",
                Map.of("userId", userId, "timestamp", from));
        log.debug("Cleaned up {} stays, {} trips and {} data gaps of user {} from {}", deletedStays, deletedTrips,
                deletedGaps, userId, from);
        return restart.get();
    }

    /**
     * Fallback method when no stays are found before the affected timestamp.
     * Deletes all timeline events and starts regeneration from the beginning.
     */
    private Instant deleteAllTimelineEventsAndStartFromScratch(UUID userId) {
        log.debug("Fallback: clearing all timeline data for user {} and starting from scratch", userId);

        // Delete all stays for this user
        long deletedStays = timelineStayRepository.delete("user.id = :userId", Map.of("userId", userId));
        if (deletedStays > 0) {
            log.debug("Deleted {} stays for user {}", deletedStays, userId);
        }

        // Delete all trips for this user
        long deletedTrips = timelineTripRepository.delete("user.id = :userId", Map.of("userId", userId));
        if (deletedTrips > 0) {
            log.debug("Deleted {} trips for user {}", deletedTrips, userId);
        }

        // Delete all data gaps for this user
        long deletedGaps = timelineDataGapRepository.delete("user.id = :userId", Map.of("userId", userId));
        if (deletedGaps > 0) {
            log.debug("Deleted {} data gaps for user {}", deletedGaps, userId);
        }

        // Return early date to ensure we process all GPS points from the beginning
        return DEFAULT_START_DATE;
    }

    /**
     * Clears the pending rebuild this generation covered, then asks for another pass when the latest stay was left
     * without a place name. Must be the last database work of a successful generation: from here on the request row
     * is locked by this transaction.
     */
    private void finishPendingRebuild(UUID userId, TimelineRebuildRequest pendingRebuild, Instant regenerationStartTime) {
        if (pendingRebuild != null && !rebuildRequestRepository.complete(pendingRebuild)) {
            log.debug("Rebuild request for user {} changed during generation; keeping it for the next run", userId);
        }
        requestGeocodingRetryIfNeeded(userId, regenerationStartTime);
    }

    /**
     * When reverse geocoding fails, the stay gets a fallback name and nothing is cached. The real-time job used to
     * retry that for the latest stay simply by rebuilding it every few minutes; now it only rebuilds on request, so
     * the latest stay asks for its own retry.
     */
    private void requestGeocodingRetryIfNeeded(UUID userId, Instant regenerationStartTime) {
        timelineStayRepository.find("user.id = ?1 order by timestamp desc", userId)
                .firstResultOptional()
                .filter(stay -> !stay.getTimestamp().isBefore(regenerationStartTime))
                .filter(stay -> stay.getFavoriteLocation() == null && stay.getGeocodingLocation() == null)
                .ifPresent(stay -> {
                    log.info("Latest stay of user {} at {} has no resolved place; requesting another pass",
                            userId, stay.getTimestamp());
                    rebuildRequestRepository.request(userId, stay.getTimestamp());
                });
    }

    private void recordPendingRebuildFailure(UUID userId, Exception failure) {
        try {
            rebuildRequestRepository.recordFailure(userId, failure.toString());
        } catch (Exception recordError) {
            log.warn("Could not record the failed timeline rebuild for user {}: {}", userId, recordError.getMessage());
        }
    }

    private boolean acquireLock(UUID userId) {
        int updatedRows = UserEntity.update("timelineStatus = :status where id = :userId and timelineStatus = :idleStatus",
                Map.of("status", TimelineStatus.PROCESSING, "userId", userId, "idleStatus", TimelineStatus.IDLE));
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

        updateProgress(jobId, "preparingBoatSetup", "Preparing Boat setup", 3, 25,
                Map.of("boatEvidenceAvailable", false));
        String environmentDatasetVersion = boatSetupService.ensureReadyForUser(
                userId,
                config,
                // `phase` comes from BoatSetupService as free-text (not yet a stable catalog key), so it
                // is passed through as the fallback under a shared generic step key.
                (phase, percentage) -> updateProgress(jobId, "boatEvidencePhase", phase, 3, percentage,
                        Map.of("boatEvidenceAvailable", true))
        );
        updateProgress(jobId, "boatEvidenceReady", "Boat water evidence ready", 3, 35,
                Map.of("boatEvidenceAvailable", true));
        log.info("Boat evidence preparation completed for user {} in {} ms (datasetVersionAvailable={})",
                userId,
                elapsedMillis(boatEvidenceStartNanos),
                environmentDatasetVersion != null);
        return environmentDatasetVersion;
    }

    private void releaseLock(UUID userId) {
        UserEntity.update("timelineStatus = :status where id = :userId",
                Map.of("status", TimelineStatus.IDLE, "userId", userId));
    }

    private void fireTimelineDataChanged(UUID userId, Instant affectedFrom, Instant affectedTo, UUID jobId,
                                         boolean tripsChanged) {
        if (userId == null || affectedFrom == null || affectedTo == null) {
            return;
        }
        timelineDataChangedEvent.fire(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, jobId, tripsChanged));
    }

    static TimelineDataRange calculateTimelineDataRange(Instant fallbackFrom, Instant fallbackTo, RawTimeline rawTimeline) {
        TimelineDataRange range = TimelineDataRange.of(fallbackFrom, fallbackTo);
        if (rawTimeline == null || rawTimeline.isEmpty()) {
            return range;
        }

        if (rawTimeline.getStays() != null) {
            for (var stay : rawTimeline.getStays()) {
                if (stay != null) {
                    range = range.include(stay.getStartTime(), endAt(stay.getStartTime(), stay.getDuration()));
                }
            }
        }
        if (rawTimeline.getTrips() != null) {
            for (var trip : rawTimeline.getTrips()) {
                if (trip != null) {
                    range = range.include(trip.getStartTime(), endAt(trip.getStartTime(), trip.getDuration()));
                }
            }
        }
        if (rawTimeline.getDataGaps() != null) {
            for (var dataGap : rawTimeline.getDataGaps()) {
                if (dataGap != null) {
                    range = range.include(dataGap.getStartTime(), endAt(dataGap.getStartTime(), dataGap.getDuration()));
                }
            }
        }
        return range;
    }

    static boolean hasGeneratedTrips(RawTimeline rawTimeline) {
        return rawTimeline != null && rawTimeline.getTrips() != null
                && rawTimeline.getTrips().stream().anyMatch(trip -> trip != null && trip.getStartTime() != null);
    }

    private static Instant endAt(Instant start, Duration duration) {
        if (start == null) {
            return null;
        }
        return duration == null ? start : start.plus(duration);
    }

    record TimelineDataRange(Instant affectedFrom, Instant affectedTo) {
        static TimelineDataRange of(Instant affectedFrom, Instant affectedTo) {
            return new TimelineDataRange(affectedFrom, affectedTo);
        }

        TimelineDataRange include(Instant start, Instant end) {
            return new TimelineDataRange(earliest(affectedFrom, start), latest(affectedTo, end));
        }

        private static Instant earliest(Instant first, Instant second) {
            if (first == null) return second;
            if (second == null) return first;
            return first.isBefore(second) ? first : second;
        }

        private static Instant latest(Instant first, Instant second) {
            if (first == null) return second;
            if (second == null) return first;
            return first.isAfter(second) ? first : second;
        }
    }

    /**
     * Helper method to update job progress if job tracking is enabled.
     *
     * @param stepKey short suffix appended to the {@code timelineJobs.progressMessages.} catalog
     *                namespace (see {@link TimelineJobProgressService#step})
     * @param fallback English text shown when the active locale has no translation for {@code stepKey}
     *                 yet; also used as interpolation source together with {@code details}
     */
    private void updateProgress(UUID jobId, String stepKey, String fallback, int stepIndex, int percentage, Map<String, Object> details) {
        if (jobId != null) {
            jobProgressService.updateProgress(jobId, TimelineJobProgressService.step(stepKey, fallback, details), stepIndex, percentage, details);
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
