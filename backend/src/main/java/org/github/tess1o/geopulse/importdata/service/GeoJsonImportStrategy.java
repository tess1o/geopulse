package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Use template method pattern to handle streaming import workflow.
     */
    @Override
    public void processImportData(ImportJob job) throws IOException {
        processStreamingImport(job, this::streamingImportWithDirectWrites,
                "Parsing and inserting GPS data...", "features");
    }

    /**
     * Perform streaming import with direct database writes - NO entity accumulation.
     * This is the true memory-efficient implementation.
     */
    private StreamingImportResult streamingImportWithDirectWrites(ImportJob job, UserEntity user, boolean clearMode)
            throws IOException {

        int batchSize = settingsService.getInteger("import.geojson-streaming-batch-size");
        List<GpsPointEntity> currentBatch = new ArrayList<>(batchSize);
        AtomicInteger totalImported = new AtomicInteger(0);
        AtomicInteger totalSkipped = new AtomicInteger(0);
        AtomicInteger totalFeatures = new AtomicInteger(0);
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);

        // Get total expected points from validation for accurate progress tracking
        int totalExpectedPoints = job.getTotalRecordsFromValidation();

        log.info("Starting streaming import with batch size: {}, clear mode: {}, total expected points: {}, from {}",
                batchSize, clearMode, totalExpectedPoints, job.hasTempFile() ? "temp file" : "memory");

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingGeoJsonParser parser = new StreamingGeoJsonParser(dataStream, objectMapper);

        parser.parseFeatures((feature, stats) -> {
            totalFeatures.incrementAndGet();

            if (!feature.hasValidGeometry()) {
                return;
            }

            GeoJsonGeometry geometry = feature.getGeometry();
            GeoJsonProperties properties = feature.getProperties();

            if (geometry instanceof GeoJsonPoint point) {
                GpsPointEntity gpsPoint = convertPointToGpsPoint(point, properties, user, job);
                if (gpsPoint != null) {
                    addToBatchAndFlushIfNeeded(currentBatch, gpsPoint, firstTimestamp,
                        totalImported, totalSkipped, clearMode, job, totalExpectedPoints, batchSize);
                }
            } else if (geometry instanceof GeoJsonLineString lineString) {
                for (GeoJsonPoint point : lineString.getPoints()) {
                    GpsPointEntity gpsPoint = convertPointToGpsPoint(point, properties, user, job);
                    if (gpsPoint != null) {
                        addToBatchAndFlushIfNeeded(currentBatch, gpsPoint, firstTimestamp,
                            totalImported, totalSkipped, clearMode, job, totalExpectedPoints, batchSize);
                    }
                }
            }
        });

            // Flush any remaining batch
            if (!currentBatch.isEmpty()) {
                flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedPoints);
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
            boolean clearMode,
            ImportJob job,
            int totalExpectedPoints,
            int batchSize) {

        // Track first timestamp for timeline generation
        if (firstTimestamp.get() == null && gpsPoint.getTimestamp() != null) {
            firstTimestamp.set(gpsPoint.getTimestamp());
        }

        currentBatch.add(gpsPoint);

        // Flush when batch is full
        if (currentBatch.size() >= batchSize) {
            flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedPoints);
            currentBatch.clear(); // CRITICAL: Clear to release memory

            // Update progress after DB flush based on actual imported count
            // Progress from 20% to 70% (50% range) based on database writes
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
        if (isOutsideDateRange(timestamp, job)) {
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
