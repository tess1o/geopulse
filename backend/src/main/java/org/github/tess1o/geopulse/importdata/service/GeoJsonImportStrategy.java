package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.geojson.model.*;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Import strategy for GeoJSON format.
 * Supports both Point and LineString geometries.
 */
@ApplicationScoped
@Slf4j
public class GeoJsonImportStrategy extends BaseGpsImportStrategy {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Override
    public String getFormat() {
        return "geojson";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        String jsonContent = new String(job.getZipData()); // zipData contains GeoJSON for this format

        // Parse GeoJSON
        GeoJsonFeatureCollection featureCollection = objectMapper.readValue(jsonContent, GeoJsonFeatureCollection.class);

        if (featureCollection.getFeatureCount() == 0) {
            throw new IllegalArgumentException("GeoJSON file contains no features");
        }

        // Count total points from all features (Point features = 1 point, LineString features = multiple points)
        int totalPoints = 0;
        int validPoints = 0;

        for (GeoJsonFeature feature : featureCollection.getFeatures()) {
            if (!feature.hasValidGeometry()) {
                continue;
            }

            GeoJsonGeometry geometry = feature.getGeometry();
            if (geometry instanceof GeoJsonPoint) {
                totalPoints++;
                if (((GeoJsonPoint) geometry).hasValidCoordinates()) {
                    validPoints++;
                }
            } else if (geometry instanceof GeoJsonLineString) {
                GeoJsonLineString lineString = (GeoJsonLineString) geometry;
                List<GeoJsonPoint> points = lineString.getPoints();
                totalPoints += lineString.getCoordinates().size();
                validPoints += points.size();
            }
        }

        if (validPoints == 0) {
            throw new IllegalArgumentException("GeoJSON file contains no valid GPS points");
        }

        log.info("GeoJSON validation successful: {} features, {} total points, {} valid GPS points",
                featureCollection.getFeatureCount(), totalPoints, validPoints);

        return new FormatValidationResult(totalPoints, validPoints);
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        String jsonContent = new String(job.getZipData());
        GeoJsonFeatureCollection featureCollection = objectMapper.readValue(jsonContent, GeoJsonFeatureCollection.class);

        return convertFeaturesToGpsPoints(featureCollection, user, job);
    }

    private List<GpsPointEntity> convertFeaturesToGpsPoints(GeoJsonFeatureCollection featureCollection,
                                                             UserEntity user, ImportJob job) {
        List<GpsPointEntity> gpsPoints = new ArrayList<>();
        int processedFeatures = 0;
        int totalFeatures = featureCollection.getFeatureCount();

        for (GeoJsonFeature feature : featureCollection.getFeatures()) {
            if (!feature.hasValidGeometry()) {
                processedFeatures++;
                continue;
            }

            GeoJsonGeometry geometry = feature.getGeometry();
            GeoJsonProperties properties = feature.getProperties();

            if (geometry instanceof GeoJsonPoint) {
                // Convert single Point feature to GPS point
                GeoJsonPoint point = (GeoJsonPoint) geometry;
                GpsPointEntity gpsPoint = convertPointToGpsPoint(point, properties, user, job);
                if (gpsPoint != null) {
                    gpsPoints.add(gpsPoint);
                }
            } else if (geometry instanceof GeoJsonLineString) {
                // Convert LineString feature to multiple GPS points
                GeoJsonLineString lineString = (GeoJsonLineString) geometry;
                for (GeoJsonPoint point : lineString.getPoints()) {
                    GpsPointEntity gpsPoint = convertPointToGpsPoint(point, properties, user, job);
                    if (gpsPoint != null) {
                        gpsPoints.add(gpsPoint);
                    }
                }
            }

            processedFeatures++;
            updateProgress(processedFeatures, totalFeatures, job, 10, 80);
        }

        return gpsPoints;
    }

    private GpsPointEntity convertPointToGpsPoint(GeoJsonPoint point, GeoJsonProperties properties,
                                                   UserEntity user, ImportJob job) {
        if (!point.hasValidCoordinates()) {
            return null;
        }

        // Parse timestamp from properties
        Instant timestamp = parseTimestamp(properties);
        if (timestamp == null) {
            log.warn("Skipping GPS point without valid timestamp");
            return null;
        }

        // Apply date range filter using base class method
        if (shouldSkipDueDateFilter(timestamp, job)) {
            return null;
        }

        try {
            GpsPointEntity gpsEntity = new GpsPointEntity();
            gpsEntity.setUser(user);
            gpsEntity.setDeviceId(properties != null && properties.getDeviceId() != null
                    ? properties.getDeviceId()
                    : "geojson-import");
            gpsEntity.setCoordinates(GeoUtils.createPoint(point.getLongitude(), point.getLatitude()));
            gpsEntity.setTimestamp(timestamp);
            gpsEntity.setSourceType(GpsSourceType.GEOJSON);
            gpsEntity.setCreatedAt(Instant.now());

            // Set altitude if available (prefer geometry altitude, fallback to properties)
            Double altitude = point.getAltitude();
            if (altitude == null && properties != null) {
                altitude = properties.getAltitude();
            }
            if (altitude != null) {
                gpsEntity.setAltitude(altitude);
            }

            // Set velocity if available
            if (properties != null && properties.getVelocity() != null) {
                gpsEntity.setVelocity(properties.getVelocity());
            }

            // Set accuracy if available
            if (properties != null && properties.getAccuracy() != null) {
                gpsEntity.setAccuracy(properties.getAccuracy());
            }

            // Set battery if available
            if (properties != null && properties.getBattery() != null) {
                gpsEntity.setBattery(properties.getBattery().doubleValue());
            }

            return gpsEntity;

        } catch (Exception e) {
            log.warn("Failed to create GPS entity from GeoJSON point: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse timestamp from GeoJSON properties.
     * Supports ISO-8601 format and Unix timestamps (seconds or milliseconds).
     */
    private Instant parseTimestamp(GeoJsonProperties properties) {
        if (properties == null || properties.getTimestamp() == null) {
            return null;
        }

        String timestampStr = properties.getTimestamp();

        try {
            // Try parsing as ISO-8601 timestamp
            return Instant.parse(timestampStr);
        } catch (DateTimeParseException e) {
            // Try parsing as Unix timestamp (seconds or milliseconds)
            try {
                long timestamp = Long.parseLong(timestampStr);
                // If timestamp is in milliseconds (> year 2001 in seconds), convert to seconds
                if (timestamp > 1_000_000_000_000L) {
                    return Instant.ofEpochMilli(timestamp);
                } else {
                    return Instant.ofEpochSecond(timestamp);
                }
            } catch (NumberFormatException ex) {
                log.warn("Unable to parse timestamp: {}", timestampStr);
                return null;
            }
        }
    }
}
