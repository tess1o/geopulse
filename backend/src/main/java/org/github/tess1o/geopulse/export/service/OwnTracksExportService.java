package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;

import java.io.IOException;

/**
 * Service responsible for generating OwnTracks format exports.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksExportService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GpsPointMapper gpsPointMapper;

    @Inject
    ExportDataCollectorService dataCollectorService;

    /**
     * Generates an OwnTracks export for the given export job.
     *
     * @param job the export job
     * @return the OwnTracks JSON as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateOwnTracksExport(ExportJob job) throws IOException {
        log.debug("Generating OwnTracks export for user {}", job.getUserId());

        var allPoints = dataCollectorService.collectGpsPoints(job);

        // Convert GPS points to OwnTracks format
        var ownTracksMessages = gpsPointMapper.toOwnTracksLocationMessages(allPoints);

        // Create JSON array for OwnTracks format
        String json = objectMapper.writeValueAsString(ownTracksMessages);

        log.debug("Generated OwnTracks export with {} GPS points", allPoints.size());
        return json.getBytes();
    }
}
