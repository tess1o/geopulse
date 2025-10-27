package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.StreamingGoogleTimelineParser;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.GoogleTimelineGpsPoint;
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
 * Import strategy for Google Timeline JSON format.
 * Supports both legacy format (array of records) and new format (semantic segments).
 *
 * Uses streaming parser for memory-efficient import - processes large files (100MB+) without loading into RAM.
 */
@ApplicationScoped
@Slf4j
public class GoogleTimelineImportStrategy extends BaseGpsImportStrategy {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    /**
     * Batch size for streaming processing - aligns with DB batch sizes for optimal performance.
     */
    @ConfigProperty(name = "geopulse.import.googletimeline.streaming-batch-size", defaultValue = "500")
    @StaticInitSafe
    int streamingBatchSize;

    @Override
    public String getFormat() {
        return "google-timeline";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        // Use streaming parser for validation - no memory overhead
        log.info("Validating Google Timeline file using streaming parser (memory-efficient from {})",
                job.hasTempFile() ? "temp file" : "memory");

        // Track timestamps during validation for use in clear mode
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);
        AtomicReference<Instant> lastTimestamp = new AtomicReference<>(null);

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingGoogleTimelineParser parser = new StreamingGoogleTimelineParser(dataStream, objectMapper);

            // Parse through entire file to validate structure, count points, and track timestamps
            StreamingGoogleTimelineParser.ParsingStats stats = parser.parseGpsPoints((gpsPoint, currentStats) -> {
                // Just track timestamps during validation - don't process GPS points yet
                if (gpsPoint.getTimestamp() != null) {
                    if (firstTimestamp.get() == null || gpsPoint.getTimestamp().isBefore(firstTimestamp.get())) {
                        firstTimestamp.set(gpsPoint.getTimestamp());
                    }
                    if (lastTimestamp.get() == null || gpsPoint.getTimestamp().isAfter(lastTimestamp.get())) {
                        lastTimestamp.set(gpsPoint.getTimestamp());
                    }
                }
            });

            if (stats.totalGpsPoints == 0) {
                throw new IllegalArgumentException("Google Timeline file contains no valid GPS points");
            }

            log.info("Google Timeline streaming validation successful: format={}, records={}, segments={}, rawSignals={}, totalGpsPoints={}, date range: {} to {}",
                    stats.formatType, stats.totalRecords, stats.totalSemanticSegments,
                    stats.totalRawSignals, stats.totalGpsPoints,
                    firstTimestamp.get(), lastTimestamp.get());

            // Return total GPS points as both totalPoints and validPoints since streaming parser only emits valid points
            return new FormatValidationResult(stats.totalGpsPoints, stats.totalGpsPoints,
                    firstTimestamp.get(), lastTimestamp.get());
        }
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        // This method should NEVER be called for Google Timeline because we override processImportData()
        // If this executes, something is wrong - we want to know about it!
        throw new UnsupportedOperationException(
            "parseAndConvertToGpsEntities should not be called for Google Timeline! " +
            "Google Timeline uses streaming import via processImportData() override. " +
            "This method loads all entities in memory and should never execute.");
    }

    /**
     * Override processImportData to use true streaming without accumulating all entities in memory.
     * This is the key optimization for handling large files (100MB+).
     */
    @Override
    public void processImportData(ImportJob job) throws IOException {
        log.info("Processing Google Timeline import using TRUE STREAMING mode (minimal memory footprint)");

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
            job.updateProgress(20, "Parsing and inserting GPS data...");
            StreamingImportResult result = streamingImportWithDirectWrites(job, user, clearMode);

            // Use timestamp from validation, or fall back to streaming result
            if (firstTimestamp == null) {
                firstTimestamp = result.firstTimestamp;
            }

            job.updateProgress(70, "Generating timeline (may include reverse geocoding)...");
            timelineImportHelper.triggerTimelineGenerationForImportedGpsData(job, firstTimestamp);

            job.updateProgress(100, "Import completed successfully");

            log.info("Google Timeline streaming import completed: {} imported, {} skipped from {} total GPS points",
                    result.imported, result.skipped, result.totalGpsPoints);

        } catch (Exception e) {
            log.error("Failed to process Google Timeline streaming import: {}", e.getMessage(), e);
            throw new IOException("Failed to process Google Timeline import: " + e.getMessage(), e);
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

        log.info("Starting streaming import with batch size: {}, clear mode: {}, total expected points: {}, from {}",
                streamingBatchSize, clearMode, totalExpectedPoints, job.hasTempFile() ? "temp file" : "memory");

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingGoogleTimelineParser parser = new StreamingGoogleTimelineParser(dataStream, objectMapper);

            parser.parseGpsPoints((gpsPoint, stats) -> {
                totalGpsPoints.incrementAndGet();

                // Convert Google Timeline GPS point to GpsPointEntity
                GpsPointEntity gpsEntity = convertGpsPointToEntity(gpsPoint, user, job);
                if (gpsEntity != null) {
                    addToBatchAndFlushIfNeeded(currentBatch, gpsEntity, firstTimestamp,
                        totalImported, totalSkipped, clearMode, job, totalExpectedPoints);
                }
            });

            // Flush any remaining batch
            if (!currentBatch.isEmpty()) {
                flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedPoints);
            }

            log.info("Streaming import completed: {} GPS points processed, {} points imported, {} skipped",
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
     * Convert Google Timeline GPS point to GpsPointEntity
     */
    private GpsPointEntity convertGpsPointToEntity(GoogleTimelineGpsPoint point, UserEntity user, ImportJob job) {
        // Skip points without valid coordinates or timestamp
        if (point.getTimestamp() == null ||
                !isValidCoordinate(point.getLatitude()) ||
                !isValidCoordinate(point.getLongitude())) {
            return null;
        }

        // Apply date range filter using base class method
        if (shouldSkipDueDateFilter(point.getTimestamp(), job)) {
            return null;
        }

        try {
            GpsPointEntity gpsEntity = new GpsPointEntity();
            gpsEntity.setUser(user);
            gpsEntity.setDeviceId("google-timeline-import");
            gpsEntity.setCoordinates(GeoUtils.createPoint(point.getLongitude(), point.getLatitude()));
            gpsEntity.setTimestamp(point.getTimestamp());
            gpsEntity.setSourceType(GpsSourceType.GOOGLE_TIMELINE);
            gpsEntity.setCreatedAt(Instant.now());

            // Set velocity if available (convert from m/s to km/h)
            if (point.getVelocityMs() != null) {
                gpsEntity.setVelocity(point.getVelocityMs() * 3.6); // Convert m/s to km/h
            }

            return gpsEntity;

        } catch (Exception e) {
            log.warn("Failed to create GPS entity from Google Timeline point: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Add GPS point to current batch and flush to database when batch is full.
     * This keeps memory usage constant by writing batches immediately.
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

            // Update progress after DB flush based on actual imported count
            // Progress from 20% to 70% (50% range) based on database writes, not parsing
            if (totalExpectedPoints > 0) {
                int imported = totalImported.get() + totalSkipped.get();
                int progress = 20 + (int)((double)imported / totalExpectedPoints * 50);
                job.updateProgress(Math.min(progress, 70),
                    "Importing to database: " + imported + " / " + totalExpectedPoints + " GPS points processed");
            }
        }
    }

    /**
     * Flush a batch directly to the database and clear it from memory.
     * This is where memory is freed continuously during import.
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


    private boolean isValidCoordinate(double coord) {
        return !Double.isNaN(coord) && !Double.isInfinite(coord);
    }

}