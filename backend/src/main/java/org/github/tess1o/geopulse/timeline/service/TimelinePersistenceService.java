package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.locationtech.jts.geom.LineString;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Service for persisting timeline data with proper UTC date handling.
 * Only persists completed past days - today's timeline is always generated live.
 */
@ApplicationScoped
@Slf4j
public class TimelinePersistenceService {

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    UserRepository userRepository;


    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    ReverseGeocodingLocationRepository geocodingRepository;

    /**
     * Persist a complete timeline for a specific date if it's a completed past day.
     * Today's timeline should never be persisted as it's continuously changing.
     *
     * @param userId user ID
     * @param timelineDate date of the timeline
     * @param timeline timeline data to persist
     * @return true if timeline was persisted, false if skipped (today's data)
     */
    @Transactional
    public boolean persistTimelineIfComplete(UUID userId, Instant timelineDate, MovementTimelineDTO timeline) {
        // Only persist completed past days
        if (!shouldPersistTimeline(timelineDate)) {
            log.debug("Skipping persistence for timeline date {} (today or future)", timelineDate);
            return false;
        }

        // Clear existing timeline data for this date
        clearTimelineForDate(userId, timelineDate);

        // Generate version hash for this timeline
        String timelineVersion = "cached-" + Instant.now().toEpochMilli();

        // Persist stays
        persistStays(userId, timelineDate, timeline.getStays(), timelineVersion);

        // Persist trips
        persistTrips(userId, timelineDate, timeline.getTrips(), timelineVersion);

        log.info("Persisted timeline for user {} on date {} with {} stays and {} trips", 
                userId, timelineDate, timeline.getStaysCount(), timeline.getTripsCount());

        return true;
    }

    /**
     * Persist a timeline for a custom time range if it represents a completed past period.
     * 
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range  
     * @param timeline timeline data to persist
     * @return true if timeline was persisted, false if skipped
     */
    @Transactional
    public boolean persistTimelineForRange(UUID userId, Instant startTime, Instant endTime, MovementTimelineDTO timeline) {
        // Only persist completed past periods
        if (!shouldPersistTimeline(startTime, endTime)) {
            log.debug("Skipping persistence for timeline range {} to {} (not a completed past period)", startTime, endTime);
            return false;
        }

        // Clear existing timeline data for this range
        clearTimelineForRange(userId, startTime, endTime);

        // Generate version hash for this timeline (use start time as key)
        String timelineVersion = "cached-" + Instant.now().toEpochMilli();

        // Persist stays
        persistStays(userId, startTime, timeline.getStays(), timelineVersion);

        // Persist trips
        persistTrips(userId, startTime, timeline.getTrips(), timelineVersion);

        log.info("Persisted timeline for user {} from {} to {} with {} stays and {} trips", 
                userId, startTime, endTime, timeline.getStaysCount(), timeline.getTripsCount());

        return true;
    }

    /**
     * Check if a timeline time range should be persisted.
     * Only completed past days are persisted - today's timeline is always live.
     *
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return true if should be persisted, false otherwise
     */
    public boolean shouldPersistTimeline(Instant startTime, Instant endTime) {
        // Check if the start time represents today in UTC - if so, don't persist
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        
        // Only persist if the start date is before today (completed past day)
        return startDate.isBefore(today);
    }

    /**
     * Check if a timeline date should be persisted (UTC day boundaries).
     * Only completed past days are persisted - today is always live.
     * This method is for backward compatibility with single-date operations.
     *
     * @param timelineDate date to check
     * @return true if should be persisted, false otherwise
     */
    public boolean shouldPersistTimeline(Instant timelineDate) {
        // Use UTC for all date comparisons
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate timelineLocalDate = timelineDate.atZone(ZoneOffset.UTC).toLocalDate();
        
        // Only persist completed past days
        return timelineLocalDate.isBefore(today);
    }

    /**
     * Clear existing persisted timeline data for a specific time range.
     * Used before persisting new timeline data.
     */
    @Transactional
    public void clearTimelineForRange(UUID userId, Instant startTime, Instant endTime) {
        long deletedStays = stayRepository.delete("user.id = ?1 and timestamp >= ?2 and timestamp < ?3", 
                userId, startTime, endTime);
        long deletedTrips = tripRepository.delete("user.id = ?1 and timestamp >= ?2 and timestamp < ?3", 
                userId, startTime, endTime);

        if (deletedStays > 0 || deletedTrips > 0) {
            log.debug("Cleared existing timeline data for user {} from {} to {}: {} stays, {} trips", 
                    userId, startTime, endTime, deletedStays, deletedTrips);
        }
    }

    /**
     * Clear existing persisted timeline data for a specific date (UTC day boundaries).
     * Used for compatibility with single-date operations.
     */
    @Transactional
    public void clearTimelineForDate(UUID userId, Instant timelineDate) {
        LocalDate localDate = timelineDate.atZone(ZoneOffset.UTC).toLocalDate();
        Instant startOfDay = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        clearTimelineForRange(userId, startOfDay, endOfDay);
    }

    /**
     * Check if timeline data exists for a specific date.
     */
    public boolean hasPersistedTimelineForDate(UUID userId, Instant timelineDate) {
        List<TimelineStayEntity> stays = stayRepository.findByUserAndDate(userId, timelineDate);
        return !stays.isEmpty();
    }

