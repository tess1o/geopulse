package org.github.tess1o.geopulse.gps.integrations.googletimeline;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.GoogleTimelineGpsPoint;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming parser for Google Timeline JSON files.
 * Processes both legacy format (array of records) and new format (semantic segments)
 * using Jackson's streaming API for memory-efficient parsing.
 *
 * This parser:
 * - Parses JSON incrementally without loading entire file into memory
 * - Extracts only the fields we actually need for GPS points
 * - Skips unused fields like userLocationProfile, frequentPlaces, etc.
 * - Uses callback-based processing to avoid accumulating all entities in memory
 *
 * Memory usage: Constant O(1) - only current segment/record in memory at a time.
 */
@Slf4j
public class StreamingGoogleTimelineParser {

    private final InputStream inputStream;
    private final ObjectMapper objectMapper;
    private final JsonFactory jsonFactory;

    /**
     * Interval in minutes between interpolated GPS points for visit records
     */
    public static final int VISIT_INTERPOLATION_INTERVAL_MINUTES = 5;

    public StreamingGoogleTimelineParser(InputStream inputStream, ObjectMapper objectMapper) {
        this.inputStream = inputStream;
        this.objectMapper = objectMapper;
        this.jsonFactory = new JsonFactory();
    }

    /**
     * Callback interface for processing GPS points as they are parsed
     */
    @FunctionalInterface
    public interface GpsPointCallback {
        void onGpsPoint(GoogleTimelineGpsPoint gpsPoint, ParsingStats currentStats);
    }

    /**
     * Statistics collected during parsing
     */
    public static class ParsingStats {
        public int totalRecords = 0;          // For legacy format: number of records
        public int totalSemanticSegments = 0;  // For new format: number of semantic segments
        public int totalRawSignals = 0;        // For new format: number of raw signals
        public int visitCount = 0;
        public int activityCount = 0;
        public int timelinePathCount = 0;
        public int totalGpsPoints = 0;         // Total GPS points extracted
        public Instant firstTimestamp = null;
        public Instant lastTimestamp = null;
        public FormatType formatType = FormatType.UNKNOWN;

        public void updateTimestamp(Instant timestamp) {
            if (timestamp == null) return;

            if (firstTimestamp == null || timestamp.isBefore(firstTimestamp)) {
                firstTimestamp = timestamp;
            }
            if (lastTimestamp == null || timestamp.isAfter(lastTimestamp)) {
                lastTimestamp = timestamp;
            }
        }
    }

    public enum FormatType {
        LEGACY_ARRAY,
        SEMANTIC_SEGMENTS,
        UNKNOWN
    }

    /**
     * Parse Google Timeline JSON and invoke callback for each GPS point.
     * Automatically detects format (legacy array vs semantic segments).
     *
     * @param callback function to call for each parsed GPS point
     * @return parsing statistics
     * @throws IOException if parsing fails
     */
    public ParsingStats parseGpsPoints(GpsPointCallback callback) throws IOException {
        ParsingStats stats = new ParsingStats();

        try (JsonParser parser = jsonFactory.createParser(inputStream)) {
            // Detect format by checking first token
            JsonToken token = parser.nextToken();

            if (token == JsonToken.START_ARRAY) {
                // Legacy format: array of records
                stats.formatType = FormatType.LEGACY_ARRAY;
                log.info("Detected Google Timeline legacy format (array of records)");
                parseLegacyFormat(parser, callback, stats);
            } else if (token == JsonToken.START_OBJECT) {
                // New format: object with semanticSegments
                stats.formatType = FormatType.SEMANTIC_SEGMENTS;
                log.info("Detected Google Timeline semantic segments format");
                parseSemanticSegmentsFormat(parser, callback, stats);
            } else {
                throw new IllegalArgumentException("Unknown Google Timeline format - expected array or object");
            }
        }

        log.info("Google Timeline parsing complete: format={}, records={}, segments={}, rawSignals={}, gpsPoints={}, dateRange={} to {}",
                stats.formatType, stats.totalRecords, stats.totalSemanticSegments,
                stats.totalRawSignals, stats.totalGpsPoints,
                stats.firstTimestamp, stats.lastTimestamp);

        return stats;
    }

