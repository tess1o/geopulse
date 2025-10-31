package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.integrations.gpx.StreamingGpxParser;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Import strategy for GPX (GPS Exchange Format) files.
 * Uses streaming parser for memory-efficient import - processes large files without loading into RAM.
 */
@ApplicationScoped
@Slf4j
public class GpxImportStrategy extends BaseGpsImportStrategy {

    /**
     * Batch size for streaming processing - aligns with DB batch sizes for optimal performance.
     */
    @ConfigProperty(name = "geopulse.import.gpx.streaming-batch-size", defaultValue = "500")
    @StaticInitSafe
    int streamingBatchSize;

    @Override
    public String getFormat() {
        return "gpx";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        // Use streaming parser for validation - no memory overhead
        log.info("Validating GPX file using streaming parser (memory-efficient from {})",
                job.hasTempFile() ? "temp file" : "memory");

        // Track timestamps during validation for use in clear mode
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);
        AtomicReference<Instant> lastTimestamp = new AtomicReference<>(null);

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingGpxParser parser = new StreamingGpxParser(dataStream);

            // Parse through entire file to validate structure, count points, and track timestamps
            StreamingGpxParser.ParsingStats stats = parser.parseGpsPoints((point, currentStats) -> {
                // Track timestamp range
                if (point.time != null) {
                    if (firstTimestamp.get() == null || point.time.isBefore(firstTimestamp.get())) {
                        firstTimestamp.set(point.time);
                    }
                    if (lastTimestamp.get() == null || point.time.isAfter(lastTimestamp.get())) {
                        lastTimestamp.set(point.time);
                    }
                }
            });

            if (stats.totalGpsPoints == 0) {
                throw new IllegalArgumentException("GPX file contains no valid GPS points with timestamps");
            }

            log.info("GPX streaming validation successful: {} track points, {} waypoints, {} total GPS points, date range: {} to {}",
                    stats.totalTrackPoints, stats.totalWaypoints, stats.totalGpsPoints,
                    firstTimestamp.get(), lastTimestamp.get());

