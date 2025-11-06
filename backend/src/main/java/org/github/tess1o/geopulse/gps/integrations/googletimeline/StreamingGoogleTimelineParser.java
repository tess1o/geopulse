package org.github.tess1o.geopulse.gps.integrations.googletimeline;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
 * <p>
 * This parser:
 * - Parses JSON incrementally without loading entire file into memory
 * - Extracts only the fields we actually need for GPS points
 * - Skips unused fields like userLocationProfile, frequentPlaces, etc.
 * - Uses callback-based processing to avoid accumulating all entities in memory
 * <p>
 * Memory usage: Constant O(1) - only current segment/record in memory at a time.
 */
@Slf4j
public class StreamingGoogleTimelineParser {

    private final InputStream inputStream;
    private final JsonFactory jsonFactory;

    /**
     * Interval in minutes between interpolated GPS points for visit records
     */
    public static final int VISIT_INTERPOLATION_INTERVAL_MINUTES = 5;

    public StreamingGoogleTimelineParser(InputStream inputStream) {
        this.inputStream = inputStream;
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
        public int totalRecordsLocations = 0;  // For records format: number of location entries
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
        RECORDS,
        UNKNOWN
    }

    /**
     * Parse Google Timeline JSON and invoke callback for each GPS point.
     * Automatically detects format (legacy array vs semantic segments vs records).
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
                // Object format - need to distinguish between RECORDS and SEMANTIC_SEGMENTS
                // Look at first field name to determine format
                token = parser.nextToken();
                if (token == JsonToken.FIELD_NAME) {
                    String firstField = parser.getCurrentName();
                    if ("locations".equals(firstField)) {
                        // Records format: object with locations array
                        stats.formatType = FormatType.RECORDS;
                        log.info("Detected Google Timeline Records format (locations array)");
                        parseRecordsFormat(parser, callback, stats);
                    } else {
                        // Semantic segments format: object with semanticSegments, rawSignals, etc.
                        stats.formatType = FormatType.SEMANTIC_SEGMENTS;
                        log.info("Detected Google Timeline semantic segments format");
                        // We've already consumed the first field name, so pass it to the parser
                        parseSemanticSegmentsFormat(parser, callback, stats);
                    }
                } else {
                    throw new IllegalArgumentException("Unknown Google Timeline format - expected field name in object");
                }
            } else {
                throw new IllegalArgumentException("Unknown Google Timeline format - expected array or object");
            }
        }

        log.info("Google Timeline parsing complete: format={}, records={}, segments={}, rawSignals={}, recordsLocations={}, gpsPoints={}, dateRange={} to {}",
                stats.formatType, stats.totalRecords, stats.totalSemanticSegments,
                stats.totalRawSignals, stats.totalRecordsLocations, stats.totalGpsPoints,
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
                    // Check if value is null before parsing
                    JsonToken token = parser.nextToken();
                    if (token != JsonToken.VALUE_NULL) {
                        recordType = "activity";
                        // Parser is already at START_OBJECT, so don't call nextToken() in the method
                        parseLegacyActivityFromObject(parser, startTime, endTime, callback, stats);
                        stats.activityCount++;
                    }
                    // If null, parser advances past it automatically
                }
                case "visit" -> {
                    // Check if value is null before parsing
                    JsonToken token = parser.nextToken();
                    if (token != JsonToken.VALUE_NULL) {
                        recordType = "visit";
                        // Parser is already at START_OBJECT, so don't call nextToken() in the method
                        parseLegacyVisitFromObject(parser, startTime, endTime, callback, stats);
                        stats.visitCount++;
                    }
                    // If null, parser advances past it automatically
                }
                case "timelinePath" -> {
                    // Check if value is null before parsing
                    JsonToken token = parser.nextToken();
                    if (token != JsonToken.VALUE_NULL) {
                        recordType = "timelinePath";
                        // Parser is already at START_ARRAY, so don't call nextToken() in the method
                        parseLegacyTimelinePathFromArray(parser, startTime, callback, stats);
                        stats.timelinePathCount++;
                    }
                    // If null, parser advances past it automatically
                }
                default -> parser.skipChildren(); // Skip unknown fields
            }
        }
    }

    private void parseLegacyActivityFromObject(JsonParser parser, Instant startTime, Instant endTime,
                                               GpsPointCallback callback, ParsingStats stats) throws IOException {
        String start = null;
        String end = null;
        Double distanceMeters = null;
        String activityType = "unknown";
        double confidence = 0.0;

        // Parser is already at START_OBJECT, no need to advance

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
                            confidence = parser.getValueAsString() == null ? null : Double.valueOf(parser.getValueAsString());
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

    private void parseLegacyVisitFromObject(JsonParser parser, Instant startTime, Instant endTime,
                                            GpsPointCallback callback, ParsingStats stats) throws IOException {
        String placeLocation = null;
        String semanticType = "unknown";
        double confidence = 0.0;

        // Parser is already at START_OBJECT, no need to advance

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "probability" -> {
                    parser.nextToken();
                    confidence = parser.getValueAsString() == null ? null : Double.valueOf(parser.getValueAsString());
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

    private void parseLegacyTimelinePathFromArray(JsonParser parser, Instant startTime,
                                                  GpsPointCallback callback, ParsingStats stats) throws IOException {
        // Parser is already at START_ARRAY, no need to advance

        // Now advance into the array
        JsonToken token = parser.nextToken();
        while (token != JsonToken.END_ARRAY) {
            // We should be at START_OBJECT for each path point
            if (token == JsonToken.START_OBJECT) {
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
                        offsetMinutes = parser.getValueAsString() == null ? 0 : Integer.parseInt(parser.getValueAsString());
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

            // Advance to next element or END_ARRAY
            token = parser.nextToken();
        }
        // Parser is now at END_ARRAY, which is correct for returning to parseLegacyRecord
    }

    /**
     * Parse Records format: object with locations array
     */
    private void parseRecordsFormat(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        // Parser is positioned at FIELD_NAME "locations" after format detection
        // Advance to the array value
        parser.nextToken(); // Should be START_ARRAY

        if (parser.getCurrentToken() != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException("Records format: expected 'locations' to be an array");
        }

        // Process each location in the array
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            parseRecordsLocation(parser, callback, stats);
            stats.totalRecordsLocations++;
        }

