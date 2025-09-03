package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for clearing existing data before import operations.
 * Implements smart date range calculation to only delete data where replacements exist.
 */
@ApplicationScoped
@Slf4j
public class ImportDataClearingService {

    @Inject
    EntityManager entityManager;

    /**
     * Calculate the smart deletion range based on user filters and actual file data.
     * This ensures we only delete data where we're importing replacements.
     * 
     * @param job The import job with user-specified date range filter
     * @param fileDataRange The actual min/max timestamps found in the import file
     * @return DateRange for deletion, or null if no deletion should occur
     */
    public DateRange calculateDeletionRange(ImportJob job, DateRange fileDataRange) {
        if (!job.getOptions().isClearDataBeforeImport() || fileDataRange == null) {
            return null;
        }
        
        // If user specified a date range filter, calculate intersection
        if (job.getOptions().getDateRangeFilter() != null) {
            Instant userStart = job.getOptions().getDateRangeFilter().getStartDate();
            Instant userEnd = job.getOptions().getDateRangeFilter().getEndDate();
            
            // Calculate intersection: max(userStart, fileStart) to min(userEnd, fileEnd)
            Instant deleteStart = userStart.isAfter(fileDataRange.getStartDate()) ? 
                userStart : fileDataRange.getStartDate();
            Instant deleteEnd = userEnd.isBefore(fileDataRange.getEndDate()) ? 
                userEnd : fileDataRange.getEndDate();
            
            // Only return range if intersection exists
            if (deleteStart.isBefore(deleteEnd)) {
                return new DateRange(deleteStart, deleteEnd);
            } else {
                log.warn("No intersection between user date filter and file data - no deletion will occur");
                return null;
            }
        }
        
        // No user filter, delete entire file data range
        return fileDataRange;
    }
    
    /**
     * Extract the actual date range from GPS entities that will be imported.
     * 
     * @param gpsEntities The GPS entities to be imported
     * @return DateRange with min/max timestamps, or null if no valid timestamps
     */
    public DateRange extractDataRange(List<GpsPointEntity> gpsEntities) {
        if (gpsEntities.isEmpty()) {
            return null;
        }
        
        Instant minTimestamp = gpsEntities.stream()
            .map(GpsPointEntity::getTimestamp)
            .filter(timestamp -> timestamp != null)
            .min(Instant::compareTo)
            .orElse(null);
            
        Instant maxTimestamp = gpsEntities.stream()
            .map(GpsPointEntity::getTimestamp)
            .filter(timestamp -> timestamp != null)
            .max(Instant::compareTo)
            .orElse(null);
        
        if (minTimestamp == null || maxTimestamp == null) {
            return null;
        }
        
        return new DateRange(minTimestamp, maxTimestamp);
    }
    
    /**
     * Clear existing GPS data for a user within the specified date range.
     * 
     * @param userId The user whose data should be cleared
     * @param deletionRange The date range to clear
     * @return Number of GPS points deleted
     */
    @Transactional
    public int clearGpsDataInRange(UUID userId, DateRange deletionRange) {
        if (deletionRange == null) {
            return 0;
        }
        
        log.info("Clearing GPS data for user {} in range {} to {}", 
                userId, deletionRange.getStartDate(), deletionRange.getEndDate());
        
        // Delete GPS points within the specified range
        int deletedCount = entityManager.createQuery(
                "DELETE FROM GpsPointEntity g WHERE g.user.id = :userId " +
                "AND g.timestamp >= :startDate AND g.timestamp <= :endDate")
                .setParameter("userId", userId)
                .setParameter("startDate", deletionRange.getStartDate())
                .setParameter("endDate", deletionRange.getEndDate())
                .executeUpdate();
        
        log.info("Cleared {} GPS points for user {} in date range", deletedCount, userId);
        return deletedCount;
    }
    
    /**
     * Clear timeline data (stays and trips) for a user within the specified date range.
     * This is used when importing GeoPulse data that includes timeline information.
     * 
     * @param userId The user whose timeline data should be cleared
     * @param deletionRange The date range to clear
     * @return Number of timeline items deleted
     */
    @Transactional
    public int clearTimelineDataInRange(UUID userId, DateRange deletionRange) {
        if (deletionRange == null) {
            return 0;
        }
        
        log.info("Clearing timeline data for user {} in range {} to {}", 
                userId, deletionRange.getStartDate(), deletionRange.getEndDate());
        
        // Delete timeline stays within the specified range
        int deletedStays = entityManager.createQuery(
                "DELETE FROM TimelineStayEntity s WHERE s.user.id = :userId " +
                "AND s.timestamp >= :startDate AND s.timestamp <= :endDate")
                .setParameter("userId", userId)
                .setParameter("startDate", deletionRange.getStartDate())
                .setParameter("endDate", deletionRange.getEndDate())
                .executeUpdate();
        
        // Delete timeline trips within the specified range
        int deletedTrips = entityManager.createQuery(
                "DELETE FROM TimelineTripEntity t WHERE t.user.id = :userId " +
                "AND t.timestamp >= :startDate AND t.timestamp <= :endDate")
                .setParameter("userId", userId)
                .setParameter("startDate", deletionRange.getStartDate())
                .setParameter("endDate", deletionRange.getEndDate())
                .executeUpdate();
        
        // Delete data gaps within the specified range
        int deletedDataGaps = entityManager.createQuery(
                "DELETE FROM TimelineDataGapEntity d WHERE d.user.id = :userId " +
                "AND d.startTime >= :startDate AND d.endTime <= :endDate")
                .setParameter("userId", userId)
                .setParameter("startDate", deletionRange.getStartDate())
                .setParameter("endDate", deletionRange.getEndDate())
                .executeUpdate();
        
        int totalDeleted = deletedStays + deletedTrips + deletedDataGaps;
        log.info("Cleared {} timeline items for user {} in date range ({} stays, {} trips, {} data gaps)", 
                totalDeleted, userId, deletedStays, deletedTrips, deletedDataGaps);
        
        return totalDeleted;
    }
    
    /**
     * Represents a date range for data operations.
     */
    public static class DateRange {
        private final Instant startDate;
        private final Instant endDate;
        
        public DateRange(Instant startDate, Instant endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        public Instant getStartDate() {
            return startDate;
        }
        
        public Instant getEndDate() {
            return endDate;
        }
        
        @Override
        public String toString() {
            return String.format("DateRange{%s to %s}", startDate, endDate);
        }
    }
}