            return new FormatValidationResult(stats.totalGpsPoints, stats.totalGpsPoints,
                    firstTimestamp.get(), lastTimestamp.get());
        }
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        // This method should NEVER be called for GPX because we override processImportData()
        throw new UnsupportedOperationException(
            "parseAndConvertToGpsEntities should not be called for GPX! " +
            "GPX uses streaming import via processImportData() override. " +
            "This method loads all entities in memory and should never execute.");
    }

    /**
     * Override processImportData to use true streaming without accumulating all entities in memory.
     * This is the key optimization for handling large files.
     */
    @Override
    public void processImportData(ImportJob job) throws IOException {
        log.info("Processing GPX import using TRUE STREAMING mode (minimal memory footprint)");

        try {
            UserEntity user = userRepository.findById(job.getUserId());
            if (user == null) {
                throw new IllegalStateException("User not found: " + job.getUserId());
            }

            // For streaming mode, we need to:
            // 1. Optionally clear data (requires date range from validation)
            // 2. Stream and write directly to DB
            // 3. Track first timestamp for timeline generation

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
            job.updateProgress(20, "Parsing and inserting GPS data...");
            StreamingImportResult result = streamingImportWithDirectWrites(job, user, clearMode);

            // Use timestamp from validation, or fall back to streaming result
            if (firstTimestamp == null) {
                firstTimestamp = result.firstTimestamp;
            }

            job.updateProgress(70, "Generating timeline (may include reverse geocoding)...");
            timelineImportHelper.triggerTimelineGenerationForImportedGpsData(job, firstTimestamp);

            job.updateProgress(100, "Import completed successfully");

            log.warn("GPX streaming import STATISTICS: {} imported, {} skipped from {} total GPS points (SKIP RATE: {}%). " +
                    "Note: Skipped points may include duplicates (detected by unique constraint) and filtered points.",
                    result.imported, result.skipped, result.totalGpsPoints,
                    result.totalGpsPoints > 0 ? (result.skipped * 100.0 / result.totalGpsPoints) : 0);

        } catch (Exception e) {
            log.error("Failed to process GPX streaming import: {}", e.getMessage(), e);
            throw new IOException("Failed to process GPX import: " + e.getMessage(), e);
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
     * Perform streaming import with direct database writes - NO entity accumulation.
     * This is the true memory-efficient implementation.
     */
    private StreamingImportResult streamingImportWithDirectWrites(ImportJob job, UserEntity user, boolean clearMode)
            throws IOException {

        List<GpsPointEntity> currentBatch = new ArrayList<>(streamingBatchSize);
        AtomicInteger totalImported = new AtomicInteger(0);
        AtomicInteger totalSkipped = new AtomicInteger(0);
        AtomicInteger totalGpsPoints = new AtomicInteger(0);
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);

        // Get total expected points from validation for accurate progress tracking
        int totalExpectedPoints = job.getTotalRecordsFromValidation();

        log.info("Starting GPX streaming import with batch size: {}, clear mode: {}, total expected points: {}, from {}",
                streamingBatchSize, clearMode, totalExpectedPoints, job.hasTempFile() ? "temp file" : "memory");

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingGpxParser parser = new StreamingGpxParser(dataStream);

            parser.parseGpsPoints((point, stats) -> {
                totalGpsPoints.incrementAndGet();

                // Apply date range filter if specified
                if (shouldSkipDueDateFilter(point.time, job)) {
                    return;
                }

                // Convert GPX point to GPS entity
                GpsPointEntity gpsEntity = convertGpxPointToGpsEntity(point, user);
                if (gpsEntity != null) {
                    addToBatchAndFlushIfNeeded(currentBatch, gpsEntity, firstTimestamp,
                        totalImported, totalSkipped, clearMode, job, totalExpectedPoints);
                }
            });

            // Flush any remaining batch
            if (!currentBatch.isEmpty()) {
                flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedPoints);
            }

            log.info("GPX streaming import completed: {} GPS points processed, {} imported, {} skipped",
                    totalGpsPoints.get(), totalImported.get(), totalSkipped.get());

            return new StreamingImportResult(
                totalImported.get(),
                totalSkipped.get(),
                totalGpsPoints.get(),
                firstTimestamp.get()
            );
        }
    }

    /**
     * Add GPS point to current batch and flush to database when batch is full.
     */
    private void addToBatchAndFlushIfNeeded(
            List<GpsPointEntity> currentBatch,
            GpsPointEntity gpsPoint,
            AtomicReference<Instant> firstTimestamp,
            AtomicInteger totalImported,
            AtomicInteger totalSkipped,
            boolean clearMode,
            ImportJob job,
            int totalExpectedPoints) {

        // Track first timestamp for timeline generation
        if (firstTimestamp.get() == null && gpsPoint.getTimestamp() != null) {
            firstTimestamp.set(gpsPoint.getTimestamp());
        }

        currentBatch.add(gpsPoint);

        // Flush when batch is full
        if (currentBatch.size() >= streamingBatchSize) {
            flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedPoints);
            currentBatch.clear(); // CRITICAL: Clear to release memory

            // Update progress after DB flush
            if (totalExpectedPoints > 0) {
                int processed = totalImported.get() + totalSkipped.get();
                int progress = 20 + (int)((double)processed / totalExpectedPoints * 50);
                job.updateProgress(Math.min(progress, 70),
                    "Importing to database: " + processed + " / " + totalExpectedPoints + " GPS points processed");
            }
        }
    }

    /**
     * Flush a batch directly to the database and clear it from memory.
     */
    private void flushBatchToDatabase(
            List<GpsPointEntity> batch,
            boolean clearMode,
            AtomicInteger totalImported,
            AtomicInteger totalSkipped,
            int totalExpected) {

        if (batch.isEmpty()) {
            return;
        }

        try {
            int alreadyProcessed = totalImported.get() + totalSkipped.get();
            int imported = batchProcessor.processBatch(batch, clearMode, alreadyProcessed, totalExpected);
            totalImported.addAndGet(imported);
            totalSkipped.addAndGet(batch.size() - imported);
        } catch (Exception e) {
            log.error("Failed to flush batch to database: {}", e.getMessage(), e);
            // Continue processing even if one batch fails
        }
    }

    /**
     * Convert StreamingGpxParser.GpxPoint to GpsPointEntity
     */
    private GpsPointEntity convertGpxPointToGpsEntity(StreamingGpxParser.GpxPoint point, UserEntity user) {
        try {
            GpsPointEntity gpsEntity = new GpsPointEntity();
            gpsEntity.setUser(user);
            gpsEntity.setDeviceId("trackpoint".equals(point.type) ? "gpx-import" : "gpx-waypoint-import");
            gpsEntity.setCoordinates(GeoUtils.createPoint(point.lon, point.lat));
            gpsEntity.setTimestamp(point.time);
            gpsEntity.setSourceType(GpsSourceType.GPX);
            gpsEntity.setCreatedAt(Instant.now());

            // Set elevation if available
            if (point.elevation != null) {
                gpsEntity.setAltitude(point.elevation);
            }

            // Set speed if available (convert from m/s to km/h for trackpoints)
            if (point.speed != null) {
                gpsEntity.setVelocity(point.speed * 3.6); // Convert m/s to km/h
            } else if ("waypoint".equals(point.type)) {
                // Waypoints are typically stationary
                gpsEntity.setVelocity(0.0);
            }

            return gpsEntity;

        } catch (Exception e) {
            log.warn("Failed to create GPS entity from GPX point: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Result of streaming import operation
     */
    private static class StreamingImportResult {
        final int imported;
        final int skipped;
        final int totalGpsPoints;
        final Instant firstTimestamp;

        StreamingImportResult(int imported, int skipped, int totalGpsPoints, Instant firstTimestamp) {
            this.imported = imported;
            this.skipped = skipped;
            this.totalGpsPoints = totalGpsPoints;
            this.firstTimestamp = firstTimestamp;
        }
    }
}
