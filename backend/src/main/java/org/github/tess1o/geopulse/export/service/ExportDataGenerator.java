package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;

import java.io.IOException;

/**
 * Main orchestrator for export operations using STREAMING approach.
 * Delegates to specialized services for format-specific exports and data
 * collection.
 *
 */
@ApplicationScoped
@Slf4j
public class ExportDataGenerator {

    @Inject
    GpxExportService gpxExportService;

    @Inject
    GeoJsonExportService geoJsonExportService;

    @Inject
    OwnTracksExportService ownTracksExportService;

    @Inject
    CsvExportService csvExportService;

    @Inject
    GeoPulseExportService geoPulseExportService;

    public void generateGeoPulseNativeExport(ExportJob job) throws IOException {
        geoPulseExportService.generateGeoPulseNativeExport(job);
    }

    /**
     * Generates an OwnTracks format export.
     */
    public void generateOwnTracksExport(ExportJob job) throws IOException {
        ownTracksExportService.generateOwnTracksExport(job);
    }

    /**
     * Generates a GeoJSON format export.
     */
    public void generateGeoJsonExport(ExportJob job) throws IOException {
        geoJsonExportService.generateGeoJsonExport(job);
    }

    /**
     * Generates a CSV format export.
     * Uses streaming approach to handle large datasets efficiently.
     *
     * @param job the export job
     * @throws IOException if an I/O error occurs
     */
    public void generateCsvExport(ExportJob job) throws IOException {
        csvExportService.generateCsvExport(job);
    }

    /**
     * Generates a GPX format export.
     */
    public void generateGpxExport(ExportJob job, boolean zipPerTrip, String zipGroupBy) throws IOException {
        gpxExportService.generateGpxExport(job, zipPerTrip, zipGroupBy);
    }

    /**
     * Generates a GPX file for a single trip.
     */
    public byte[] generateSingleTripGpx(java.util.UUID userId, Long tripId) throws IOException {
        return gpxExportService.generateSingleTripGpx(userId, tripId);
    }

    /**
     * Generates a GPX file for a single stay.
     */
    public byte[] generateSingleStayGpx(java.util.UUID userId, Long stayId) throws IOException {
        return gpxExportService.generateSingleStayGpx(userId, stayId);
    }
}
