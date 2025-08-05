package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.timeline.assembly.TimelineDataService;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Simplified timeline query service with clear, predictable logic.
 * 
 * Core principles:
 * 1. Today = Always live generation
 * 2. Past = Use cache if exists, generate if GPS exists but no cache, empty if no GPS
 * 3. Mixed (past + today) = Combine cached past + live today
 */
@ApplicationScoped
@Slf4j
public class TimelineQueryService {

    @Inject
    TimelineService timelineGenerationService;

    @Inject
    TimelineDataService timelineDataService;

    @Inject
    TimelineCacheService timelineCacheService;

    /**
     * Get timeline for a user within a specific time range.
     * Simple logic without version checking, expansion, or staleness detection.
     */
    public MovementTimelineDTO getTimeline(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Getting timeline for user {} from {} to {}", userId, startTime, endTime);

        // Determine date boundaries (use UTC for consistency)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = endTime.atZone(ZoneOffset.UTC).toLocalDate();

        log.debug("Date analysis: today={}, startDate={}, endDate={}", today, startDate, endDate);

        if (startDate.equals(today) && endDate.equals(today)) {
            // Case 1: Today only - always generate live
            log.debug("Today only request - generating live timeline");
            return generateLiveTimeline(userId, startTime, endTime);
            
        } else if (endDate.isBefore(today)) {
            // Case 2: Past only - use cache or generate
            log.debug("Past only request - checking cache");
            return getPastTimeline(userId, startTime, endTime);
            
        } else if (startDate.isBefore(today) || (startDate.equals(today) && endDate.isAfter(today))) {
            // Case 3: Mixed (includes past/today/future combinations) - combine available data
            log.debug("Mixed request (past/today/future) - combining available data sources");
            return getMixedTimeline(userId, startTime, endTime, today);
            
        } else {
            // Case 4: Pure future dates only - return empty
            log.debug("Pure future dates requested - returning empty timeline");
            return createEmptyTimeline(userId, TimelineDataSource.LIVE);
        }
    }

    /**
     * Generate live timeline (always fresh from GPS data).
     */
    private MovementTimelineDTO generateLiveTimeline(UUID userId, Instant startTime, Instant endTime) {
        try {
            MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(userId, startTime, endTime);
            timeline.setDataSource(TimelineDataSource.LIVE);
            timeline.setLastUpdated(Instant.now());

            log.debug("Generated live timeline: {} stays, {} trips", 
                     timeline.getStaysCount(), timeline.getTripsCount());
            return timeline;
            
        } catch (Exception e) {
            log.error("Failed to generate live timeline for user {} from {} to {}", 
                     userId, startTime, endTime, e);
            return createEmptyTimeline(userId, TimelineDataSource.LIVE);
        }
    }

    /**
     * Get past timeline from cache or generate if needed.
     */
    private MovementTimelineDTO getPastTimeline(UUID userId, Instant startTime, Instant endTime) {
        // Check if timeline exists in cache
        if (timelineCacheService.exists(userId, startTime, endTime)) {
            log.debug("Found cached timeline data");
            MovementTimelineDTO cachedTimeline = timelineCacheService.get(userId, startTime, endTime);
            cachedTimeline.setDataSource(TimelineDataSource.CACHED);
            return cachedTimeline;
        }

        // No cached data - check if GPS data exists
        log.debug("No cached timeline - checking GPS data availability");
        GpsPointPathDTO gpsData = timelineDataService.getGpsPointPath(userId, startTime, endTime);
        
        if (gpsData == null || gpsData.getPoints() == null || gpsData.getPoints().isEmpty()) {
            log.debug("No GPS data found - returning empty timeline");
            return createEmptyTimeline(userId, TimelineDataSource.CACHED);
        }

        // GPS data exists - generate and cache timeline
        log.debug("Found {} GPS points - generating and caching timeline", gpsData.getPoints().size());
        try {
            MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(userId, startTime, endTime);
            timeline.setDataSource(TimelineDataSource.CACHED);
            timeline.setLastUpdated(Instant.now());

            // Cache the generated timeline for future requests
            timelineCacheService.save(userId, startTime, endTime, timeline);
            
            log.debug("Generated and cached timeline: {} stays, {} trips", 
                     timeline.getStaysCount(), timeline.getTripsCount());
            return timeline;
            
        } catch (Exception e) {
            log.error("Failed to generate timeline for user {} from {} to {}", 
                     userId, startTime, endTime, e);
            return createEmptyTimeline(userId, TimelineDataSource.CACHED);
        }
    }

    /**
     * Get mixed timeline (past from cache + today live, ignoring future dates).
     */
    private MovementTimelineDTO getMixedTimeline(UUID userId, Instant startTime, Instant endTime, LocalDate today) {
        LocalDate startDate = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = endTime.atZone(ZoneOffset.UTC).toLocalDate();
        
        // Limit end date to today - ignore future dates
        Instant effectiveEndTime = endTime;
        if (endDate.isAfter(today)) {
            effectiveEndTime = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        }
        
        if (startDate.equals(today)) {
            // Request starts today - just generate live for today portion
            log.debug("Request starts today - generating live timeline for today only");
            return generateLiveTimeline(userId, startTime, effectiveEndTime);
        }
        
        // Split the request at today's boundary
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        
        // Get past portion from cache (from start until end of yesterday)
        MovementTimelineDTO pastTimeline = getPastTimeline(userId, startTime, todayStart.minusNanos(1));
        
        // Get today portion live (from today start until effective end)
        MovementTimelineDTO todayTimeline = generateLiveTimeline(userId, todayStart, effectiveEndTime);
        
        // Combine the timelines
        MovementTimelineDTO combined = combineTimelines(userId, pastTimeline, todayTimeline);
        combined.setDataSource(TimelineDataSource.MIXED);
        combined.setLastUpdated(Instant.now());

        log.debug("Combined timeline: {} stays ({}+{}), {} trips ({}+{})", 
                 combined.getStaysCount(), pastTimeline.getStaysCount(), todayTimeline.getStaysCount(),
                 combined.getTripsCount(), pastTimeline.getTripsCount(), todayTimeline.getTripsCount());
        
        return combined;
    }

    /**
     * Combine two timelines into one.
     */
    private MovementTimelineDTO combineTimelines(UUID userId, MovementTimelineDTO past, MovementTimelineDTO today) {
        MovementTimelineDTO combined = new MovementTimelineDTO(userId);
        
        // Add all stays and trips from both timelines
        combined.getStays().addAll(past.getStays());
        combined.getStays().addAll(today.getStays());
        combined.getTrips().addAll(past.getTrips());
        combined.getTrips().addAll(today.getTrips());
        
        // Sort by timestamp to maintain chronological order
        combined.getStays().sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        combined.getTrips().sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        return combined;
    }

    /**
     * Create empty timeline for error cases or no data scenarios.
     */
    private MovementTimelineDTO createEmptyTimeline(UUID userId, TimelineDataSource dataSource) {
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(dataSource);
        timeline.setLastUpdated(Instant.now());
        return timeline;
    }
}