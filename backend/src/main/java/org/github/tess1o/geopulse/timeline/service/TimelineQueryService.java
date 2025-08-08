package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.timeline.assembly.TimelineDataService;
import org.github.tess1o.geopulse.timeline.assembly.TimelineProcessingService;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;

import java.time.Duration;
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
    
    @Inject
    TimelineStayRepository timelineStayRepository;
    
    @Inject
    TimelineTripRepository timelineTripRepository;
    
    @Inject
    TimelinePersistenceMapper persistenceMapper;
    @Inject
    TimelineProcessingService timelineProcessingService;

    @Inject
    TimelineConfigurationProvider configurationProvider;
    /**
     * Get timeline for a user within a specific time range.
     * Simple logic without version checking, expansion, or staleness detection.
     * Includes prepending previous context for complete timeline view.
     */
    public MovementTimelineDTO getTimeline(UUID userId, Instant startTime, Instant endTime) {
        return getTimeline(userId, startTime, endTime, true);
    }

    /**
     * Get timeline for a user within a specific time range with option to skip prepending.
     * 
     * @param userId user ID
     * @param startTime start time (inclusive)
     * @param endTime end time (inclusive)
     * @param includePrependedContext if true, prepend previous context; if false, skip prepending
     * @return timeline data
     */
    public MovementTimelineDTO getTimeline(UUID userId, Instant startTime, Instant endTime, boolean includePrependedContext) {
        log.debug("Getting timeline for user {} from {} to {} (prepend: {})", userId, startTime, endTime, includePrependedContext);

        // Determine date boundaries (use UTC for consistency)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = endTime.atZone(ZoneOffset.UTC).toLocalDate();

        log.debug("Date analysis: today={}, startDate={}, endDate={}", today, startDate, endDate);

        MovementTimelineDTO timeline;
        
        if (startDate.equals(today) && endDate.equals(today)) {
            // Case 1: Today only - always generate live
            log.debug("Today only request - generating live timeline");
            timeline = generateLiveTimeline(userId, startTime, endTime);
            
        } else if (endDate.isBefore(today)) {
            // Case 2: Past only - use cache or generate
            log.debug("Past only request - checking cache");
            timeline = getPastTimeline(userId, startTime, endTime);
            
        } else if (startDate.isBefore(today) || (startDate.equals(today) && endDate.isAfter(today))) {
            // Case 3: Mixed (includes past/today/future combinations) - combine available data
            log.debug("Mixed request (past/today/future) - combining available data sources");
            timeline = getMixedTimeline(userId, startTime, endTime, today);
            
        } else {
            // Case 4: Pure future dates only - return empty
            log.debug("Pure future dates requested - returning empty timeline");
            timeline = createEmptyTimeline(userId, TimelineDataSource.LIVE);
        }
        
        // Enhance timeline with previous context for complete view (if requested)
        if (includePrependedContext) {
            var timelinePrepended = prependPreviousContext(userId, startTime, timeline);
            TimelineConfig config = configurationProvider.getConfigurationForUser(userId);
            return timelineProcessingService.processTimeline(config, timelinePrepended);
        } else {
            return timeline;
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

            log.debug("Generated live timeline: {} stays, {} trips, {} data gaps", 
                     timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());
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
            
            log.debug("Generated and cached timeline: {} stays, {} trips, {} data gaps", 
                     timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());
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

        log.debug("Combined timeline: {} stays ({}+{}), {} trips ({}+{}), {} data gaps ({}+{})", 
                 combined.getStaysCount(), pastTimeline.getStaysCount(), todayTimeline.getStaysCount(),
                 combined.getTripsCount(), pastTimeline.getTripsCount(), todayTimeline.getTripsCount(),
                 combined.getDataGapsCount(), pastTimeline.getDataGapsCount(), todayTimeline.getDataGapsCount());
        
        return combined;
    }

    /**
     * Combine two timelines into one, detecting cross-day data gaps at the boundary.
     */
    private MovementTimelineDTO combineTimelines(UUID userId, MovementTimelineDTO past, MovementTimelineDTO today) {
        log.debug("=== COMBINING TIMELINES FOR CROSS-DAY GAP DETECTION ===");
        log.debug("Past timeline: {} stays, {} trips, {} data gaps", 
                 past.getStaysCount(), past.getTripsCount(), past.getDataGapsCount());
        log.debug("Today timeline: {} stays, {} trips, {} data gaps", 
                 today.getStaysCount(), today.getTripsCount(), today.getDataGapsCount());
        
        MovementTimelineDTO combined = new MovementTimelineDTO(userId);
        
        // Add all stays, trips, and data gaps from both timelines
        combined.getStays().addAll(past.getStays());
        combined.getStays().addAll(today.getStays());
        combined.getTrips().addAll(past.getTrips());
        combined.getTrips().addAll(today.getTrips());
        combined.getDataGaps().addAll(past.getDataGaps());
        combined.getDataGaps().addAll(today.getDataGaps());
        
        // Detect cross-day gaps between the last activity in past timeline and first activity in today timeline
        detectCrossDayGaps(userId, past, today, combined);
        
        // Sort by timestamp to maintain chronological order
        combined.getStays().sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        combined.getTrips().sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        combined.getDataGaps().sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        
        log.debug("Combined timeline result: {} stays, {} trips, {} data gaps", 
                 combined.getStaysCount(), combined.getTripsCount(), combined.getDataGapsCount());
        
        return combined;
    }
    
    /**
     * Detect data gaps that span across the boundary between past and today timelines.
     * This catches gaps that wouldn't be detected when processing timelines separately.
     */
    private void detectCrossDayGaps(UUID userId, MovementTimelineDTO past, MovementTimelineDTO today, MovementTimelineDTO combined) {
        // Get configuration for gap detection thresholds
        TimelineConfig config = configurationProvider.getConfigurationForUser(userId);
        Integer gapThresholdSeconds = config.getDataGapThresholdSeconds();
        Integer minGapDurationSeconds = config.getDataGapMinDurationSeconds();
        
        if (gapThresholdSeconds == null || minGapDurationSeconds == null) {
            log.debug("Cross-day gap detection disabled - thresholds not configured");
            return;
        }
        
        log.debug("Checking for cross-day gaps with threshold: {}s, min duration: {}s", 
                 gapThresholdSeconds, minGapDurationSeconds);
        
        // Find the last timestamp in the past timeline (could be stay, trip, or data gap end)
        Instant lastPastActivity = getLastActivityTimestamp(past);
        
        // Find the first timestamp in the today timeline (could be stay, trip, or data gap start)  
        Instant firstTodayActivity = getFirstActivityTimestamp(today);
        
        if (lastPastActivity == null || firstTodayActivity == null) {
            log.debug("Cannot detect cross-day gap - missing activity timestamps (past: {}, today: {})", 
                     lastPastActivity, firstTodayActivity);
            return;
        }
        
        // Calculate gap duration
        long gapDurationSeconds = java.time.Duration.between(lastPastActivity, firstTodayActivity).getSeconds();
        
        log.info("Cross-day gap analysis: {} to {} = {}s ({}h)", 
                lastPastActivity, firstTodayActivity, gapDurationSeconds, gapDurationSeconds / 3600.0);
        
        if (gapDurationSeconds > gapThresholdSeconds && gapDurationSeconds >= minGapDurationSeconds) {
            TimelineDataGapDTO crossDayGap = new TimelineDataGapDTO(lastPastActivity, firstTodayActivity, gapDurationSeconds);
            combined.getDataGaps().add(crossDayGap);
            
            log.info("✓ CROSS-DAY DATA GAP DETECTED: {} to {} (duration: {}s = {}h)", 
                    lastPastActivity, firstTodayActivity, gapDurationSeconds, gapDurationSeconds / 3600.0);
        } else {
            log.debug("Cross-day gap too short to record: {}s < {}s threshold", gapDurationSeconds, minGapDurationSeconds);
        }
    }
    
    /**
     * Get the last activity timestamp from a timeline (latest end time of stays, trips, or data gaps).
     */
    private Instant getLastActivityTimestamp(MovementTimelineDTO timeline) {
        Instant lastTimestamp = null;
        
        // Check last stay
        if (!timeline.getStays().isEmpty()) {
            TimelineStayLocationDTO lastStay = timeline.getStays().get(timeline.getStays().size() - 1);
            Instant stayEndTime = lastStay.getTimestamp().plusSeconds(lastStay.getStayDuration() * 60);
            lastTimestamp = maxInstant(lastTimestamp, stayEndTime);
        }
        
        // Check last trip  
        if (!timeline.getTrips().isEmpty()) {
            TimelineTripDTO lastTrip = timeline.getTrips().get(timeline.getTrips().size() - 1);
            Instant tripEndTime = lastTrip.getTimestamp().plusSeconds(lastTrip.getTripDuration() * 60);
            lastTimestamp = maxInstant(lastTimestamp, tripEndTime);
        }
        
        // Check last data gap
        if (!timeline.getDataGaps().isEmpty()) {
            TimelineDataGapDTO lastGap = timeline.getDataGaps().get(timeline.getDataGaps().size() - 1);
            lastTimestamp = maxInstant(lastTimestamp, lastGap.getEndTime());
        }
        
        return lastTimestamp;
    }
    
    /**
     * Get the first activity timestamp from a timeline (earliest start time of stays, trips, or data gaps).
     */
    private Instant getFirstActivityTimestamp(MovementTimelineDTO timeline) {
        Instant firstTimestamp = null;
        
        // Check first stay
        if (!timeline.getStays().isEmpty()) {
            TimelineStayLocationDTO firstStay = timeline.getStays().get(0);
            firstTimestamp = minInstant(firstTimestamp, firstStay.getTimestamp());
        }
        
        // Check first trip
        if (!timeline.getTrips().isEmpty()) {
            TimelineTripDTO firstTrip = timeline.getTrips().get(0);
            firstTimestamp = minInstant(firstTimestamp, firstTrip.getTimestamp());
        }
        
        // Check first data gap
        if (!timeline.getDataGaps().isEmpty()) {
            TimelineDataGapDTO firstGap = timeline.getDataGaps().get(0);
            firstTimestamp = minInstant(firstTimestamp, firstGap.getStartTime());
        }
        
        return firstTimestamp;
    }
    
    private Instant maxInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
    
    private Instant minInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
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
    
    /**
     * Prepend previous context (latest stay or trip) to provide complete timeline view.
     * Adjusts the duration of prepended item to show continuity until the first activity.
     *
     * @param userId user identifier  
     * @param requestStartTime start time of the original request
     * @param timeline timeline to enhance with previous context
     * @return timeline with previous context prepended if available
     */
    private MovementTimelineDTO prependPreviousContext(UUID userId, Instant requestStartTime, MovementTimelineDTO timeline) {
        try {
            log.debug("Looking for previous context before {} for user {}", requestStartTime, userId);
            
            // Find latest stay and trip before the request start time
            TimelineStayEntity latestStay = timelineStayRepository.findLatestBefore(userId, requestStartTime);
            TimelineTripEntity latestTrip = timelineTripRepository.findLatestBefore(userId, requestStartTime);

            log.debug("Found latest stay = {}, latest trip = {}", latestStay, latestTrip);
            
            // Determine which is more recent (closest to request start time)
            boolean useStay = false;
            Instant latestActivityTime = null;
            
            if (latestStay != null && latestTrip != null) {
                if (latestStay.getTimestamp().isAfter(latestTrip.getTimestamp())) {
                    useStay = true;
                    latestActivityTime = latestStay.getTimestamp();
                } else {
                    useStay = false;
                    latestActivityTime = latestTrip.getTimestamp();
                }
            } else if (latestStay != null) {
                useStay = true;
                latestActivityTime = latestStay.getTimestamp();
            } else if (latestTrip != null) {
                useStay = false;
                latestActivityTime = latestTrip.getTimestamp();
            } else {
                log.debug("No previous context found for user {}", userId);
                return timeline;
            }


            // Find the earliest time in current timeline to determine where previous activity should end
            Instant earliestTimeInTimeline = findEarliestTimeInTimeline(timeline, requestStartTime);

            log.debug("Found earliest time in timeline = {}", earliestTimeInTimeline);
            
            if (earliestTimeInTimeline == null) {
                // No activities in current timeline, don't prepend (nothing to connect to)
                log.debug("Current timeline is empty, not prepending previous context");
                return timeline;
            }
            
            // Prepend the appropriate activity with adjusted duration
            if (useStay) {
                prependStayWithAdjustedDuration(timeline, latestStay, earliestTimeInTimeline);
                log.debug("Prepended stay from {} (adjusted to end at {})", latestActivityTime, earliestTimeInTimeline);
            } else {
                prependTripWithAdjustedDuration(timeline, latestTrip, earliestTimeInTimeline);
                log.debug("Prepended trip from {} (adjusted to end at {})", latestActivityTime, earliestTimeInTimeline);
            }
            
        } catch (Exception e) {
            log.warn("Failed to prepend previous context for user {}: {}", userId, e.getMessage());
            // Continue without previous context rather than failing the entire request
        }
        
        return timeline;
    }
    
    /**
     * Find the earliest timestamp in the timeline that's at or after the request start time.
     * This ensures prepended activities connect properly to the requested time range.
     */
    private Instant findEarliestTimeInTimeline(MovementTimelineDTO timeline, Instant requestStartTime) {
        Instant earliest = null;
        
        // Check stays - find first stay at or after request start time
        for (var stay : timeline.getStays()) {
            if (stay.getTimestamp().compareTo(requestStartTime) >= 0) {
                if (earliest == null || stay.getTimestamp().isBefore(earliest)) {
                    earliest = stay.getTimestamp();
                }
            }
        }
        
        // Check trips - find first trip at or after request start time
        for (var trip : timeline.getTrips()) {
            if (trip.getTimestamp().compareTo(requestStartTime) >= 0) {
                if (earliest == null || trip.getTimestamp().isBefore(earliest)) {
                    earliest = trip.getTimestamp();
                }
            }
        }
        
        // If no activities found at or after request start time, use request start time itself
        if (earliest == null) {
            earliest = requestStartTime;
        }
        
        return earliest;
    }
    
    /**
     * Prepend a stay with adjusted duration to show continuity.
     */
    private void prependStayWithAdjustedDuration(MovementTimelineDTO timeline, TimelineStayEntity stayEntity, Instant endTime) {
        TimelineStayLocationDTO stayDTO = persistenceMapper.toDTO(stayEntity);
        
        // Calculate new duration: from stay start to earliest time in timeline
        long adjustedDurationMinutes = Duration.between(stayEntity.getTimestamp(), endTime).toMinutes();
        stayDTO.setStayDuration(adjustedDurationMinutes);
        
        timeline.getStays().add(0, stayDTO);
        
        log.debug("Prepended stay: {} at {} (adjusted duration: {}s)", 
                 stayDTO.getLocationName(), stayDTO.getTimestamp(), adjustedDurationMinutes);
    }
    
    /**
     * Prepend a trip with adjusted duration to show continuity.
     */
    private void prependTripWithAdjustedDuration(MovementTimelineDTO timeline, TimelineTripEntity tripEntity, Instant endTime) {
        TimelineTripDTO tripDTO = persistenceMapper.toTripDTO(tripEntity);
        
        // Calculate new duration: from trip start to earliest time in timeline  
        long adjustedDurationMinutes = Duration.between(tripEntity.getTimestamp(), endTime).toMinutes();
        tripDTO.setTripDuration(adjustedDurationMinutes);
        
        timeline.getTrips().add(0, tripDTO);
        
        log.debug("Prepended trip: {} at {} (adjusted duration: {}min)", 
                 tripDTO.getMovementType(), tripDTO.getTimestamp(), adjustedDurationMinutes);
    }
}