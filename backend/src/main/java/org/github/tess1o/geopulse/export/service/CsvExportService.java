package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

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

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    SystemSettingsService settingsService;

    @Inject
    ExportTempFileService tempFileService;

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
        java.nio.file.Path tempFile = tempFileService.createTempFile(job.getJobId(), ".csv");

        try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(tempFile);
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
                                    writer.write(formatCsvRow(point));
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
                throw e.getCause();
            }

            log.info("Completed streaming CSV export: {} records in {} batches", totalWritten[0], batchCount[0]);
        }

        // Update job with file info
        job.setTempFilePath(tempFile.toString());
        job.setFileExtension(".csv");
        job.setContentType("text/csv");
        job.setFileSizeBytes(java.nio.file.Files.size(tempFile));

        job.updateProgress(95, "Finalizing CSV export...");
        job.updateProgress(100, "Export completed");
    }

    /**
     * Format a single GPS point as a CSV row.
     * Format:
     * timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type,telemetry
     *
     * @param point GPS point entity
     * @return CSV row string with newline
     */
    private String formatCsvRow(GpsPointEntity point) {
        StringBuilder row = new StringBuilder();

        appendCsvValue(row, point.getTimestamp().toString());
        appendCsvValue(row, point.getLatitude());
        appendCsvValue(row, point.getLongitude());
        appendCsvValue(row, point.getAccuracy());
        appendCsvValue(row, point.getVelocity());
        appendCsvValue(row, point.getAltitude());
        appendCsvValue(row, point.getBattery());
        appendCsvValue(row, point.getDeviceId());
        appendCsvValue(row, point.getSourceType() != null ? point.getSourceType().name() : "");
        appendCsvValue(row, telemetryToJson(point.getTelemetry()));
        row.append("\n");

        return row.toString();
    }

    private String telemetryToJson(java.util.Map<String, Object> telemetry) {
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
