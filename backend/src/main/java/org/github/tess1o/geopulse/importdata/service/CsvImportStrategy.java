package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Import strategy for CSV format.
 * Parses CSV files with GPS point data.
 *
 * Expected CSV format:
 * timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type
 *
 * Required fields: timestamp, latitude, longitude
 * Optional fields: accuracy, velocity, altitude, battery, device_id, source_type
 */
@ApplicationScoped
@Slf4j
public class CsvImportStrategy extends BaseGpsImportStrategy {

    /**
     * Batch size for streaming processing - aligns with DB batch sizes for optimal performance.
     */
    @ConfigProperty(name = "geopulse.import.csv.streaming-batch-size", defaultValue = "500")
    @StaticInitSafe
    int streamingBatchSize;

    @Override
    public String getFormat() {
        return "csv";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        log.info("Validating CSV file using streaming parser (memory-efficient from {})",
                job.hasTempFile() ? "temp file" : "memory");

        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);
        AtomicReference<Instant> lastTimestamp = new AtomicReference<>(null);
        AtomicInteger totalRows = new AtomicInteger(0);
        AtomicInteger validRows = new AtomicInteger(0);

        try (InputStream dataStream = job.getDataStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(dataStream, StandardCharsets.UTF_8))) {

            // Read and validate header
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            // Validate header format
            if (!headerLine.startsWith("timestamp,latitude,longitude")) {
                throw new IllegalArgumentException("Invalid CSV header. Expected: timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type");
            }

            // Parse data rows to count and validate
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                totalRows.incrementAndGet();

                try {
                    CsvRow row = parseCsvRow(line);
                    if (row != null && row.isValid()) {
                        validRows.incrementAndGet();

                        // Track timestamps
                        if (firstTimestamp.get() == null || row.timestamp.isBefore(firstTimestamp.get())) {
                            firstTimestamp.set(row.timestamp);
                        }
                        if (lastTimestamp.get() == null || row.timestamp.isAfter(lastTimestamp.get())) {
                            lastTimestamp.set(row.timestamp);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping invalid CSV row (line {}): {}", totalRows.get(), e.getMessage());
                }
            }

            if (validRows.get() == 0) {
                throw new IllegalArgumentException("CSV file contains no valid GPS points");
            }

            log.info("CSV validation successful: {} total rows, {} valid GPS points, date range: {} to {}",
                    totalRows.get(), validRows.get(), firstTimestamp.get(), lastTimestamp.get());

            return new FormatValidationResult(totalRows.get(), validRows.get(),
                    firstTimestamp.get(), lastTimestamp.get());
        }
    }

    @Override
    public void processImportData(ImportJob job) throws IOException {
        processStreamingImport(job, this::streamingImportWithDirectWrites,
                "Parsing and inserting GPS data...", "rows");
    }

    /**
     * Perform streaming import with direct database writes - NO entity accumulation.
     */
    private StreamingImportResult streamingImportWithDirectWrites(ImportJob job, UserEntity user, boolean clearMode)
            throws IOException {

        List<GpsPointEntity> currentBatch = new ArrayList<>(streamingBatchSize);
        AtomicInteger totalImported = new AtomicInteger(0);
        AtomicInteger totalSkipped = new AtomicInteger(0);
        AtomicInteger totalRows = new AtomicInteger(0);
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);

        // Get total expected points from validation for accurate progress tracking
        int totalExpectedPoints = job.getTotalRecordsFromValidation();

        log.info("Starting CSV streaming import with batch size: {}, clear mode: {}, total expected points: {}, from {}",
                streamingBatchSize, clearMode, totalExpectedPoints, job.hasTempFile() ? "temp file" : "memory");

        try (InputStream dataStream = job.getDataStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(dataStream, StandardCharsets.UTF_8))) {

            // Skip header line
            reader.readLine();

            // Process data rows
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                totalRows.incrementAndGet();

                try {
                    CsvRow row = parseCsvRow(line);
                    if (row != null && row.isValid()) {
                        // Apply date range filter if specified
                        if (shouldSkipDueDateFilter(row.timestamp, job)) {
                            totalSkipped.incrementAndGet();
                            continue;
                        }

                        GpsPointEntity gpsPoint = convertRowToGpsPoint(row, user);
                        if (gpsPoint != null) {
                            addToBatchAndFlushIfNeeded(currentBatch, gpsPoint, firstTimestamp,
                                totalImported, totalSkipped, clearMode, job, totalExpectedPoints);
                        }
                    } else {
                        totalSkipped.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.debug("Skipping invalid CSV row (line {}): {}", totalRows.get(), e.getMessage());
                    totalSkipped.incrementAndGet();
                }
            }

            // Flush remaining batch
            if (!currentBatch.isEmpty()) {
                int processed = batchProcessor.processBatch(currentBatch, clearMode);
                totalImported.addAndGet(processed);
                currentBatch.clear();
            }

            log.info("CSV streaming import completed: {} rows processed, {} imported, {} skipped",
                    totalRows.get(), totalImported.get(), totalSkipped.get());

            return new StreamingImportResult(totalImported.get(), totalSkipped.get(),
                    totalRows.get(), firstTimestamp.get(), null);
        }
    }

    /**
     * Add GPS point to batch and flush if batch is full.
     */
    private void addToBatchAndFlushIfNeeded(List<GpsPointEntity> currentBatch, GpsPointEntity gpsPoint,
                                            AtomicReference<Instant> firstTimestamp,
                                            AtomicInteger totalImported, AtomicInteger totalSkipped,
                                            boolean clearMode, ImportJob job, int totalExpectedPoints) {
        currentBatch.add(gpsPoint);

        // Track first timestamp for timeline generation
        if (firstTimestamp.get() == null) {
            firstTimestamp.set(gpsPoint.getTimestamp());
        }

        // Flush batch if full
        if (currentBatch.size() >= streamingBatchSize) {
            int processed = batchProcessor.processBatch(currentBatch, clearMode);
            totalImported.addAndGet(processed);
            currentBatch.clear();

            // Update progress (20% to 70% range)
            updateProgress(totalImported.get(), totalExpectedPoints, job, 20, 50);
        }
    }

    /**
     * Parse a single CSV row into a CsvRow object.
     * Format: timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type
     */
    private CsvRow parseCsvRow(String line) {
        String[] parts = line.split(",", -1); // -1 to preserve trailing empty strings

        if (parts.length < 3) {
            throw new IllegalArgumentException("CSV row must have at least 3 fields (timestamp, latitude, longitude)");
        }

        CsvRow row = new CsvRow();

        try {
            // Required fields
            row.timestamp = Instant.parse(parts[0].trim());
            row.latitude = Double.parseDouble(parts[1].trim());
            row.longitude = Double.parseDouble(parts[2].trim());

            // Optional fields
            if (parts.length > 3 && !parts[3].trim().isEmpty()) {
                row.accuracy = Double.parseDouble(parts[3].trim());
            }
            if (parts.length > 4 && !parts[4].trim().isEmpty()) {
                row.velocity = Double.parseDouble(parts[4].trim());
            }
            if (parts.length > 5 && !parts[5].trim().isEmpty()) {
                row.altitude = Double.parseDouble(parts[5].trim());
            }
            if (parts.length > 6 && !parts[6].trim().isEmpty()) {
                row.battery = Double.parseDouble(parts[6].trim());
            }
            if (parts.length > 7 && !parts[7].trim().isEmpty()) {
                row.deviceId = parts[7].trim();
            }
            if (parts.length > 8 && !parts[8].trim().isEmpty()) {
                try {
                    row.sourceType = GpsSourceType.valueOf(parts[8].trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Invalid source type - will use default (CSV)
                    row.sourceType = GpsSourceType.CSV;
                }
            }

            return row;

        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid timestamp format. Expected ISO-8601 (e.g., 2024-01-15T10:30:00Z)");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value in CSV row");
        }
    }

    /**
     * Convert CSV row to GpsPointEntity.
     */
    private GpsPointEntity convertRowToGpsPoint(CsvRow row, UserEntity user) {
        // Validate required fields - latitude range: -90 to 90, longitude range: -180 to 180
        if (row.latitude < -90.0 || row.latitude > 90.0 || row.longitude < -180.0 || row.longitude > 180.0) {
            log.debug("Skipping GPS point with invalid coordinates: lat={}, lon={}", row.latitude, row.longitude);
            return null;
        }

        GpsPointEntity entity = new GpsPointEntity();
        entity.setUser(user);
        entity.setTimestamp(row.timestamp);
        entity.setCoordinates(GeoUtils.createPoint(row.longitude, row.latitude));
        entity.setAccuracy(row.accuracy);
        entity.setVelocity(row.velocity);
        entity.setAltitude(row.altitude);
        entity.setBattery(row.battery);
        entity.setDeviceId(row.deviceId);
        entity.setSourceType(row.sourceType != null ? row.sourceType : GpsSourceType.CSV);
        entity.setCreatedAt(Instant.now());

        return entity;
    }

    /**
     * Internal class to represent a parsed CSV row.
     */
    private static class CsvRow {
        Instant timestamp;
        Double latitude;
        Double longitude;
        Double accuracy;
        Double velocity;
        Double altitude;
        Double battery;
        String deviceId;
        GpsSourceType sourceType;

        boolean isValid() {
            return timestamp != null && latitude != null && longitude != null;
        }
    }
}