    /**
     * Parse legacy format: array of records
     */
    private void parseLegacyFormat(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        // We're already positioned at START_ARRAY
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            // Parse one record
            parseLegacyRecord(parser, callback, stats);
            stats.totalRecords++;
        }
    }

    /**
     * Parse one record from legacy format
     */
    private void parseLegacyRecord(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        Instant startTime = null;
        Instant endTime = null;
        String recordType = null;

        // Track which section we're in
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();

            if (fieldName == null) continue;

            switch (fieldName) {
                case "startTime" -> {
                    parser.nextToken();
                    String timeStr = parser.getValueAsString();
                    startTime = parseInstant(timeStr);
                }
                case "endTime" -> {
                    parser.nextToken();
                    String timeStr = parser.getValueAsString();
                    endTime = parseInstant(timeStr);
                }
                case "activity" -> {
                    recordType = "activity";
                    parseLegacyActivity(parser, startTime, endTime, callback, stats);
                    stats.activityCount++;
                }
                case "visit" -> {
                    recordType = "visit";
                    parseLegacyVisit(parser, startTime, endTime, callback, stats);
                    stats.visitCount++;
                }
                case "timelinePath" -> {
                    recordType = "timelinePath";
                    parseLegacyTimelinePath(parser, startTime, callback, stats);
                    stats.timelinePathCount++;
                }
                default -> parser.skipChildren(); // Skip unknown fields
            }
        }
    }

    private void parseLegacyActivity(JsonParser parser, Instant startTime, Instant endTime,
                                     GpsPointCallback callback, ParsingStats stats) throws IOException {
        String start = null;
        String end = null;
        Double distanceMeters = null;
        String activityType = "unknown";
        double confidence = 0.0;

        parser.nextToken(); // START_OBJECT

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "start" -> {
                    parser.nextToken();
                    start = parser.getValueAsString();
                }
                case "end" -> {
                    parser.nextToken();
                    end = parser.getValueAsString();
                }
                case "distanceMeters" -> {
                    parser.nextToken();
                    distanceMeters = parser.getValueAsString() == null ? null : Double.valueOf(parser.getValueAsString());
                }
                case "topCandidate" -> {
                    parser.nextToken(); // START_OBJECT
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String tcField = parser.getCurrentName();
                        if ("type".equals(tcField)) {
                            parser.nextToken();
                            activityType = parser.getValueAsString();
                        } else if ("probability".equals(tcField)) {
                            parser.nextToken();
                            confidence = parser.getValueAsString() == null? null : Double.valueOf(parser.getValueAsString());
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
                default -> parser.skipChildren();
            }
        }

        // Calculate velocity
        Double velocity = null;
        if (startTime != null && endTime != null && distanceMeters != null && distanceMeters > 0) {
            long durationSeconds = ChronoUnit.SECONDS.between(startTime, endTime);
            if (durationSeconds > 0) {
                velocity = distanceMeters / durationSeconds; // m/s
            }
        }

        // Emit start point
        double[] startCoords = parseGeoString(start);
        if (startCoords != null && startTime != null) {
            GoogleTimelineGpsPoint point = GoogleTimelineGpsPoint.builder()
                    .timestamp(startTime)
                    .latitude(startCoords[0])
                    .longitude(startCoords[1])
                    .recordType("activity_start")
                    .activityType(activityType)
                    .confidence(confidence)
                    .velocityMs(velocity)
                    .build();

            stats.totalGpsPoints++;
            stats.updateTimestamp(startTime);
            callback.onGpsPoint(point, stats);
        }

        // Emit end point
        double[] endCoords = parseGeoString(end);
        if (endCoords != null && endTime != null) {
            GoogleTimelineGpsPoint point = GoogleTimelineGpsPoint.builder()
                    .timestamp(endTime)
                    .latitude(endCoords[0])
                    .longitude(endCoords[1])
                    .recordType("activity_end")
                    .activityType(activityType)
                    .confidence(confidence)
                    .velocityMs(velocity)
                    .build();

            stats.totalGpsPoints++;
            stats.updateTimestamp(endTime);
            callback.onGpsPoint(point, stats);
        }
    }

    private void parseLegacyVisit(JsonParser parser, Instant startTime, Instant endTime,
                                  GpsPointCallback callback, ParsingStats stats) throws IOException {
        String placeLocation = null;
        String semanticType = "unknown";
        double confidence = 0.0;

        parser.nextToken(); // START_OBJECT

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "probability" -> {
                    parser.nextToken();
                    confidence = parser.getValueAsString() == null? null : Double.valueOf(parser.getValueAsString());
                }
                case "topCandidate" -> {
                    parser.nextToken(); // START_OBJECT
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String tcField = parser.getCurrentName();
                        if ("placeLocation".equals(tcField)) {
                            parser.nextToken();
                            placeLocation = parser.getValueAsString();
                        } else if ("semanticType".equals(tcField)) {
                            parser.nextToken();
                            semanticType = parser.getValueAsString();
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
                default -> parser.skipChildren();
            }
        }

        // Emit interpolated visit points
        double[] coords = parseGeoString(placeLocation);
        if (coords != null && startTime != null && endTime != null) {
            List<GoogleTimelineGpsPoint> visitPoints = interpolateVisitPoints(
                    startTime, endTime, coords[0], coords[1], semanticType, confidence);

            for (GoogleTimelineGpsPoint point : visitPoints) {
                stats.totalGpsPoints++;
                stats.updateTimestamp(point.getTimestamp());
                callback.onGpsPoint(point, stats);
            }
        }
    }

    private void parseLegacyTimelinePath(JsonParser parser, Instant startTime,
                                        GpsPointCallback callback, ParsingStats stats) throws IOException {
        parser.nextToken(); // START_ARRAY

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            // Parse one path point
            String point = null;
            int offsetMinutes = 0;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                if (fieldName == null) continue;

                if ("point".equals(fieldName)) {
                    parser.nextToken();
                    point = parser.getValueAsString();
                } else if ("durationMinutesOffsetFromStartTime".equals(fieldName)) {
                    parser.nextToken();
                    offsetMinutes = parser.getValueAsString() == null? 0 : Integer.parseInt(parser.getValueAsString());
                } else {
                    parser.skipChildren();
                }
            }

            // Emit timeline point
            double[] coords = parseGeoString(point);
            if (coords != null && startTime != null) {
                Instant pointTime = startTime.plus(offsetMinutes, ChronoUnit.MINUTES);

                GoogleTimelineGpsPoint gpsPoint = GoogleTimelineGpsPoint.builder()
                        .timestamp(pointTime)
                        .latitude(coords[0])
                        .longitude(coords[1])
                        .recordType("timeline_point")
                        .activityType("movement")
                        .confidence(1.0)
                        .build();

                stats.totalGpsPoints++;
                stats.updateTimestamp(pointTime);
                callback.onGpsPoint(gpsPoint, stats);
            }
        }
    }

    /**
     * Parse semantic segments format: object with semanticSegments and rawSignals
     */
    private void parseSemanticSegmentsFormat(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        // We're already positioned at START_OBJECT
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();

            if (fieldName == null) continue;

            switch (fieldName) {
                case "semanticSegments" -> parseSemanticSegmentsArray(parser, callback, stats);
                case "rawSignals" -> parseRawSignalsArray(parser, callback, stats);
                case "userLocationProfile" -> parser.skipChildren(); // Skip - we don't use this
                default -> parser.skipChildren();
            }
        }
    }

    private void parseSemanticSegmentsArray(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        parser.nextToken(); // START_ARRAY

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            parseSemanticSegment(parser, callback, stats);
            stats.totalSemanticSegments++;
        }
    }

    private void parseSemanticSegment(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        Instant startTime = null;
        Instant endTime = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "startTime" -> {
                    parser.nextToken();
                    startTime = parseInstant(parser.getValueAsString());
                }
                case "endTime" -> {
                    parser.nextToken();
                    endTime = parseInstant(parser.getValueAsString());
                }
                case "visit" -> {
                    parseSemanticVisit(parser, startTime, endTime, callback, stats);
                    stats.visitCount++;
                }
                case "activity" -> {
                    parseSemanticActivity(parser, startTime, endTime, callback, stats);
                    stats.activityCount++;
                }
                case "timelinePath" -> {
                    parseSemanticTimelinePath(parser, callback, stats);
                    stats.timelinePathCount++;
                }
                default -> parser.skipChildren();
            }
        }
    }

    private void parseSemanticVisit(JsonParser parser, Instant startTime, Instant endTime,
                                    GpsPointCallback callback, ParsingStats stats) throws IOException {
        String latLng = null;
        String semanticType = "unknown";
        double confidence = 0.0;

        parser.nextToken(); // START_OBJECT

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "probability" -> {
                    parser.nextToken();
                    confidence = parser.getValueAsString() == null? null : Double.valueOf(parser.getValueAsString());
                }
                case "topCandidate" -> {
                    parser.nextToken(); // START_OBJECT
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String tcField = parser.getCurrentName();
                        if ("placeLocation".equals(tcField)) {
                            parser.nextToken(); // START_OBJECT
                            while (parser.nextToken() != JsonToken.END_OBJECT) {
                                if ("latLng".equals(parser.getCurrentName())) {
                                    parser.nextToken();
                                    latLng = parser.getValueAsString();
                                } else {
                                    parser.skipChildren();
                                }
                            }
                        } else if ("semanticType".equals(tcField)) {
                            parser.nextToken();
                            semanticType = parser.getValueAsString();
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
                default -> parser.skipChildren();
            }
        }

        // Emit interpolated visit points
        double[] coords = parseGeoString(latLng);
        if (coords != null && startTime != null && endTime != null) {
            List<GoogleTimelineGpsPoint> visitPoints = interpolateVisitPoints(
                    startTime, endTime, coords[0], coords[1], semanticType, confidence);

            for (GoogleTimelineGpsPoint point : visitPoints) {
                stats.totalGpsPoints++;
                stats.updateTimestamp(point.getTimestamp());
                callback.onGpsPoint(point, stats);
            }
        }
    }

    private void parseSemanticActivity(JsonParser parser, Instant startTime, Instant endTime,
                                       GpsPointCallback callback, ParsingStats stats) throws IOException {
        String startLatLng = null;
        String endLatLng = null;
        Double distanceMeters = null;
        String activityType = "unknown";
        double confidence = 0.0;

        parser.nextToken(); // START_OBJECT

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "start" -> {
                    parser.nextToken(); // START_OBJECT
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        if ("latLng".equals(parser.getCurrentName())) {
                            parser.nextToken();
                            startLatLng = parser.getValueAsString();
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
                case "end" -> {
                    parser.nextToken(); // START_OBJECT
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        if ("latLng".equals(parser.getCurrentName())) {
                            parser.nextToken();
                            endLatLng = parser.getValueAsString();
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
                case "distanceMeters" -> {
                    parser.nextToken();
                    distanceMeters = parser.getValueAsString() == null ? null : Double.valueOf(parser.getDoubleValue());
                }
                case "topCandidate" -> {
                    parser.nextToken(); // START_OBJECT
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String tcField = parser.getCurrentName();
                        if ("type".equals(tcField)) {
                            parser.nextToken();
                            activityType = parser.getValueAsString();
                        } else if ("probability".equals(tcField)) {
                            parser.nextToken();
                            confidence = parser.getValueAsString() == null? null : Double.valueOf(parser.getValueAsString());
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
                default -> parser.skipChildren();
            }
        }

        // Calculate velocity
        Double velocity = null;
        if (startTime != null && endTime != null && distanceMeters != null && distanceMeters > 0) {
            long durationSeconds = ChronoUnit.SECONDS.between(startTime, endTime);
            if (durationSeconds > 0) {
                velocity = distanceMeters / durationSeconds; // m/s
            }
        }

        // Emit start point
        double[] startCoords = parseGeoString(startLatLng);
        if (startCoords != null && startTime != null) {
            GoogleTimelineGpsPoint point = GoogleTimelineGpsPoint.builder()
                    .timestamp(startTime)
                    .latitude(startCoords[0])
                    .longitude(startCoords[1])
                    .recordType("activity_start")
                    .activityType(activityType)
                    .confidence(confidence)
                    .velocityMs(velocity)
                    .build();

            stats.totalGpsPoints++;
            stats.updateTimestamp(startTime);
            callback.onGpsPoint(point, stats);
        }

        // Emit end point
        double[] endCoords = parseGeoString(endLatLng);
        if (endCoords != null && endTime != null) {
            GoogleTimelineGpsPoint point = GoogleTimelineGpsPoint.builder()
                    .timestamp(endTime)
                    .latitude(endCoords[0])
                    .longitude(endCoords[1])
                    .recordType("activity_end")
                    .activityType(activityType)
                    .confidence(confidence)
                    .velocityMs(velocity)
                    .build();

            stats.totalGpsPoints++;
            stats.updateTimestamp(endTime);
            callback.onGpsPoint(point, stats);
        }
    }

    private void parseSemanticTimelinePath(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        parser.nextToken(); // START_ARRAY

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            String point = null;
            Instant time = null;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                if (fieldName == null) continue;

                if ("point".equals(fieldName)) {
                    parser.nextToken();
                    point = parser.getValueAsString();
                } else if ("time".equals(fieldName)) {
                    parser.nextToken();
                    time = parseInstant(parser.getValueAsString());
                } else {
                    parser.skipChildren();
                }
            }

            // Emit timeline point
            double[] coords = parseGeoString(point);
            if (coords != null && time != null) {
                GoogleTimelineGpsPoint gpsPoint = GoogleTimelineGpsPoint.builder()
                        .timestamp(time)
                        .latitude(coords[0])
                        .longitude(coords[1])
                        .recordType("timeline_point")
                        .activityType("movement")
                        .confidence(1.0)
                        .build();

                stats.totalGpsPoints++;
                stats.updateTimestamp(time);
                callback.onGpsPoint(gpsPoint, stats);
            }
        }
    }

    private void parseRawSignalsArray(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        parser.nextToken(); // START_ARRAY

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            parseRawSignal(parser, callback, stats);
            stats.totalRawSignals++;
        }
    }

    private void parseRawSignal(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            if ("position".equals(fieldName)) {
                parseRawPosition(parser, callback, stats);
            } else {
                parser.skipChildren();
            }
        }
    }

    private void parseRawPosition(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        String latLng = null;
        Instant timestamp = null;
        String source = "unknown";
        Double speedMs = null;

        parser.nextToken(); // START_OBJECT

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "latLng" -> {
                    parser.nextToken();
                    latLng = parser.getValueAsString();
                }
                case "timestamp" -> {
                    parser.nextToken();
                    timestamp = parseInstant(parser.getValueAsString());
                }
                case "source" -> {
                    parser.nextToken();
                    source = parser.getValueAsString();
                }
                case "speedMetersPerSecond" -> {
                    parser.nextToken();
                    speedMs = parser.getDoubleValue();
                }
                default -> parser.skipChildren();
            }
        }

        // Emit raw position point
        double[] coords = parseGeoString(latLng);
        if (coords != null && timestamp != null) {
            GoogleTimelineGpsPoint point = GoogleTimelineGpsPoint.builder()
                    .timestamp(timestamp)
                    .latitude(coords[0])
                    .longitude(coords[1])
                    .recordType("raw_position")
                    .activityType(source)
                    .confidence(1.0)
                    .velocityMs(speedMs)
                    .build();

            stats.totalGpsPoints++;
            stats.updateTimestamp(timestamp);
            callback.onGpsPoint(point, stats);
        }
    }

    /**
     * Parse geo string from both old and new formats to coordinates.
     * Old format: "geo:lat,lng"
     * New format: "lat°, lng°"
     */
    private static double[] parseGeoString(String geoStr) {
        if (geoStr == null || geoStr.trim().isEmpty()) {
            return null;
        }

        try {
            String coords;

            // Handle old format: "geo:lat,lng"
            if (geoStr.startsWith("geo:")) {
                coords = geoStr.replace("geo:", "");
            }
            // Handle new format: "lat°, lng°"
            else if (geoStr.contains("°")) {
                coords = geoStr.replace("°", "").trim();
            } else {
                // Try parsing as-is
                coords = geoStr.trim();
            }

            String[] parts = coords.split(",");
            if (parts.length == 2) {
                double lat = Double.parseDouble(parts[0].trim());
                double lng = Double.parseDouble(parts[1].trim());
                return new double[]{lat, lng};
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse geo string: {}", geoStr);
        }

        return null;
    }

    /**
     * Parse ISO-8601 timestamp string to Instant
     */
    private static Instant parseInstant(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            return null;
        }

        try {
            return Instant.parse(timestampStr);
        } catch (Exception e) {
            log.debug("Failed to parse timestamp: {}", timestampStr);
            return null;
        }
    }

    /**
     * Generate interpolated GPS points for a visit at regular intervals.
     * This creates multiple points at the same location during a visit period,
     * which helps staypoint detection algorithms identify stays.
     */
    private static List<GoogleTimelineGpsPoint> interpolateVisitPoints(
            Instant startTime, Instant endTime,
            double latitude, double longitude,
            String placeName, double confidence) {

        List<GoogleTimelineGpsPoint> points = new ArrayList<>();
        long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);

        // For very short visits (less than interval), generate at least start and end points
        if (durationMinutes < VISIT_INTERPOLATION_INTERVAL_MINUTES) {
            // Add start point
            points.add(GoogleTimelineGpsPoint.builder()
                    .timestamp(startTime)
                    .latitude(latitude)
                    .longitude(longitude)
                    .recordType("visit")
                    .activityType(placeName)
                    .confidence(confidence)
                    .velocityMs(0.0)
                    .build());

            // Add end point if it's different from start
            if (durationMinutes > 0) {
                points.add(GoogleTimelineGpsPoint.builder()
                        .timestamp(endTime)
                        .latitude(latitude)
                        .longitude(longitude)
                        .recordType("visit")
                        .activityType(placeName)
                        .confidence(confidence)
                        .velocityMs(0.0)
                        .build());
            }
            return points;
        }

        // Generate points at regular intervals
        Instant currentTime = startTime;
        while (!currentTime.isAfter(endTime)) {
            points.add(GoogleTimelineGpsPoint.builder()
                    .timestamp(currentTime)
                    .latitude(latitude)
                    .longitude(longitude)
                    .recordType("visit")
                    .activityType(placeName)
                    .confidence(confidence)
                    .velocityMs(0.0)
                    .build());

            currentTime = currentTime.plus(VISIT_INTERPOLATION_INTERVAL_MINUTES, ChronoUnit.MINUTES);
        }

        // Add final point at exact end time if we didn't already add it
        if (!points.isEmpty()) {
            GoogleTimelineGpsPoint lastPoint = points.get(points.size() - 1);
            if (!lastPoint.getTimestamp().equals(endTime)) {
                points.add(GoogleTimelineGpsPoint.builder()
                        .timestamp(endTime)
                        .latitude(latitude)
                        .longitude(longitude)
                        .recordType("visit")
                        .activityType(placeName)
                        .confidence(confidence)
                        .velocityMs(0.0)
                        .build());
            }
        }

        return points;
    }
}
