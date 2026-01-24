package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;

import java.io.IOException;

/**
 * Service responsible for generating OwnTracks format exports using streaming
 * approach.
 * Memory-efficient: processes GPS points in batches without loading all data
 * into memory.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksExportService {

    @Inject
    GpsPointMapper gpsPointMapper;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    StreamingExportService streamingExportService;

    @Inject
    ExportTempFileService tempFileService;

    /**
     * Generates an OwnTracks export for the given export job using STREAMING
     * approach.
     * Writes directly to a temporary file to avoid memory issues.
     *
     * @param job the export job
     * @throws IOException if an I/O error occurs
     */
    public void generateOwnTracksExport(ExportJob job) throws IOException {
        log.info("Starting streaming OwnTracks export for user {}", job.getUserId());

        job.updateProgress(5, "Initializing OwnTracks export...");

        // Create temp file
        java.nio.file.Path tempFile = tempFileService.createTempFile(job.getJobId(), ".json");

        try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(tempFile);
                java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(os)) {

            job.updateProgress(10, "Starting to stream GPS data...");

            // Stream as simple JSON array
            int batchSize = streamingExportService.getBatchSize();
            int totalWritten = streamingExportService.streamJsonArray(
                    bos,
                    // Fetch batch function
                    page -> gpsPointRepository.findByUserAndDateRange(
                            job.getUserId(),
                            job.getDateRange().getStartDate(),
                            job.getDateRange().getEndDate(),
                            page,
                            batchSize,
                            "timestamp",
                            "asc"),
                    // Convert GPS point to OwnTracks message DTO
                    gpsPoint -> gpsPointMapper.toOwnTracksLocationMessage(gpsPoint),
                    // Progress tracking
                    job,
                    -1, // Unknown total, will track by batches
                    10, // progress start: 10%
                    90, // progress end: 90%
                    "Exporting GPS points:");

            log.info("Completed streaming OwnTracks export: {} messages", totalWritten);
        }

        // Update job with file info
        job.setTempFilePath(tempFile.toString());
        job.setFileExtension(".json");
        job.setContentType("application/json");
        job.setFileSizeBytes(java.nio.file.Files.size(tempFile));

        job.updateProgress(95, "Finalizing OwnTracks export...");
        job.updateProgress(100, "Export completed");
    }
}
