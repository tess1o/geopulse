package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.integrations.geojson.StreamingGeoJsonParser;
import org.github.tess1o.geopulse.gps.integrations.geojson.model.*;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Import strategy for GeoJSON format.
 * Supports both Point and LineString geometries.
 */
@ApplicationScoped
@Slf4j
public class GeoJsonImportStrategy extends BaseGpsImportStrategy {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    /**
     * Batch size for streaming processing - aligns with DB batch sizes for optimal performance.
     * Uses bulk insert batch size since streaming is most beneficial with clear mode.
     */
    @ConfigProperty(name = "geopulse.import.geojson.streaming-batch-size", defaultValue = "500")
    @StaticInitSafe
    int streamingBatchSize;

    @Override
    public String getFormat() {
        return "geojson";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        // Use streaming parser for validation - no memory overhead
        log.info("Validating GeoJSON file using streaming parser (memory-efficient from {}){}",
                job.hasTempFile() ? "temp file" : "memory");

        // Track timestamps during validation for use in clear mode
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);
        AtomicReference<Instant> lastTimestamp = new AtomicReference<>(null);

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingGeoJsonParser parser = new StreamingGeoJsonParser(dataStream, objectMapper);

            // Parse through entire file to validate structure, count points, and track timestamps
            StreamingGeoJsonParser.ParsingStats stats = parser.parseFeatures((feature, currentStats) -> {
            if (!feature.hasValidGeometry()) {
                return;
            }

            // Extract timestamp from properties
            GeoJsonProperties properties = feature.getProperties();
            Instant timestamp = parseTimestamp(properties);

            if (timestamp != null) {
                if (firstTimestamp.get() == null || timestamp.isBefore(firstTimestamp.get())) {
                    firstTimestamp.set(timestamp);
                }
                if (lastTimestamp.get() == null || timestamp.isAfter(lastTimestamp.get())) {
                    lastTimestamp.set(timestamp);
                }
            }
            });

            if (stats.totalFeatures == 0) {
                throw new IllegalArgumentException("GeoJSON file contains no features");
            }

            if (stats.validPoints == 0) {
                throw new IllegalArgumentException("GeoJSON file contains no valid GPS points");
            }

            log.info("GeoJSON streaming validation successful: {} features, {} total points, {} valid GPS points, date range: {} to {}",
                    stats.totalFeatures, stats.totalPoints, stats.validPoints,
                    firstTimestamp.get(), lastTimestamp.get());

