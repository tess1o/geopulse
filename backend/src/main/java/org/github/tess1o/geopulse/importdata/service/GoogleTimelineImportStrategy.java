package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.GoogleTimelineGpsPoint;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.GoogleTimelineRecord;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.util.GoogleTimelineParser;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Import strategy for Google Timeline JSON format.
 * Implements the same parsing logic as google_timeline_parser.py
 */
@ApplicationScoped
@Slf4j
public class GoogleTimelineImportStrategy extends BaseGpsImportStrategy {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Override
    public String getFormat() {
        return "google-timeline";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        String jsonContent = new String(job.getZipData()); // zipData contains JSON for Google Timeline

        // Parse as JSON array of Google Timeline records
        List<GoogleTimelineRecord> records = objectMapper.readValue(jsonContent,
                new TypeReference<List<GoogleTimelineRecord>>() {
                });

        if (records.isEmpty()) {
            throw new IllegalArgumentException("Google Timeline file contains no records");
        }

        // Analyze record types and validate data quality
        int activityCount = 0;
        int visitCount = 0;
        int timelinePathCount = 0;
        int unknownCount = 0;
        int validRecords = 0;

        for (GoogleTimelineRecord record : records) {
            switch (record.getRecordType()) {
                case ACTIVITY -> {
                    activityCount++;
                    if (isValidActivityRecord(record)) validRecords++;
                }
                case VISIT -> {
                    visitCount++;
                    if (isValidVisitRecord(record)) validRecords++;
                }
                case TIMELINE_PATH -> {
                    timelinePathCount++;
                    if (isValidTimelinePathRecord(record)) validRecords++;
                }
                case UNKNOWN -> unknownCount++;
            }
        }

        log.info("Google Timeline validation successful: {} total records ({} activity, {} visit, {} timeline_path, {} unknown), {} valid records",
                records.size(), activityCount, visitCount, timelinePathCount, unknownCount, validRecords);

        return new FormatValidationResult(records.size(), validRecords);
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        String jsonContent = new String(job.getZipData());
        List<GoogleTimelineRecord> records = objectMapper.readValue(jsonContent,
                new TypeReference<List<GoogleTimelineRecord>>() {
                });

        // Extract GPS points using the same logic as Python script
        List<GoogleTimelineGpsPoint> gpsPoints = GoogleTimelineParser.extractGpsPoints(records, true);

        // Convert to GpsPointEntity objects
        return convertToGpsEntities(gpsPoints, user, job);
    }

    private List<GpsPointEntity> convertToGpsEntities(List<GoogleTimelineGpsPoint> gpsPoints, UserEntity user, ImportJob job) {
        List<GpsPointEntity> gpsEntities = new ArrayList<>();
        int processedPoints = 0;

        for (GoogleTimelineGpsPoint point : gpsPoints) {
            // Skip points without valid coordinates or timestamp
            if (point.getTimestamp() == null ||
                    !isValidCoordinate(point.getLatitude()) ||
                    !isValidCoordinate(point.getLongitude())) {
                continue;
            }

            // Apply date range filter using base class method
            if (shouldSkipDueDateFilter(point.getTimestamp(), job)) {
                continue;
            }

            try {
                GpsPointEntity gpsEntity = new GpsPointEntity();
                gpsEntity.setUser(user);
                gpsEntity.setDeviceId("google-timeline-import");
                gpsEntity.setCoordinates(GeoUtils.createPoint(point.getLongitude(), point.getLatitude()));
                gpsEntity.setTimestamp(point.getTimestamp());
                gpsEntity.setSourceType(GpsSourceType.GOOGLE_TIMELINE);
                gpsEntity.setCreatedAt(Instant.now());

                // Set velocity if available (convert from m/s to km/h)
                if (point.getVelocityMs() != null) {
                    gpsEntity.setVelocity(point.getVelocityMs() * 3.6); // Convert m/s to km/h
                }

                // For Google Timeline, we don't have accuracy/battery/altitude in most cases
                // so we leave these as null

                gpsEntities.add(gpsEntity);

            } catch (Exception e) {
                log.warn("Failed to create GPS entity from point: {}", e.getMessage());
            }

            // Update progress using base class method
            processedPoints++;
            updateProgress(processedPoints, gpsPoints.size(), job, 10, 80);
        }

        return gpsEntities;
    }

    private boolean isValidActivityRecord(GoogleTimelineRecord record) {
        if (record.getActivity() == null || !record.hasValidTimes()) {
            return false;
        }

        double[] startCoords = GoogleTimelineParser.parseGeoString(record.getActivity().getStart());
        double[] endCoords = GoogleTimelineParser.parseGeoString(record.getActivity().getEnd());

        return startCoords != null || endCoords != null;
    }

    private boolean isValidVisitRecord(GoogleTimelineRecord record) {
        if (record.getVisit() == null || record.getVisit().getTopCandidate() == null || !record.hasValidTimes()) {
            return false;
        }

        double[] coords = GoogleTimelineParser.parseGeoString(record.getVisit().getTopCandidate().getPlaceLocation());
        return coords != null;
    }

    private boolean isValidTimelinePathRecord(GoogleTimelineRecord record) {
        if (record.getTimelinePath() == null || record.getTimelinePath().length == 0 || !record.hasValidTimes()) {
            return false;
        }

        // Check if at least one point in the path has valid coordinates
        for (var pathPoint : record.getTimelinePath()) {
            double[] coords = GoogleTimelineParser.parseGeoString(pathPoint.getPoint());
            if (coords != null) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidCoordinate(double coord) {
        return !Double.isNaN(coord) && !Double.isInfinite(coord);
    }

}