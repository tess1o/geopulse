package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointFilterDTO;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.user.model.DistanceUnit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for generating CSV format exports using streaming
 * approach.
 * Memory-efficient: processes GPS points in batches without loading all data
 * into memory.
 */
@ApplicationScoped
@Slf4j
public class CsvExportService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int API_EXPORT_BATCH_SIZE = 1000;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    SystemSettingsService settingsService;

    @Inject
    ExportTempFileService tempFileService;

    public void generateCsvExport(OutputStream output, UUID userId, GpsPointFilterDTO filters,
                                  DistanceUnit distanceUnit) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            String velocityUnit = distanceUnit == DistanceUnit.KILOMETERS ? "km/h" : "mph";
            writer.write("timestamp,latitude,longitude,accuracy,battery,velocity(" + velocityUnit
                    + "),altitude,sourceType,telemetry\n");

            try {
                gpsPointRepository.streamByUserAndFilters(userId, filters, API_EXPORT_BATCH_SIZE, batch -> {
                    try {
                        for (GpsPointEntity point : batch) {
                            double velocity = point.getVelocity() != null ? point.getVelocity() : 0.0;
                            if (distanceUnit == DistanceUnit.MILES) {
                                velocity *= 0.621371;
                            }
                            writer.write(formatCsvRow(
                                    point.getTimestamp(),
                                    point.getLatitude(),
                                    point.getLongitude(),
                                    point.getAccuracy(),
                                    point.getBattery(),
                                    velocity,
                                    point.getAltitude(),
                                    point.getSourceType() != null ? point.getSourceType().name() : "",
                                    telemetryToJson(point.getTelemetry())));
                        }
                        writer.flush();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause(); // NOPMD - deliberately rethrow the original checked cause
            }
        }
    }

    /**
     * Generates a CSV export for the given export job using STREAMING approach.
     * Writes directly to a temporary file to avoid memory issues.
     *
     * @param job the export job
     * @throws IOException if an I/O error occurs
     */
    public void generateCsvExport(ExportJob job) throws IOException {
        log.info("Starting streaming CSV export for user {}", job.getUserId());

        job.updateProgress(5, "Initializing CSV export...");

        int batchSize = settingsService.getInteger("export.batch-size");

        // Create temp file
        Path tempFile = tempFileService.createTempFile(job.getJobId(), ".csv");

        try (OutputStream os = Files.newOutputStream(tempFile);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {

            // Write CSV header
            writer.write("timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type,telemetry\n");

            job.updateProgress(10, "Starting to stream GPS data...");

            int[] totalWritten = {0};
            int[] batchCount = {0};
            try {
                gpsPointRepository.streamByUserAndDateRangeForExport(
                        job.getUserId(),
                        job.getDateRange().getStartDate(),
                        job.getDateRange().getEndDate(),
                        batchSize,
                        batch -> {
                            try {
                                for (GpsPointEntity point : batch) {
                                    writer.write(formatCsvRow(
                                            point.getTimestamp(),
                                            point.getLatitude(),
                                            point.getLongitude(),
                                            point.getAccuracy(),
                                            point.getVelocity(),
                                            point.getAltitude(),
                                            point.getBattery(),
                                            point.getDeviceId(),
                                            point.getSourceType() != null ? point.getSourceType().name() : "",
                                            telemetryToJson(point.getTelemetry())));
                                    totalWritten[0]++;
                                }
                                writer.flush();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }

                            batchCount[0]++;

                            int progress = 10 + (int) (80.0 * totalWritten[0] / Math.max(totalWritten[0] + batchSize, 1));
                            progress = Math.min(progress, 90);
                            job.updateProgress(progress, String.format("Exporting GPS points: %d records", totalWritten[0]));

                            if (batchCount[0] % 10 == 0) {
                                log.debug("Streamed {} records in {} batches", totalWritten[0], batchCount[0]);
                            }
                        });
            } catch (UncheckedIOException e) {
                throw e.getCause(); // NOPMD - deliberately rethrow the original checked cause
            }

            log.info("Completed streaming CSV export: {} records in {} batches", totalWritten[0], batchCount[0]);
        }

        // Update job with file info
        job.setTempFilePath(tempFile.toString());
        job.setFileExtension(".csv");
        job.setContentType("text/csv");
        job.setFileSizeBytes(Files.size(tempFile));

        job.updateProgress(95, "Finalizing CSV export...");
        job.updateProgress(100, "Export completed");
    }

    private String formatCsvRow(Object... values) {
        StringBuilder row = new StringBuilder();
        for (Object value : values) {
            appendCsvValue(row, value);
        }
        row.append("\n");
        return row.toString();
    }

    private String telemetryToJson(Map<String, Object> telemetry) {
        if (telemetry == null || telemetry.isEmpty()) {
            return "";
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(telemetry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize telemetry to JSON for CSV export", e);
            return "";
        }
    }

    private void appendCsvValue(StringBuilder row, Object value) {
        if (row.length() > 0) {
            row.append(",");
        }
        String raw = value != null ? value.toString() : "";
        row.append(escapeCsv(raw));
    }

    private String escapeCsv(String value) {
        boolean shouldQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!shouldQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