            return new FormatValidationResult(stats.totalPoints, stats.validPoints,
                    firstTimestamp.get(), lastTimestamp.get());
        }
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        // This method should NEVER be called for GeoJSON because we override processImportData()
        // If this executes, something is wrong - we want to know about it!
        throw new UnsupportedOperationException(
            "parseAndConvertToGpsEntities should not be called for GeoJSON! " +
            "GeoJSON uses streaming import via processImportData() override. " +
            "This method loads all entities in memory and should never execute.");

        // Old implementation kept below for reference only (unreachable code)
        /*
        List<GpsPointEntity> allGpsPoints = new ArrayList<>();
        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(job.getZipData(), objectMapper);
        parser.parseFeatures((feature, stats) -> {
            if (!feature.hasValidGeometry()) {
                return;
            }

            GeoJsonGeometry geometry = feature.getGeometry();
            GeoJsonProperties properties = feature.getProperties();

            if (geometry instanceof GeoJsonPoint point) {
                GpsPointEntity gpsPoint = convertPointToGpsPoint(point, properties, user, job);
                if (gpsPoint != null) {
                    allGpsPoints.add(gpsPoint);
                }
            } else if (geometry instanceof GeoJsonLineString lineString) {
                for (GeoJsonPoint point : lineString.getPoints()) {
                    GpsPointEntity gpsPoint = convertPointToGpsPoint(point, properties, user, job);
                    if (gpsPoint != null) {
                        allGpsPoints.add(gpsPoint);
                    }
                }
            }
        });
        return allGpsPoints;
        */
    }

    /**
     * Override processImportData to use true streaming without accumulating all entities in memory.
     * This is the key optimization for handling large files (800MB+).
     */
    @Override
    public void processImportData(ImportJob job) throws IOException {
        log.info("Processing GeoJSON import using TRUE STREAMING mode (minimal memory footprint)");

        try {
            UserEntity user = userRepository.findById(job.getUserId());
            if (user == null) {
                throw new IllegalStateException("User not found: " + job.getUserId());
            }

            // For streaming mode, we need to:
            // 1. Optionally clear data (requires date range, which requires one pass)
            // 2. Stream and write directly to DB
            // 3. Track first timestamp for timeline generation

            boolean clearMode = job.getOptions().isClearDataBeforeImport();

            // For clear mode, use timestamps captured during validation to delete old data
            // This avoids parsing the file twice!
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
            job.updateProgress(25, "Streaming import with direct database writes...");
            StreamingImportResult result = streamingImportWithDirectWrites(job, user, clearMode);

            // Use timestamp from validation, or fall back to streaming result
            if (firstTimestamp == null) {
                firstTimestamp = result.firstTimestamp;
            }

            job.updateProgress(95, "Generating timeline...");
            timelineImportHelper.triggerTimelineGenerationForImportedGpsData(job, firstTimestamp);

            job.updateProgress(100, "Import completed successfully");

            log.info("GeoJSON streaming import completed: {} imported, {} skipped from {} total features",
                    result.imported, result.skipped, result.totalFeatures);

        } catch (Exception e) {
            log.error("Failed to process GeoJSON streaming import: {}", e.getMessage(), e);
            throw new IOException("Failed to process GeoJSON import: " + e.getMessage(), e);
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
        AtomicInteger totalFeatures = new AtomicInteger(0);
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);

        log.info("Starting streaming import with batch size: {}, clear mode: {}, from {}",
                streamingBatchSize, clearMode, job.hasTempFile() ? "temp file" : "memory");

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingGeoJsonParser parser = new StreamingGeoJsonParser(dataStream, objectMapper);

        parser.parseFeatures((feature, stats) -> {
            totalFeatures.set(stats.totalFeatures);

            if (!feature.hasValidGeometry()) {
                return;
            }

            GeoJsonGeometry geometry = feature.getGeometry();
            GeoJsonProperties properties = feature.getProperties();

            if (geometry instanceof GeoJsonPoint point) {
                GpsPointEntity gpsPoint = convertPointToGpsPoint(point, properties, user, job);
                if (gpsPoint != null) {
                    addToBatchAndFlushIfNeeded(currentBatch, gpsPoint, firstTimestamp,
                        totalImported, totalSkipped, clearMode);
                }
            } else if (geometry instanceof GeoJsonLineString lineString) {
                for (GeoJsonPoint point : lineString.getPoints()) {
                    GpsPointEntity gpsPoint = convertPointToGpsPoint(point, properties, user, job);
                    if (gpsPoint != null) {
                        addToBatchAndFlushIfNeeded(currentBatch, gpsPoint, firstTimestamp,
                            totalImported, totalSkipped, clearMode);
                    }
                }
            }

            // Update progress periodically
            if (totalFeatures.get() > 0 && totalFeatures.get() % 10000 == 0) {
                int progress = 30 + (int) ((double) totalFeatures.get() / stats.totalFeatures * 65);
                job.updateProgress(progress, "Streaming import: " + totalFeatures.get() + " features processed");
            }
        });

            // Flush any remaining batch
            if (!currentBatch.isEmpty()) {
                flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped);
            }

            log.info("Streaming import completed: {} features processed, {} points imported, {} skipped",
                    totalFeatures.get(), totalImported.get(), totalSkipped.get());

            return new StreamingImportResult(
                totalImported.get(),
                totalSkipped.get(),
                totalFeatures.get(),
                firstTimestamp.get()
            );
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
            boolean clearMode) {

        // Track first timestamp for timeline generation
        if (firstTimestamp.get() == null && gpsPoint.getTimestamp() != null) {
            firstTimestamp.set(gpsPoint.getTimestamp());
        }

        currentBatch.add(gpsPoint);

        // Flush when batch is full
        if (currentBatch.size() >= streamingBatchSize) {
            flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped);
            currentBatch.clear(); // CRITICAL: Clear to release memory
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
            AtomicInteger totalSkipped) {

        if (batch.isEmpty()) {
            return;
        }

        try {
            int imported = batchProcessor.processBatch(batch, clearMode);
            totalImported.addAndGet(imported);
            totalSkipped.addAndGet(batch.size() - imported);

            log.debug("Flushed batch of {} points to database ({} imported, {} skipped)",
                    batch.size(), imported, batch.size() - imported);

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
        final int totalFeatures;
        final Instant firstTimestamp;

        StreamingImportResult(int imported, int skipped, int totalFeatures, Instant firstTimestamp) {
            this.imported = imported;
            this.skipped = skipped;
            this.totalFeatures = totalFeatures;
            this.firstTimestamp = firstTimestamp;
        }
    }


    private GpsPointEntity convertPointToGpsPoint(GeoJsonPoint point, GeoJsonProperties properties,
                                                   UserEntity user, ImportJob job) {
        if (!point.hasValidCoordinates()) {
            return null;
        }

        // Parse timestamp from properties
        Instant timestamp = parseTimestamp(properties);
        if (timestamp == null) {
            log.warn("Skipping GPS point without valid timestamp");
            return null;
        }

        // Apply date range filter using base class method
        if (shouldSkipDueDateFilter(timestamp, job)) {
            return null;
        }

        try {
            GpsPointEntity gpsEntity = new GpsPointEntity();
            gpsEntity.setUser(user);
            gpsEntity.setDeviceId(properties != null && properties.getDeviceId() != null
                    ? properties.getDeviceId()
                    : "geojson-import");
            gpsEntity.setCoordinates(GeoUtils.createPoint(point.getLongitude(), point.getLatitude()));
            gpsEntity.setTimestamp(timestamp);
            gpsEntity.setSourceType(GpsSourceType.GEOJSON);
            gpsEntity.setCreatedAt(Instant.now());

            // Set altitude if available (prefer geometry altitude, fallback to properties)
            Double altitude = point.getAltitude();
            if (altitude == null && properties != null) {
                altitude = properties.getAltitude();
            }
            if (altitude != null) {
                gpsEntity.setAltitude(altitude);
            }

            // Set velocity if available
            if (properties != null && properties.getVelocity() != null) {
                gpsEntity.setVelocity(properties.getVelocity());
            }

            // Set accuracy if available
            if (properties != null && properties.getAccuracy() != null) {
                gpsEntity.setAccuracy(properties.getAccuracy());
            }

            // Set battery if available
            if (properties != null && properties.getBattery() != null) {
                gpsEntity.setBattery(properties.getBattery().doubleValue());
            }

            return gpsEntity;

        } catch (Exception e) {
            log.warn("Failed to create GPS entity from GeoJSON point: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse timestamp from GeoJSON properties.
     * Supports ISO-8601 format and Unix timestamps (seconds or milliseconds).
     */
    private Instant parseTimestamp(GeoJsonProperties properties) {
        if (properties == null || properties.getTimestamp() == null) {
            return null;
        }

        String timestampStr = properties.getTimestamp();

        try {
            // Try parsing as ISO-8601 timestamp
            return Instant.parse(timestampStr);
        } catch (DateTimeParseException e) {
            // Try parsing as Unix timestamp (seconds or milliseconds)
            try {
                long timestamp = Long.parseLong(timestampStr);
                // If timestamp is in milliseconds (> year 2001 in seconds), convert to seconds
                if (timestamp > 1_000_000_000_000L) {
                    return Instant.ofEpochMilli(timestamp);
                } else {
                    return Instant.ofEpochSecond(timestamp);
                }
            } catch (NumberFormatException ex) {
                log.warn("Unable to parse timestamp: {}", timestampStr);
                return null;
            }
        }
    }
}
