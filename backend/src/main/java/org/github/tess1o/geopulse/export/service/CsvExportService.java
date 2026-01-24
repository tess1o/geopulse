package org.github.tess1o.geopulse.export.service;

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
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service responsible for generating CSV format exports using streaming
 * approach.
 * Memory-efficient: processes GPS points in batches without loading all data
 * into memory.
 */
@ApplicationScoped
@Slf4j
public class CsvExportService {

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
            writer.write("timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type\n");

            job.updateProgress(10, "Starting to stream GPS data...");

            int page = 0;
            int totalWritten = 0;

            while (true) {
                // Fetch batch of GPS points
                List<GpsPointEntity> batch = gpsPointRepository.findByUserAndDateRange(
                        job.getUserId(),
                        job.getDateRange().getStartDate(),
                        job.getDateRange().getEndDate(),
                        page,
                        batchSize,
                        "timestamp",
                        "asc");

                if (batch.isEmpty()) {
                    break;
                }

                // Write each GPS point as CSV row
                for (GpsPointEntity point : batch) {
                    writer.write(formatCsvRow(point));
                    totalWritten++;
                }

                writer.flush(); // Flush after each batch
                page++;

                // Update progress (10% to 90%)
                int progress = 10 + (int) (80.0 * totalWritten / Math.max(totalWritten + batchSize, 1));
                progress = Math.min(progress, 90);
                job.updateProgress(progress, String.format("Exporting GPS points: %d records", totalWritten));

                // Log progress periodically
                if (page % 10 == 0) {
                    log.debug("Streamed {} records in {} batches", totalWritten, page);
                }
            }

            log.info("Completed streaming CSV export: {} records in {} batches", totalWritten, page);
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
     * timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type
     *
     * @param point GPS point entity
     * @return CSV row string with newline
     */
    private String formatCsvRow(GpsPointEntity point) {
        StringBuilder row = new StringBuilder();

        // Required fields
        row.append(point.getTimestamp().toString()).append(",");
        row.append(point.getLatitude()).append(",");
        row.append(point.getLongitude()).append(",");

        // Optional fields
        row.append(point.getAccuracy() != null ? point.getAccuracy() : "").append(",");
        row.append(point.getVelocity() != null ? point.getVelocity() : "").append(",");
        row.append(point.getAltitude() != null ? point.getAltitude() : "").append(",");
        row.append(point.getBattery() != null ? point.getBattery() : "").append(",");
        row.append(point.getDeviceId() != null ? point.getDeviceId() : "").append(",");
        row.append(point.getSourceType() != null ? point.getSourceType().name() : "").append("\n");

        return row.toString();
    }
}
