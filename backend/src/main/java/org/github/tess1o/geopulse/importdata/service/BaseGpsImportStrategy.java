package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Abstract base class for GPS-only import strategies (OwnTracks, Google Timeline, GPX).
 * Contains common logic for processing GPS data imports.
 */
@Slf4j
public abstract class BaseGpsImportStrategy implements ImportStrategy {
    
    @Inject
    protected UserRepository userRepository;
    
    @Inject
    protected BatchProcessor batchProcessor;
    
    @Inject
    protected TimelineImportHelper timelineImportHelper;
    
    @Inject
    protected ImportDataClearingService dataClearingService;
    
    @ConfigProperty(name = "geopulse.import.bulk-insert-batch-size", defaultValue = "500")
    @StaticInitSafe
    int bulkInsertBatchSize;
    
    @ConfigProperty(name = "geopulse.import.merge-batch-size", defaultValue = "250")
    @StaticInitSafe
    int mergeBatchSize;
    
    @Override
    public List<String> validateAndDetectDataTypes(ImportJob job) throws IOException {
        log.info("Validating {} data for user {}", getFormat(), job.getUserId());
        
        try {
            // Parse and validate format-specific data
            FormatValidationResult validationResult = validateFormatSpecificData(job);
            
            if (validationResult.getValidRecordCount() == 0) {
                throw new IllegalArgumentException(getFormat() + " file contains no valid GPS data");
            }
            
            log.info("{} validation successful: {} total records, {} valid GPS points",
                    getFormat(), validationResult.getTotalRecordCount(), validationResult.getValidRecordCount());

            // Store timestamps in job for use in clear mode
            job.setDataFirstTimestamp(validationResult.getFirstTimestamp());
            job.setDataLastTimestamp(validationResult.getLastTimestamp());

            return List.of(ExportImportConstants.DataTypes.RAW_GPS);
            
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("Invalid " + getFormat() + " format: " + e.getMessage());
        }
    }
    