        // Skip any remaining fields in the root object (though typically there are none)
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            parser.skipChildren();
        }
    }

    /**
     * Parse a single location entry from Records format
     */
    private void parseRecordsLocation(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        Integer latitudeE7 = null;
        Integer longitudeE7 = null;
        Instant timestamp = null;
        Double accuracy = null;
        Double altitude = null;

        // Parser is positioned at START_OBJECT of a location entry
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "latitudeE7" -> {
                    parser.nextToken();
                    latitudeE7 = parser.getIntValue();
                }
                case "longitudeE7" -> {
                    parser.nextToken();
                    longitudeE7 = parser.getIntValue();
                }
                case "timestamp" -> {
                    parser.nextToken();
                    timestamp = parseInstant(parser.getValueAsString());
                }
                case "accuracy" -> {
                    parser.nextToken();
                    // Accuracy can be integer or double
                    accuracy = parser.getDoubleValue();
                }
                case "altitude" -> {
                    parser.nextToken();
                    // Altitude can be integer or double
                    altitude = parser.getDoubleValue();
                }
                default -> parser.skipChildren(); // Skip other fields like source, deviceTag, etc.
            }
        }

        // Convert E7 coordinates to decimal and emit GPS point
        if (latitudeE7 != null && longitudeE7 != null && timestamp != null) {
            double latitude = latitudeE7 / 10000000.0;
            double longitude = longitudeE7 / 10000000.0;

            GoogleTimelineGpsPoint point = GoogleTimelineGpsPoint.builder()
                    .timestamp(timestamp)
                    .latitude(latitude)
                    .longitude(longitude)
                    .recordType("records_location")
                    .activityType("location")
                    .confidence(accuracy != null ? Math.min(1.0, 100.0 / accuracy) : 0.5) // Convert accuracy to confidence (lower accuracy = higher confidence)
                    .accuracy(accuracy) // Store raw accuracy in meters
                    .altitude(altitude) // Store altitude in meters
                    .build();

            stats.totalGpsPoints++;
            stats.updateTimestamp(timestamp);
            callback.onGpsPoint(point, stats);
        }
    }

    /**
     * Parse semantic segments format: object with semanticSegments and rawSignals
     */
    private void parseSemanticSegmentsFormat(JsonParser parser, GpsPointCallback callback, ParsingStats stats)
            throws IOException {

        // Parser may be positioned at FIELD_NAME (if we peeked during format detection)
        // or at START_OBJECT (if called directly)
        // Handle the first field if we're already at it
        if (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
            String fieldName = parser.getCurrentName();
            processSemanticSegmentField(parser, fieldName, callback, stats);
        }

        // Continue processing remaining fields
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;
            processSemanticSegmentField(parser, fieldName, callback, stats);
        }
    }

    /**
     * Process a single field in semantic segments format
     */
    private void processSemanticSegmentField(JsonParser parser, String fieldName,
                                             GpsPointCallback callback, ParsingStats stats) throws IOException {
        switch (fieldName) {
            case "semanticSegments" -> parseSemanticSegmentsArray(parser, callback, stats);
            case "rawSignals" -> parseRawSignalsArray(parser, callback, stats);
            case "userLocationProfile" -> parser.skipChildren(); // Skip - we don't use this
            default -> parser.skipChildren();
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
                    confidence = parser.getValueAsString() == null ? null : Double.valueOf(parser.getValueAsString());
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
                            confidence = parser.getValueAsString() == null ? null : Double.valueOf(parser.getValueAsString());
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
        Double accuracyMeters = null;
        Double altitudeMeters = null;

        parser.nextToken(); // START_OBJECT

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            if (fieldName == null) continue;

            switch (fieldName) {
                case "latLng", "LatLng" -> {  // Handle both lowercase and capitalized versions
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
                case "accuracyMeters" -> {
                    parser.nextToken();
                    try {
                        accuracyMeters = parser.getDoubleValue();
                    } catch (Exception e) {
                        log.error("Error parsing accuracyMeters", e.getMessage());
                        //do nothing
                    }
                }
                case "altitudeMeters" -> {
                    parser.nextToken();
                    try {
                        altitudeMeters = parser.getDoubleValue();
                    } catch (Exception e) {
                        log.error("Error parsing accuracyMeters", e.getMessage());
                        //do nothing
                    }
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
                    .accuracy(accuracyMeters)
                    .altitude(altitudeMeters)
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
     * Parse timestamp to Instant - handles both ISO-8601 strings and numeric epoch timestamps
     */
    private static Instant parseInstant(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            return null;
        }

        try {
            // Try ISO-8601 format first (e.g., "2019-04-03T08:00:00.000+02:00")
            return Instant.parse(timestampStr);
        } catch (Exception e) {
            // Try parsing as numeric epoch timestamp (seconds with optional fractional part)
            try {
                double epochSeconds = Double.parseDouble(timestampStr);
                long seconds = (long) epochSeconds;
                long nanos = (long) ((epochSeconds - seconds) * 1_000_000_000);
                return Instant.ofEpochSecond(seconds, nanos);
            } catch (NumberFormatException nfe) {
                log.debug("Failed to parse timestamp as ISO-8601 or numeric: {}", timestampStr);
                return null;
            }
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
