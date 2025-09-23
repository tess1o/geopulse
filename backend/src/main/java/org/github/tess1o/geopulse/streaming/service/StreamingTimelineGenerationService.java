package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.engine.StreamingTimelineProcessor;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.service.trips.StreamingTripPostProcessor;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.merge.MovementTimelineMerger;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.insight.service.BadgeRecalculationService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    PathSimplificationService pathSimplificationService;

    @Inject
    BadgeRecalculationService badgeRecalculationService;

    @Inject
    GpsDataLoader gpsDataLoader;

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
        log.info("Starting timeline regeneration for user {} from timestamp {}", userId, earliestAffectedTimestamp);
        long startTime = System.currentTimeMillis();

        if (!acquireLock(userId)) {
            log.warn("Could not acquire lock for user {}. Timeline regeneration already in progress.", userId);
            throw new TimelineGenerationLockException(userId);
        }

        try {
            // Find latest stay before the affected timestamp and clean up from that point
            Instant regenerationStartTime = deleteFromStayBeforeTimestampAndCleanup(userId, earliestAffectedTimestamp);

            TimelineConfig config = configurationProvider.getConfigurationForUser(userId);

            List<GPSPoint> newPoints = gpsDataLoader.loadGpsPointsForTimeline(userId, regenerationStartTime);

            if (newPoints.isEmpty()) {
                log.debug("No points to process for user {} from timestamp {}", userId, regenerationStartTime);
                // Even if no new points, check for ongoing data gap
                dataGapService.checkAndCreateOngoingDataGap(userId, config);
                return;
            }

            // Load context points for algorithm state
            List<GPSPoint> contextPoints = gpsDataLoader.loadContextPoints(userId, regenerationStartTime);

            // Log memory usage for monitoring
            GpsDataLoader.MemoryStats stats = gpsDataLoader.getMemoryStats(newPoints);
            log.info("Processing timeline for user {} with {} GPS points ({}MB memory) + {} context points",
                    userId, newPoints.size(), String.format("%.1f", stats.estimatedMB), contextPoints.size());

            // Convert to algorithm format (lightweight conversion)

            List<TimelineEvent> rawEvents = processor.processPoints(contextPoints, newPoints, config, userId);

            // Apply trip detection algorithm and validation
            List<TimelineEvent> events = tripPostProcessor.postProcessTrips(rawEvents, config);

            if (!events.isEmpty()) {
                // Create RawTimeline from events to preserve rich GPS data
                RawTimeline rawTimeline = RawTimeline.fromEvents(userId, events);

                // Apply merging on raw objects
                if (config.getIsMergeEnabled()) {
                    rawTimeline = timelineMerger.mergeSameNamedLocations(config, rawTimeline);
                }

                // Apply path simplification on raw objects
                if (isPathSimplificationEnabled(config)) {
                    log.debug("Applying GPS path simplification for user {}", rawTimeline.getUserId());
                    rawTimeline = applyPathSimplification(config, rawTimeline);
                }

                // Persist raw timeline with GPS statistics calculation
                persistenceManager.persistRawTimeline(userId, rawTimeline);
            }

            dataGapService.checkAndCreateOngoingDataGap(userId, config);

            log.info("Successfully completed timeline regeneration for user {} " + "from timestamp {} in {} seconds",
                    userId, earliestAffectedTimestamp, (System.currentTimeMillis() - startTime) / 1000.0d);

        } finally {
            releaseLock(userId);
        }
    }

    @Transactional
    public boolean regenerateFullTimeline(UUID userId) {
        this.generateTimelineFromTimestamp(userId, DEFAULT_START_DATE);
        // Trigger badge recalculation after successful timeline regeneration
        try {
            badgeRecalculationService.recalculateAllBadgesForUser(userId);
            log.info("Triggered badge recalculation for user {} after timeline regeneration", userId);
        } catch (Exception e) {
            log.error("Failed to recalculate badges for user {} after timeline regeneration: {}",
                    userId, e.getMessage(), e);
            // Don't fail the timeline generation if badge calculation fails
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

    /**
     * Apply path simplification to a RawTimeline, preserving rich GPS data.
     */
    private RawTimeline applyPathSimplification(TimelineConfig config, RawTimeline timeline) {
        if (timeline == null || timeline.getTrips() == null || timeline.getTrips().isEmpty()) {
            return timeline;
        }

        log.debug("Simplifying paths for {} trips in raw timeline", timeline.getTrips().size());

        List<Trip> simplifiedTrips = timeline.getTrips().stream()
                .map(trip -> simplifyTripPath(trip, config))
                .toList();

        return RawTimeline.builder()
                .userId(timeline.getUserId())
                .stays(timeline.getStays())
                .trips(simplifiedTrips)
                .dataGaps(timeline.getDataGaps())
                .build();
    }

    /**
     * Simplify the path of a single Trip domain object.
     * Preserves rich GPS data (speed, accuracy) throughout simplification.
     */
    private Trip simplifyTripPath(Trip trip, TimelineConfig config) {

        if (trip.getPath() == null || trip.getPath().isEmpty()) {
            return trip; // No path to simplify
        }

        List<? extends GpsPoint> simplifiedPoints = pathSimplificationService.simplify(trip.getPath(), config);

        @SuppressWarnings("unchecked")
        List<GPSPoint> simplifiedDomainPoints = (List<GPSPoint>) simplifiedPoints;

        return Trip.builder()
                .startTime(trip.getStartTime())
                .duration(trip.getDuration())
                .path(simplifiedDomainPoints)
                .statistics(trip.getStatistics())
                .distanceMeters(trip.getDistanceMeters())
                .tripType(trip.getTripType())
                .build();
    }

    private boolean isPathSimplificationEnabled(TimelineConfig config) {
        return config.getPathSimplificationEnabled() != null &&
                config.getPathSimplificationEnabled() &&
                config.getPathSimplificationTolerance() != null &&
                config.getPathSimplificationTolerance() > 0;
    }

    private boolean acquireLock(UUID userId) {
        int updatedRows = UserEntity.update("timelineStatus = :status where id = :userId and timelineStatus = :idleStatus",
                Parameters.with("status", TimelineStatus.PROCESSING)
                        .and("userId", userId)
                        .and("idleStatus", TimelineStatus.IDLE));
        return updatedRows > 0;
    }

    private void releaseLock(UUID userId) {
        UserEntity.update("timelineStatus = :status where id = :userId",
                Parameters.with("status", TimelineStatus.IDLE).and("userId", userId));
    }
}