package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Service responsible for generating OwnTracks format exports using streaming approach.
 * Memory-efficient: processes GPS points in batches without loading all data into memory.
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

    /**
     * Generates an OwnTracks export for the given export job using STREAMING approach.
     * Memory usage: O(batch_size) instead of O(total_records).
     *
     * @param job the export job
     * @return the OwnTracks JSON as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateOwnTracksExport(ExportJob job) throws IOException {
        log.info("Starting streaming OwnTracks export for user {}", job.getUserId());

        job.updateProgress(5, "Initializing OwnTracks export...");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        job.updateProgress(10, "Starting to stream GPS data...");

        // Stream as simple JSON array
        int batchSize = streamingExportService.getBatchSize();
        int totalWritten = streamingExportService.streamJsonArray(
            baos,
            // Fetch batch function
            page -> gpsPointRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate(),
                page,
                batchSize,
                "timestamp",
                "asc"
            ),
            // Convert GPS point to OwnTracks message DTO
            gpsPoint -> gpsPointMapper.toOwnTracksLocationMessage(gpsPoint),
            // Progress tracking
            job,
            -1, // Unknown total, will track by batches
            10,  // progress start: 10%
            90,  // progress end: 90%
            "Exporting GPS points:"
        );

        byte[] result = baos.toByteArray();

        job.updateProgress(95, "Finalizing OwnTracks export...");
        log.info("Completed streaming OwnTracks export: {} messages, {} bytes", totalWritten, result.length);
        job.updateProgress(100, "Export completed");

        return result;
    }
}