    @Override
    public void processImportData(ImportJob job) throws IOException {
        log.info("Processing {} import data for user {}", getFormat(), job.getUserId());
        
        try {
            UserEntity user = userRepository.findById(job.getUserId());
            if (user == null) {
                throw new IllegalStateException("User not found: " + job.getUserId());
            }
            
            // Parse format-specific data and convert to GPS entities
            job.updateProgress(25, "Parsing import data...");
            List<GpsPointEntity> gpsEntities = parseAndConvertToGpsEntities(job, user);
            
            if (gpsEntities.isEmpty()) {
                log.warn("No GPS points to import for user {}", job.getUserId());
                job.updateProgress(100, "Import completed - no data to process");
                return;
            }
            
            // Find the data range for smart deletion logic
            ImportDataClearingService.DateRange fileDataRange = dataClearingService.extractDataRange(gpsEntities);
            
            // Clear existing data if requested
            if (job.getOptions().isClearDataBeforeImport()) {
                job.updateProgress(30, "Clearing existing data in date range...");
                ImportDataClearingService.DateRange deletionRange = dataClearingService.calculateDeletionRange(job, fileDataRange);
                if (deletionRange != null) {
                    int deletedCount = dataClearingService.clearGpsDataInRange(job.getUserId(), deletionRange);
                    log.info("Cleared {} existing GPS points before import", deletedCount);
                }
            }
            
            // Find the earliest timestamp for timeline generation
            Instant firstGpsTimestamp = gpsEntities.stream()
                    .map(GpsPointEntity::getTimestamp)
                    .min(Instant::compareTo)
                    .orElse(null);
            
            // Process GPS points in batches using appropriate mode
            boolean clearMode = job.getOptions().isClearDataBeforeImport();
            String importMode = clearMode ? "bulk inserting" : "merging with existing data";
            job.updateProgress(35, "Importing GPS points (" + importMode + ")...");
            
            int optimalBatchSize = getBatchSize(gpsEntities, clearMode);
            log.info("Using batch size {} for {} GPS points (dataset size: {}, clear mode: {})", 
                    optimalBatchSize, gpsEntities.size(), gpsEntities.size(), clearMode);
            
            BatchProcessor.BatchResult result = batchProcessor.processInBatches(gpsEntities, optimalBatchSize, clearMode, job, 35, 95);
            
            job.updateProgress(95, "Generating timeline...");
            
            // Trigger timeline generation since we only imported GPS data
            timelineImportHelper.triggerTimelineGenerationForImportedGpsData(job, firstGpsTimestamp);
            
            job.updateProgress(100, "Import completed successfully");
            
            log.info("{} import completed for user {}: {} imported, {} skipped from {} total GPS points",
                    getFormat(), job.getUserId(), result.imported, result.skipped, gpsEntities.size());
            
        } catch (Exception e) {
            log.error("Failed to process {} import for user {}: {}", getFormat(), job.getUserId(), e.getMessage(), e);
            throw new IOException("Failed to process " + getFormat() + " import: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse and validate format-specific data.
     * 
     * @param job The import job containing the data
     * @return Validation result with counts
     * @throws IOException if parsing fails
     */
    protected abstract FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException;
    
    /**
     * Parse format-specific data and convert to GPS entities.
     * 
     * @param job The import job containing the data
     * @param user The user entity
     * @return List of GPS point entities ready for batch processing
     * @throws IOException if parsing fails
     */
    protected abstract List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException;
    
    /**
     * Get the optimal batch size for this format based on data characteristics.
     * Implements tiered batch sizing: larger batches for smaller datasets, smaller batches for larger datasets.
     * 
     * @return The batch size to use for processing
     */
    protected int getBatchSize(List<GpsPointEntity> gpsEntities, boolean clearMode) {
        int datasetSize = gpsEntities.size();
        int baseBatchSize = clearMode ? bulkInsertBatchSize : mergeBatchSize;
        
        // Tiered batch sizing based on dataset size
        if (datasetSize < 10_000) {
            // Small datasets: use larger batches (2x base size)
            return Math.min(baseBatchSize * 2, 1000);
        } else if (datasetSize < 100_000) {
            // Medium datasets: use configured batch size
            return baseBatchSize;
        } else {
            // Large datasets: use smaller batches (half base size)
            return Math.max(baseBatchSize / 2, 100);
        }
    }
    
    /**
     * Get the optimal batch size for this format.
     * Legacy method for backward compatibility.
     * 
     * @return The default batch size
     */
    protected int getBatchSize() {
        return bulkInsertBatchSize;
    }
    
    /**
     * Apply date range filter to a GPS entity if specified in import options.
     * 
     * @param timestamp The timestamp to check
     * @param job The import job with potential date range filter
     * @return true if the entity should be skipped due to date filter
     */
    protected boolean shouldSkipDueDateFilter(Instant timestamp, ImportJob job) {
        if (job.getOptions().getDateRangeFilter() == null || timestamp == null) {
            return false;
        }
        
        return timestamp.isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
               timestamp.isAfter(job.getOptions().getDateRangeFilter().getEndDate());
    }
    
    /**
     * Update import progress periodically during processing.
     * 
     * @param processed Number of items processed
     * @param total Total number of items to process
     * @param job The import job to update
     * @param baseProgress Base progress percentage (items processing is between baseProgress and baseProgress + range)
     * @param range Progress range allocated for item processing
     */
    protected void updateProgress(int processed, int total, ImportJob job, int baseProgress, int range) {
        if (processed % 1000 == 0 || processed == total) {
            int progress = baseProgress + (int) ((double) processed / total * range);
            job.setProgress(Math.min(progress, baseProgress + range));
        }
    }
    
    /**
     * Validation result containing counts of processed records.
     */
    protected static class FormatValidationResult {
        private final int totalRecordCount;
        private final int validRecordCount;
        private final Instant firstTimestamp;
        private final Instant lastTimestamp;

        public FormatValidationResult(int totalRecordCount, int validRecordCount) {
            this(totalRecordCount, validRecordCount, null, null);
        }

        public FormatValidationResult(int totalRecordCount, int validRecordCount,
                                     Instant firstTimestamp, Instant lastTimestamp) {
            this.totalRecordCount = totalRecordCount;
            this.validRecordCount = validRecordCount;
            this.firstTimestamp = firstTimestamp;
            this.lastTimestamp = lastTimestamp;
        }

        public int getTotalRecordCount() {
            return totalRecordCount;
        }

        public int getValidRecordCount() {
            return validRecordCount;
        }

        public Instant getFirstTimestamp() {
            return firstTimestamp;
        }

        public Instant getLastTimestamp() {
            return lastTimestamp;
        }
    }
}