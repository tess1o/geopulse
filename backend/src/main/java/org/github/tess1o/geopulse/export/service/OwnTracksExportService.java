package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Service responsible for generating OwnTracks format exports using streaming
 * approach.
 * Memory-efficient: processes GPS points in batches without loading all data
 * into memory.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksExportService {
    private static final String OPTION_OWNTRACKS_FORMAT = "owntracksFormat";
    private static final String FORMAT_OCAT = "ocat";
    private static final String FORMAT_ARRAY = "array";

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
        String ownTracksFormat = resolveOwnTracksFormat(job);

        // Create temp file
        java.nio.file.Path tempFile = tempFileService.createTempFile(job.getJobId(), ".json");

        try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(tempFile);
                java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(os)) {

            job.updateProgress(10, "Starting to stream GPS data...");

            int batchSize = streamingExportService.getBatchSize();
            long totalRecords = gpsPointRepository.countByUserIdAndTimePeriod(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate());

            int totalWritten;
            if (FORMAT_ARRAY.equals(ownTracksFormat)) {
                totalWritten = streamingExportService.<GpsPointEntity, OwnTracksLocationMessage>streamJsonArray(
                        bos,
                        batchConsumer -> streamExportPoints(job, batchSize, batchConsumer),
                        gpsPoint -> gpsPointMapper.toOwnTracksLocationMessage(gpsPoint),
                        job,
                        toProgressTotal(totalRecords),
                        10,
                        90,
                        "Exporting GPS points:");
            } else {
                totalWritten = streamingExportService.<GpsPointEntity>streamJsonObjectWithArray(
                        bos,
                        (gen, mapper) -> gen.writeNumberField("count", totalRecords),
                        "locations",
                        batchConsumer -> streamExportPoints(job, batchSize, batchConsumer),
                        (gen, gpsPoint, mapper) ->
                                gen.writeObject(gpsPointMapper.toOwnTracksLocationMessage(gpsPoint)),
                        job,
                        toProgressTotal(totalRecords),
                        10,
                        90,
                        "Exporting GPS points:");
            }

            log.info("Completed streaming OwnTracks export: {} messages, format={}",
                    totalWritten, ownTracksFormat);
        }

        // Update job with file info
        job.setTempFilePath(tempFile.toString());
        job.setFileExtension(".json");
        job.setContentType("application/json");
        job.setFileSizeBytes(java.nio.file.Files.size(tempFile));

        job.updateProgress(95, "Finalizing OwnTracks export...");
        job.updateProgress(100, "Export completed");
    }

    private void streamExportPoints(ExportJob job, int batchSize, Consumer<List<GpsPointEntity>> batchConsumer) {
        gpsPointRepository.streamByUserAndDateRangeForExport(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate(),
                batchSize,
                batchConsumer);
    }

    private String resolveOwnTracksFormat(ExportJob job) {
        if (job.getOptions() == null || !job.getOptions().containsKey(OPTION_OWNTRACKS_FORMAT)) {
            return FORMAT_OCAT;
        }

        Object requested = job.getOptions().get(OPTION_OWNTRACKS_FORMAT);
        if (requested == null) {
            return FORMAT_OCAT;
        }

        String normalized = requested.toString().trim().toLowerCase(Locale.ENGLISH);
        if (FORMAT_OCAT.equals(normalized) || FORMAT_ARRAY.equals(normalized)) {
            return normalized;
        }

        throw new IllegalArgumentException(
                "Unsupported OwnTracks export format: " + requested + ". Supported values: ocat, array");
    }

    private int toProgressTotal(long totalRecords) {
        return totalRecords > Integer.MAX_VALUE ? -1 : (int) totalRecords;
    }
}
