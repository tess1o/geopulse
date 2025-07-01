package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Smart timeline query service that handles both cached and live timeline data.
 * <p>
 * Key behaviors:
 * - Today's timeline: Always generated live from GPS data
 * - Past days: Use cached data if valid, otherwise regenerate
 * - Version-aware caching with automatic staleness detection
 * - Background regeneration support
 */
@ApplicationScoped
@Slf4j
public class TimelineQueryService {

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineVersionService versionService;

    @Inject
    TimelinePersistenceService persistenceService;

    @Inject
    TimelinePersistenceMapper persistenceMapper;

    @Inject
    TimelineService liveTimelineService;

    @Inject
    TimelineRegenerationService regenerationService;

    // Track background regeneration in progress to avoid duplicate work
    private final ConcurrentMap<String, Boolean> regenerationInProgress = new ConcurrentHashMap<>();

    /**
     * Get timeline for a user within a specific time range.
     * Automatically chooses between live generation and cached data.
     *
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @return timeline DTO with appropriate data source indication
     */
    public MovementTimelineDTO getTimeline(UUID userId, Instant startTime, Instant endTime) {
        // Use UTC for all date operations 
        LocalDate startDate = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Check if this spans today - if so, always generate live
        if (!startDate.isBefore(today)) {
            log.debug("Generating live timeline for user {} as date range includes today", userId);
            return generateLiveTimeline(userId, startTime, endTime);
        }

        // Past days: use persistence strategy
        return getPersistedTimeline(userId, startTime, endTime);
    }

    /**
     * Get timeline from cached data or regenerate if stale.
     */
    private MovementTimelineDTO getPersistedTimeline(UUID userId, Instant startTime, Instant endTime) {
        // For timeline caching, we check if this request can be satisfied by existing cached data
        // Since cached data is stored by actual occurrence time, we need to check for overlapping data

        // Check if we have any cached data that overlaps with the requested time range
        List<TimelineStayEntity> cachedStays = stayRepository.findByUserAndDateRange(userId, startTime, endTime);
        List<TimelineTripEntity> cachedTrips = tripRepository.findByUserAndDateRange(userId, startTime, endTime);

        // Determine if we have complete coverage for the requested time range
        boolean hasCompleteCoverage = hasCompleteTimelineCoverage(cachedStays, cachedTrips);

        if (!hasCompleteCoverage) {
            log.debug("No complete cached timeline coverage for user {} in range {} to {}, generating fresh", userId, startTime, endTime);
            return generateAndPersistTimeline(userId, startTime, endTime);
        }

        // Check if cached data is still valid (use start time for version generation)
        String currentVersion = versionService.generateTimelineVersion(userId, startTime);

        if (isValidCachedData(cachedStays, cachedTrips, currentVersion)) {
            log.debug("Using valid cached timeline for user {} in range {} to {}", userId, startTime, endTime);
            return buildTimelineFromEntities(userId, cachedStays, cachedTrips, TimelineDataSource.CACHED);
        }

        // Check if regeneration is already in progress
        String timeRangeKey = createTimeRangeKey(startTime, endTime);
        String regenerationKey = userId + ":" + timeRangeKey;
        if (regenerationInProgress.putIfAbsent(regenerationKey, Boolean.TRUE) != null) {
            log.debug("Timeline regeneration already in progress for user {} in range {} to {}, returning stale data", userId, startTime, endTime);
            return buildTimelineWithStaleIndicator(userId, cachedStays, cachedTrips);
        }

        try {
            // Regenerate timeline with fresh data
            log.info("Regenerating stale timeline for user {} in range {} to {}", userId, startTime, endTime);
            return generateAndPersistTimeline(userId, startTime, endTime);
        } finally {
            regenerationInProgress.remove(regenerationKey);
        }
    }

    /**
     * Generate timeline from live GPS data (for today or when no cache available).
     */
    private MovementTimelineDTO generateLiveTimeline(UUID userId, Instant startTime, Instant endTime) {
        try {
            MovementTimelineDTO timeline = liveTimelineService.getMovementTimeline(userId, startTime, endTime);

            // Add data source metadata
            timeline.setDataSource(TimelineDataSource.LIVE);
            timeline.setLastUpdated(Instant.now());
            timeline.setIsStale(false);

            return timeline;
        } catch (Exception e) {
            log.error("Failed to generate live timeline for user {} from {} to {}", userId, startTime, endTime, e);
            return createEmptyTimeline(userId, TimelineDataSource.LIVE);
        }
    }

    /**
     * Generate timeline and persist it if it's a completed past day.
     */
    private MovementTimelineDTO generateAndPersistTimeline(UUID userId, Instant startTime, Instant endTime) {
        try {
            // For custom time ranges, use live generation directly since regeneration service expects single dates
            MovementTimelineDTO timeline = liveTimelineService.getMovementTimeline(userId, startTime, endTime);

            // Add metadata
            timeline.setDataSource(TimelineDataSource.LIVE);
            timeline.setLastUpdated(Instant.now());
            timeline.setIsStale(false);

            // Try to persist if it's a completed past period
            if (persistenceService.shouldPersistTimeline(startTime, endTime)) {
                try {
                    persistenceService.persistTimelineForRange(userId, startTime, endTime, timeline);
                    timeline.setDataSource(TimelineDataSource.CACHED);
                } catch (Exception e) {
                    log.warn("Failed to persist timeline for user {} from {} to {}, continuing with live data", userId, startTime, endTime, e);
                }
            }

            return timeline;
        } catch (Exception e) {
            log.error("Failed to generate timeline for user {} from {} to {}", userId, startTime, endTime, e);
            throw e;
        }
    }


