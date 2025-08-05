package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.model.TimelineTripEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;

/**
 * Simple cache service for timeline data.
 * Handles database operations for cached timeline entities without complex version logic.
 */
@ApplicationScoped
@Slf4j
public class TimelineCacheService {

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelinePersistenceMapper persistenceMapper;

    @Inject
    org.github.tess1o.geopulse.user.repository.UserRepository userRepository;

    /**
     * Check if timeline data exists for the given time range.
     */
    public boolean exists(UUID userId, Instant startTime, Instant endTime) {
        List<TimelineStayEntity> stays = stayRepository.findByUserAndDateRange(userId, startTime, endTime);
        List<TimelineTripEntity> trips = tripRepository.findByUserAndDateRange(userId, startTime, endTime);
        
        boolean hasData = !stays.isEmpty() || !trips.isEmpty();
        log.debug("Cache check for user {} from {} to {}: {}", userId, startTime, endTime, 
                 hasData ? "EXISTS" : "NOT_FOUND");
        
        return hasData;
    }

    /**
     * Get timeline data from cache.
     */
    public MovementTimelineDTO get(UUID userId, Instant startTime, Instant endTime) {
        List<TimelineStayEntity> stays = stayRepository.findByUserAndDateRange(userId, startTime, endTime);
        List<TimelineTripEntity> trips = tripRepository.findByUserAndDateRange(userId, startTime, endTime);
        
        MovementTimelineDTO timeline = persistenceMapper.toMovementTimelineDTO(userId, stays, trips);
        timeline.setDataSource(TimelineDataSource.CACHED);  // Mark as cached data
        
        log.debug("Retrieved cached timeline for user {}: {} stays, {} trips", 
                 userId, stays.size(), trips.size());
        
        return timeline;
    }

    /**
     * Save timeline data to cache.
     */
    @Transactional
    public void save(UUID userId, Instant startTime, Instant endTime, MovementTimelineDTO timeline) {
        try {
            // Convert DTO to entities
            List<TimelineStayEntity> stayEntities = persistenceMapper.toStayEntities(timeline);
            List<TimelineTripEntity> tripEntities = persistenceMapper.toTripEntities(timeline);

            // Set user on all entities - they need user reference for persistence
            UserEntity user = userRepository.findById(userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            
            stayEntities.forEach(stay -> stay.setUser(user));
            tripEntities.forEach(trip -> trip.setUser(user));

            // Persist entities
            if (!stayEntities.isEmpty()) {
                stayRepository.persist(stayEntities);
            }
            if (!tripEntities.isEmpty()) {
                tripRepository.persist(tripEntities);
            }

            log.debug("Saved timeline to cache for user {}: {} stays, {} trips", 
                     userId, stayEntities.size(), tripEntities.size());
                     
        } catch (Exception e) {
            log.error("Failed to save timeline to cache for user {} from {} to {}", 
                     userId, startTime, endTime, e);
            throw e;
        }
    }

    /**
     * Delete timeline data for specific date ranges.
     * Used when favorite changes require regeneration.
     */
    @Transactional
    public void delete(UUID userId, List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return;
        }

        int deletedStays = 0;
        int deletedTrips = 0;

        for (LocalDate date : dates) {
            Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            // Delete stays for this date
            List<TimelineStayEntity> staysToDelete = stayRepository.findByUserAndDateRange(userId, dayStart, dayEnd);
            if (!staysToDelete.isEmpty()) {
                staysToDelete.forEach(stayRepository::delete);
                deletedStays += staysToDelete.size();
            }

            // Delete trips for this date
            List<TimelineTripEntity> tripsToDelete = tripRepository.findByUserAndDateRange(userId, dayStart, dayEnd);
            if (!tripsToDelete.isEmpty()) {
                tripsToDelete.forEach(tripRepository::delete);
                deletedTrips += tripsToDelete.size();
            }
        }

        log.info("Deleted cached timeline data for user {} on {} dates: {} stays, {} trips", 
                userId, dates.size(), deletedStays, deletedTrips);
    }

    /**
     * Delete all timeline data for a user.
     * Used for bulk operations or user cleanup.
     */
    @Transactional
    public void deleteAll(UUID userId) {
        long deletedStays = stayRepository.delete("user.id", userId);
        long deletedTrips = tripRepository.delete("user.id", userId);
        
        log.info("Deleted all cached timeline data for user {}: {} stays, {} trips", 
                userId, deletedStays, deletedTrips);
    }

    /**
     * Get date ranges that have cached timeline data for a user.
     * Useful for determining what needs to be regenerated.
     */
    public List<LocalDate> getCachedDates(UUID userId) {
        List<TimelineStayEntity> stays = stayRepository.find("user.id = ?1 ORDER BY timestamp", userId).list();
        
        return stays.stream()
                   .map(stay -> stay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate())
                   .distinct()
                   .sorted()
                   .toList();
    }

    /**
     * Check if a specific date has cached timeline data.
     */
    public boolean hasDataForDate(UUID userId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        return exists(userId, dayStart, dayEnd);
    }
}