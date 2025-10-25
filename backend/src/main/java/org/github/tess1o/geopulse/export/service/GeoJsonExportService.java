package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeature;
import org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeatureCollection;
import org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonPoint;
import org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonProperties;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for generating GeoJSON format exports.
 */
@ApplicationScoped
@Slf4j
public class GeoJsonExportService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ExportDataCollectorService dataCollectorService;

    /**
     * Generates a GeoJSON export for the given export job.
     *
     * @param job the export job
     * @return the GeoJSON as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateGeoJsonExport(ExportJob job) throws IOException {
        log.debug("Generating GeoJSON export for user {}", job.getUserId());

        var allPoints = dataCollectorService.collectGpsPoints(job);
        var allFeatures = new ArrayList<GeoJsonFeature>();

        // Convert GPS points to GeoJSON features
        for (var gpsPoint : allPoints) {
            allFeatures.add(convertGpsPointToGeoJsonFeature(gpsPoint));
        }

        // Create GeoJSON FeatureCollection
        var featureCollection = GeoJsonFeatureCollection.builder()
                .type("FeatureCollection")
                .features(allFeatures)
                .build();

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(featureCollection);

        log.debug("Generated GeoJSON export with {} GPS points", allFeatures.size());
        return json.getBytes();
    }

    /**
     * Converts a GPS point entity to a GeoJSON feature.
     *
     * @param gpsPoint the GPS point entity
     * @return the GeoJSON feature
     */
    private GeoJsonFeature convertGpsPointToGeoJsonFeature(GpsPointEntity gpsPoint) {
        // Extract coordinates from PostGIS Point geometry
        double longitude = gpsPoint.getCoordinates().getX();
        double latitude = gpsPoint.getCoordinates().getY();

        // Create Point geometry with optional altitude
        GeoJsonPoint geometry;
        if (gpsPoint.getAltitude() != null) {
            geometry = new GeoJsonPoint(longitude, latitude, gpsPoint.getAltitude());
        } else {
            geometry = new GeoJsonPoint(longitude, latitude);
        }

        // Create properties with GPS metadata
        var properties = GeoJsonProperties.builder()
                .timestamp(gpsPoint.getTimestamp().toString())
                .altitude(gpsPoint.getAltitude())
                .velocity(gpsPoint.getVelocity())
                .accuracy(gpsPoint.getAccuracy())
                .battery(gpsPoint.getBattery() != null ? gpsPoint.getBattery().intValue() : null)
                .deviceId(gpsPoint.getDeviceId())
                .sourceType(gpsPoint.getSourceType() != null ? gpsPoint.getSourceType().name() : null)
                .build();

        // Create and return Feature
        return GeoJsonFeature.builder()
                .type("Feature")
                .geometry(geometry)
                .properties(properties)
                .id(gpsPoint.getId() != null ? gpsPoint.getId().toString() : null)
                .build();
    }
}
