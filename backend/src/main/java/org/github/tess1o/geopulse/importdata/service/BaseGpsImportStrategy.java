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

            // Store timestamps and total count in job for use in clear mode and progress tracking
            job.setDataFirstTimestamp(validationResult.getFirstTimestamp());
            job.setDataLastTimestamp(validationResult.getLastTimestamp());
            job.setTotalRecordsFromValidation(validationResult.getValidRecordCount());

            return List.of(ExportImportConstants.DataTypes.RAW_GPS);
            
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("Invalid " + getFormat() + " format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Functional interface for streaming import operations.
     * Implementations should perform streaming import with direct database writes.
     */
    @FunctionalInterface
    protected interface StreamingImportFunction {
        StreamingImportResult execute(ImportJob job, UserEntity user, boolean clearMode) throws IOException;
    }

    /**
     * Template method for streaming import processing.
     * Contains the common workflow for all GPS streaming imports:
     * 1. User lookup
     * 2. Data clearing (if requested)
     * 3. Streaming import execution
     * 4. Timeline generation
     * 5. Error handling and temp file cleanup
     *
     * @param job The import job
     * @param streamingFunction The format-specific streaming import function
     * @param progressMessage The progress message to show during import
     * @param recordTypeName The name of the record type being imported (for logging)
     * @throws IOException if import fails
     */
    protected void processStreamingImport(
            ImportJob job,
            StreamingImportFunction streamingFunction,
            String progressMessage,
            String recordTypeName) throws IOException {

        log.info("Processing {} import using TRUE STREAMING mode (minimal memory footprint)", getFormat());

        try {
            UserEntity user = userRepository.findById(job.getUserId());
            if (user == null) {
                throw new IllegalStateException("User not found: " + job.getUserId());
            }

            boolean clearMode = job.getOptions().isClearDataBeforeImport();

            // For clear mode, use timestamps captured during validation to delete old data
            Instant firstTimestamp = job.getDataFirstTimestamp();

            if (clearMode && firstTimestamp != null && job.getDataLastTimestamp() != null) {
                job.updateProgress(20, "Clearing existing data in date range...");
                log.info("Clearing old data using timestamps from validation: {} to {}",
                        firstTimestamp, job.getDataLastTimestamp());

                ImportDataClearingService.DateRange deletionRange =
                    dataClearingService.calculateDeletionRange(job,
                        new ImportDataClearingService.DateRange(firstTimestamp, job.getDataLastTimestamp()));
                if (deletionRange != null) {
                    int deletedCount = dataClearingService.clearGpsDataInRange(user.getId(), deletionRange);
                    log.info("Cleared {} existing GPS points before streaming import", deletedCount);
                }
            }

            // Start streaming import with direct DB writes
            job.updateProgress(20, progressMessage);
            StreamingImportResult result = streamingFunction.execute(job, user, clearMode);

            // Use timestamp from validation, or fall back to streaming result
            if (firstTimestamp == null) {
                firstTimestamp = result.firstTimestamp;
            }

            job.updateProgress(70, "Generating timeline (may include reverse geocoding)...");
            timelineImportHelper.triggerTimelineGenerationForImportedGpsData(job, firstTimestamp);

            job.updateProgress(100, "Import completed successfully");

            // Log completion with appropriate message based on whether processedFiles is present
            if (result.processedFiles != null) {
                log.info("{} streaming import completed: processed {} files, {} imported, {} skipped from {} total {}",
                        getFormat(), result.processedFiles, result.imported, result.skipped,
                        result.totalRecords, recordTypeName);
            } else {
                log.info("{} streaming import completed: {} imported, {} skipped from {} total {}",
                        getFormat(), result.imported, result.skipped, result.totalRecords, recordTypeName);
            }

        } catch (Exception e) {
            log.error("Failed to process {} streaming import: {}", getFormat(), e.getMessage(), e);
            throw new IOException("Failed to process " + getFormat() + " import: " + e.getMessage(), e);
        } finally {
            // Clean up temp file if it exists (whether success or failure)
            if (job.hasTempFile()) {
                try {
                    java.nio.file.Path tempPath = java.nio.file.Paths.get(job.getTempFilePath());
                    if (java.nio.file.Files.exists(tempPath)) {
                        long fileSize = java.nio.file.Files.size(tempPath);
                        java.nio.file.Files.delete(tempPath);
                        log.info("Deleted temp file after import: {} ({} MB)",
                                job.getTempFilePath(), fileSize / (1024 * 1024));
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete temp file {}: {}",
                            job.getTempFilePath(), e.getMessage());
                }
            }
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