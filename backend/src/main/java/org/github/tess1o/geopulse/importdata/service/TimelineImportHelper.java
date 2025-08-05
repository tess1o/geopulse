package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.timeline.service.TimelineBackgroundService;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Helper service for handling timeline generation after bulk imports.
 * Uses the new simplified background service with priority queues.
 */
@ApplicationScoped
@Slf4j
public class TimelineImportHelper {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineBackgroundService backgroundService;

    /**
     * Trigger timeline generation for imported GPS data.
     * Uses low-priority queue since bulk imports can wait for system resources.
     */
    public void triggerTimelineGenerationForImportedGpsData(ImportJob job) {
        log.info("Triggering timeline generation for bulk import for user {}", job.getUserId());
        
        try {
            // Find date range that has GPS data for this user
            List<LocalDate> datesWithGpsData = findDatesWithGpsData(job.getUserId(), job);
            
            if (datesWithGpsData.isEmpty()) {
                log.warn("No GPS data found for timeline generation after import for user {}", job.getUserId());
                return;
            }

            // Get overall date range
            LocalDate startDate = datesWithGpsData.stream().min(LocalDate::compareTo).orElse(LocalDate.now());
            LocalDate endDate = datesWithGpsData.stream().max(LocalDate::compareTo).orElse(LocalDate.now());

            // Queue low-priority regeneration for the entire date range
            backgroundService.queueLowPriorityRegeneration(job.getUserId(), startDate, endDate);
            
            log.info("Successfully queued timeline generation for bulk import for user {} covering {} dates ({} to {})", 
                    job.getUserId(), datesWithGpsData.size(), startDate, endDate);
            
        } catch (Exception e) {
            log.error("Failed to trigger timeline generation for bulk import for user {}: {}", 
                     job.getUserId(), e.getMessage(), e);
            // Don't fail the entire import - GPS data is still imported successfully
        }
    }

    /**
     * Find dates that have GPS data for a user, optionally filtered by import job date range.
     */
    private List<LocalDate> findDatesWithGpsData(UUID userId, ImportJob job) {
        // Get distinct dates with GPS data for this user
        List<java.time.Instant> timestamps = gpsPointRepository.findDistinctTimestampsByUser(userId);
        
        List<LocalDate> dates = timestamps.stream()
            .map(timestamp -> timestamp.atZone(ZoneOffset.UTC).toLocalDate())
            .distinct()
            .sorted()
            .toList();

        // Apply date range filter if specified in import job
        if (job.getOptions() != null && job.getOptions().getDateRangeFilter() != null) {
            LocalDate filterStart = job.getOptions().getDateRangeFilter().getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate filterEnd = job.getOptions().getDateRangeFilter().getEndDate().atZone(ZoneOffset.UTC).toLocalDate();
            
            dates = dates.stream()
                .filter(date -> !date.isBefore(filterStart) && !date.isAfter(filterEnd))
                .toList();
                
            log.debug("Applied date filter {}-{}: {} dates", filterStart, filterEnd, dates.size());
        }

        log.debug("Found {} dates with GPS data for user {}", dates.size(), userId);
        return dates;
    }

    /**
     * Check if timeline generation is needed after import.
     * Only needed if we imported GPS data but no pre-computed timeline data.
     */
    public boolean isTimelineGenerationNeeded(boolean hasGpsData, boolean hasTimelineData) {
        return hasGpsData && !hasTimelineData;
    }
}