    /**
     * Check if cached timeline data is still valid.
     */
    private boolean isValidCachedData(List<TimelineStayEntity> stays, List<TimelineTripEntity> trips, String currentVersion) {
        // If no data, it's not valid
        if (stays.isEmpty() || trips.isEmpty()) {
            return false;
        }

        // Check if any entity is marked as stale
        boolean hasStaleStays = stays.stream().anyMatch(TimelineStayEntity::getIsStale);
        boolean hasStaleTrips = trips.stream().anyMatch(TimelineTripEntity::getIsStale);

        if (hasStaleStays || hasStaleTrips) {
            log.debug("Found stale entities in cached timeline data");
            return false;
        }

        // Check version hash (use stays for version check, trips should have same version)
        if (!stays.isEmpty()) {
            String cachedVersion = stays.get(0).getTimelineVersion();
            if (cachedVersion == null || !cachedVersion.equals(currentVersion)) {
                log.debug("Timeline version mismatch: cached={}, current={}", cachedVersion, currentVersion);
                return false;
            }
        }

        return true;
    }

    /**
     * Build timeline DTO from persisted entities.
     */
    private MovementTimelineDTO buildTimelineFromEntities(UUID userId, List<TimelineStayEntity> stayEntities,
                                                          List<TimelineTripEntity> tripEntities, TimelineDataSource dataSource) {
        // Convert entities to DTOs
        List<TimelineStayLocationDTO> stays = persistenceMapper.toStayDTOs(stayEntities);
        List<TimelineTripDTO> trips = persistenceMapper.toTripDTOs(tripEntities);

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Add metadata
        timeline.setDataSource(dataSource);
        timeline.setLastUpdated(getLatestUpdateTimestamp(stayEntities, tripEntities));
        timeline.setIsStale(false);

        return timeline;
    }

    /**
     * Build timeline DTO with stale data indication.
     */
    private MovementTimelineDTO buildTimelineWithStaleIndicator(UUID userId, List<TimelineStayEntity> stayEntities,
                                                                List<TimelineTripEntity> tripEntities) {
        MovementTimelineDTO timeline = buildTimelineFromEntities(userId, stayEntities, tripEntities,
                TimelineDataSource.REGENERATING);
        timeline.setIsStale(true);
        return timeline;
    }

    /**
     * Create empty timeline for error cases.
     */
    private MovementTimelineDTO createEmptyTimeline(UUID userId, TimelineDataSource dataSource) {
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(dataSource);
        timeline.setLastUpdated(Instant.now());
        timeline.setIsStale(false);
        return timeline;
    }

    /**
     * Get the latest update timestamp from entities.
     */
    private Instant getLatestUpdateTimestamp(List<TimelineStayEntity> stays, List<TimelineTripEntity> trips) {
        Instant latestStayUpdate = stays.stream()
                .map(TimelineStayEntity::getLastUpdated)
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);

        Instant latestTripUpdate = trips.stream()
                .map(TimelineTripEntity::getLastUpdated)
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);

        return latestStayUpdate.isAfter(latestTripUpdate) ? latestStayUpdate : latestTripUpdate;
    }

    /**
     * Get timeline data source for a specific date.
     */
    public TimelineDataSource getTimelineDataSource(UUID userId, Instant date) {
        if (!persistenceService.shouldPersistTimeline(date)) {
            return TimelineDataSource.LIVE;
        }

        if (persistenceService.hasPersistedTimelineForDate(userId, date)) {
            // Check if cached data is valid
            String currentVersion = versionService.generateTimelineVersion(userId, date);
            String cachedVersion = persistenceService.getPersistedTimelineVersion(userId, date);

            if (currentVersion.equals(cachedVersion)) {
                return TimelineDataSource.CACHED;
            } else {
                return TimelineDataSource.REGENERATING;
            }
        }

        return TimelineDataSource.LIVE;
    }

    /**
     * Force regeneration of timeline for a specific time range.
     * Useful for testing or manual refresh.
     */
    public MovementTimelineDTO forceRegenerateTimeline(UUID userId, Instant startTime, Instant endTime) {
        log.info("Force regenerating timeline for user {} from {} to {}", userId, startTime, endTime);

        // Clear existing cached data for the time range
        persistenceService.clearTimelineForRange(userId, startTime, endTime);

        // Clear regeneration tracking to avoid conflicts
        String timeRangeKey = createTimeRangeKey(startTime, endTime);
        String regenerationKey = userId + ":" + timeRangeKey;
        regenerationInProgress.remove(regenerationKey);

        // Use regeneration service for fresh timeline with smart strategies
        return generateAndPersistTimeline(userId, startTime, endTime);
    }

    /**
     * Create a consistent cache key for a time range.
     */
    private String createTimeRangeKey(Instant startTime, Instant endTime) {
        return startTime.toString() + "_to_" + endTime.toString();
    }

    /**
     * Check if we have complete timeline coverage for the requested time range.
     * For now, this is a simplified check - in a full implementation, this would
     * verify that we have persisted timeline data that completely covers the requested range.
     */
    private boolean hasCompleteTimelineCoverage(List<TimelineStayEntity> cachedStays,
                                                List<TimelineTripEntity> cachedTrips) {
        // Simplified approach: if we have any cached data, consider it sufficient
        // In a more sophisticated implementation, this would check:
        // 1. Whether the cached data spans the entire requested time range
        // 2. Whether there are gaps in the cached data
        // 3. Whether the cached data was generated for a compatible time range

        return !cachedStays.isEmpty() || !cachedTrips.isEmpty();
    }
}