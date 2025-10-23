package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.GoogleTimelineGpsPoint;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.GoogleTimelineRecord;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments.GoogleTimelineSemanticSegmentsRoot;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.util.GoogleTimelineParser;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.util.GoogleTimelineSemanticSegmentsParser;
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
 * Supports both legacy format (array of records) and new format (semantic segments).
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

        // Detect format by checking if JSON is an array or object
        JsonNode rootNode = objectMapper.readTree(jsonContent);

        if (rootNode.isArray()) {
            // Legacy format: array of records
            return validateLegacyFormat(jsonContent);
        } else if (rootNode.isObject() && rootNode.has("semanticSegments")) {
            // New format: semantic segments
            return validateSemanticSegmentsFormat(jsonContent);
        } else {
            throw new IllegalArgumentException("Unknown Google Timeline format. Expected either an array of records or an object with 'semanticSegments' field.");
        }
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        String jsonContent = new String(job.getZipData());

        // Detect format
        JsonNode rootNode = objectMapper.readTree(jsonContent);
        List<GoogleTimelineGpsPoint> gpsPoints;

        if (rootNode.isArray()) {
            // Legacy format
            log.info("Processing Google Timeline data in legacy format (array of records)");
            List<GoogleTimelineRecord> records = objectMapper.readValue(jsonContent,
                    new TypeReference<List<GoogleTimelineRecord>>() {
                    });
            gpsPoints = GoogleTimelineParser.extractGpsPoints(records, true);
        } else if (rootNode.isObject() && rootNode.has("semanticSegments")) {
            // New format
            log.info("Processing Google Timeline data in new format (semantic segments)");
            GoogleTimelineSemanticSegmentsRoot root = objectMapper.readValue(jsonContent,
                    GoogleTimelineSemanticSegmentsRoot.class);
            gpsPoints = GoogleTimelineSemanticSegmentsParser.extractGpsPoints(root, true);
        } else {
            throw new IllegalArgumentException("Unknown Google Timeline format");
        }

        log.info("Extracted {} GPS points from Google Timeline data", gpsPoints.size());

        // Convert to GpsPointEntity objects
        return convertToGpsEntities(gpsPoints, user, job);
    }

    private FormatValidationResult validateLegacyFormat(String jsonContent) throws IOException {
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

        log.info("Google Timeline (legacy) validation successful: {} total records ({} activity, {} visit, {} timeline_path, {} unknown), {} valid records",
                records.size(), activityCount, visitCount, timelinePathCount, unknownCount, validRecords);

        return new FormatValidationResult(records.size(), validRecords);
    }

    private FormatValidationResult validateSemanticSegmentsFormat(String jsonContent) throws IOException {
        GoogleTimelineSemanticSegmentsRoot root = objectMapper.readValue(jsonContent,
                GoogleTimelineSemanticSegmentsRoot.class);

        int totalSegments = 0;
        int validSegments = 0;
        int visitCount = 0;
        int activityCount = 0;
        int timelinePathCount = 0;
        int rawSignalCount = 0;

        if (root.getSemanticSegments() != null) {
            totalSegments = root.getSemanticSegments().size();

            for (var segment : root.getSemanticSegments()) {
                boolean isValid = false;

                if (segment.getVisit() != null && segment.getVisit().getTopCandidate() != null &&
                        segment.hasValidTimes()) {
                    visitCount++;
                    isValid = true;
                }

                if (segment.getActivity() != null && segment.hasValidTimes()) {
                    activityCount++;
                    isValid = true;
                }

                if (segment.getTimelinePath() != null && !segment.getTimelinePath().isEmpty() &&
                        segment.hasValidTimes()) {
                    timelinePathCount++;
                    isValid = true;
                }

                if (isValid) {
                    validSegments++;
                }
            }
        }

        if (root.getRawSignals() != null) {
            for (var signal : root.getRawSignals()) {
                if (signal.getPosition() != null && signal.getPosition().getTimestamp() != null) {
                    rawSignalCount++;
                }
            }
        }

        log.info("Google Timeline (semantic segments) validation successful: {} total segments ({} visit, {} activity, {} timeline_path), {} raw signals, {} valid segments",
                totalSegments, visitCount, activityCount, timelinePathCount, rawSignalCount, validSegments);

        return new FormatValidationResult(totalSegments + rawSignalCount, validSegments + rawSignalCount);
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