    /**
     * Persist timeline stay data.
     */
    private void persistStays(UUID userId, Instant timelineDate, List<TimelineStayLocationDTO> stays, String timelineVersion) {
        if (stays == null || stays.isEmpty()) {
            return;
        }

        var user = userRepository.findById(userId);
        if (user == null) {
            log.error("User not found: {}", userId);
            return;
        }

        for (TimelineStayLocationDTO stayDTO : stays) {
            TimelineStayEntity stayEntity = TimelineStayEntity.builder()
                    .user(user)
                    .timestamp(stayDTO.getTimestamp())
                    .stayDuration(stayDTO.getStayDuration())
                    .latitude(stayDTO.getLatitude())
                    .longitude(stayDTO.getLongitude())
                    .locationName(stayDTO.getLocationName())
                    .locationSource(determineLocationSource(stayDTO))
                    .build();

            // Set favorite or geocoding reference based on the DTO
            if (stayDTO.getFavoriteId() != null) {
                var favoriteEntity = favoritesRepository.findById(stayDTO.getFavoriteId());
                if (favoriteEntity != null) {
                    stayEntity.setFavoriteLocation(favoriteEntity);
                    stayEntity.setLocationSource(LocationSource.FAVORITE);
                } else {
                    log.warn("Favorite with ID {} not found for stay {}", stayDTO.getFavoriteId(), stayDTO.getTimestamp());
                    stayEntity.setLocationSource(LocationSource.HISTORICAL);
                }
            } else if (stayDTO.getGeocodingId() != null) {
                var geocodingEntity = geocodingRepository.findById(stayDTO.getGeocodingId());
                if (geocodingEntity != null) {
                    stayEntity.setGeocodingLocation(geocodingEntity);
                    stayEntity.setLocationSource(LocationSource.GEOCODING);
                } else {
                    log.warn("Geocoding location with ID {} not found for stay {}", stayDTO.getGeocodingId(), stayDTO.getTimestamp());
                    stayEntity.setLocationSource(LocationSource.HISTORICAL);
                }
            }

            stayRepository.persist(stayEntity);
        }

        log.debug("Persisted {} stays for user {} on {}", stays.size(), userId, timelineDate);
    }

    /**
     * Persist timeline trip data.
     */
    private void persistTrips(UUID userId, Instant timelineDate, List<TimelineTripDTO> trips, String timelineVersion) {
        if (trips == null || trips.isEmpty()) {
            return;
        }

        var user = userRepository.findById(userId);
        if (user == null) {
            log.error("User not found: {}", userId);
            return;
        }

        for (TimelineTripDTO tripDTO : trips) {
            // Convert path to LineString geometry if available
            LineString pathGeometry = null;
            if (tripDTO.getPath() != null && !tripDTO.getPath().isEmpty()) {
                pathGeometry = GeoUtils.convertGpsPointsToLineString(tripDTO.getPath());
            }

            // For trips, we need start and end coordinates
            // Using the trip's own coordinates as start, and if path exists, last point as end
            double endLat = tripDTO.getLatitude();
            double endLon = tripDTO.getLongitude();
            
            if (tripDTO.getPath() != null && !tripDTO.getPath().isEmpty()) {
                var lastPoint = tripDTO.getPath().get(tripDTO.getPath().size() - 1);
                endLat = lastPoint.getLatitude();
                endLon = lastPoint.getLongitude();
            }

            TimelineTripEntity tripEntity = TimelineTripEntity.builder()
                    .user(user)
                    .timestamp(tripDTO.getTimestamp())
                    .tripDuration(tripDTO.getTripDuration())
                    .startLatitude(tripDTO.getLatitude())
                    .startLongitude(tripDTO.getLongitude())
                    .endLatitude(endLat)
                    .endLongitude(endLon)
                    .distanceKm(tripDTO.getDistanceKm())
                    .movementType(tripDTO.getMovementType())
                    .path(pathGeometry)
                    .build();

            tripRepository.persist(tripEntity);
        }

        log.debug("Persisted {} trips for user {} on {}", trips.size(), userId, timelineDate);
    }

    /**
     * Determine the location source from a stay DTO.
     */
    private LocationSource determineLocationSource(TimelineStayLocationDTO stayDTO) {
        if (stayDTO.getFavoriteId() != null) {
            return LocationSource.FAVORITE;
        } else if (stayDTO.getGeocodingId() != null) {
            return LocationSource.GEOCODING;
        } else {
            // Fallback - location name exists but no reference
            return LocationSource.HISTORICAL;
        }
    }

    /**
     * Cleanup old timeline data based on retention policy.
     * 
     * @param userId user ID
     * @param retentionDays number of days to retain
     * @return number of records deleted
     */
    @Transactional
    public long cleanupOldTimelineData(UUID userId, int retentionDays) {
        Instant cutoffDate = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L);
        
        long deletedStays = stayRepository.deleteByUserBeforeDate(userId, cutoffDate);
        long deletedTrips = tripRepository.deleteByUserBeforeDate(userId, cutoffDate);
        
        long totalDeleted = deletedStays + deletedTrips;
        if (totalDeleted > 0) {
            log.info("Cleaned up {} old timeline records for user {} (retention: {} days)", 
                    totalDeleted, userId, retentionDays);
        }
        
        return totalDeleted;
    }
}