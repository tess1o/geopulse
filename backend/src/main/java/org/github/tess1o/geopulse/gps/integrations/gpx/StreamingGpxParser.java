package org.github.tess1o.geopulse.gps.integrations.gpx;

import lombok.extern.slf4j.Slf4j;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Streaming parser for GPX (GPS Exchange Format) XML files.
 * Uses StAX (XMLStreamReader) for memory-efficient parsing.
 *
 * This parser:
 * - Parses XML incrementally without loading entire file into memory
 * - Extracts only track points (trkpt) and waypoints (wpt) with timestamps
 * - Skips metadata and other elements
 * - Uses callback-based processing to avoid accumulating all entities in memory
 *
 * Memory usage: Constant O(1) - only current track point/waypoint in memory at a time.
 */
@Slf4j
public class StreamingGpxParser {

    private final InputStream inputStream;
    private final XMLInputFactory xmlInputFactory;

    public StreamingGpxParser(InputStream inputStream) {
        this.inputStream = inputStream;
        this.xmlInputFactory = XMLInputFactory.newInstance();
        // Disable external entity processing for security
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    /**
     * Callback interface for processing GPS points as they are parsed
     */
    @FunctionalInterface
    public interface GpxPointCallback {
        void onGpxPoint(GpxPoint point, ParsingStats currentStats);
    }

    /**
     * Simple GPS point extracted from GPX (track point or waypoint)
     */
    public static class GpxPoint {
        public final double lat;
        public final double lon;
        public final Instant time;
        public final Double elevation;
        public final Double speed;
        public final String type; // "trackpoint" or "waypoint"

        public GpxPoint(double lat, double lon, Instant time, Double elevation, Double speed, String type) {
            this.lat = lat;
            this.lon = lon;
            this.time = time;
            this.elevation = elevation;
            this.speed = speed;
            this.type = type;
        }
    }

    /**
     * Statistics collected during parsing
     */
    public static class ParsingStats {
        public int totalTrackPoints = 0;
        public int totalWaypoints = 0;
        public int totalGpsPoints = 0;
        public Instant firstTimestamp = null;
        public Instant lastTimestamp = null;

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

    /**
     * Parse GPX XML and invoke callback for each GPS point.
     *
     * @param callback function to call for each parsed GPS point
     * @return parsing statistics
     * @throws IOException if parsing fails
     */
    public ParsingStats parseGpsPoints(GpxPointCallback callback) throws IOException {
        ParsingStats stats = new ParsingStats();

        try {
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String elementName = reader.getLocalName();

                    if ("trkpt".equals(elementName)) {
                        // Parse track point
                        GpxPoint point = parseTrackPoint(reader);
                        if (point != null) {
                            stats.totalTrackPoints++;
                            stats.totalGpsPoints++;
                            stats.updateTimestamp(point.time);
                            callback.onGpxPoint(point, stats);
                        }
                    } else if ("wpt".equals(elementName)) {
                        // Parse waypoint
                        GpxPoint point = parseWaypoint(reader);
                        if (point != null) {
                            stats.totalWaypoints++;
                            stats.totalGpsPoints++;
                            stats.updateTimestamp(point.time);
                            callback.onGpxPoint(point, stats);
                        }
                    }
                    // Skip all other elements (metadata, routes, etc.)
                }
            }

            reader.close();

            log.info("GPX parsing complete: trackPoints={}, waypoints={}, totalGpsPoints={}, dateRange={} to {}",
                    stats.totalTrackPoints, stats.totalWaypoints, stats.totalGpsPoints,
                    stats.firstTimestamp, stats.lastTimestamp);

        } catch (XMLStreamException e) {
            throw new IOException("Failed to parse GPX XML: " + e.getMessage(), e);
        }

        return stats;
    }

    /**
     * Parse a track point element (<trkpt lat="..." lon="...">)
     */
    private GpxPoint parseTrackPoint(XMLStreamReader reader) throws XMLStreamException {
        // Get lat/lon from attributes
        Double lat = getDoubleAttribute(reader, "lat");
        Double lon = getDoubleAttribute(reader, "lon");

        if (lat == null || lon == null) {
            skipElement(reader, "trkpt");
            return null;
        }

        // Parse child elements for time, elevation, speed
        Instant time = null;
        Double elevation = null;
        Double speed = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String elementName = reader.getLocalName();

                switch (elementName) {
                    case "time" -> time = parseTime(reader);
                    case "ele" -> elevation = parseDoubleElement(reader);
                    case "speed" -> speed = parseDoubleElement(reader);
                    // Skip other elements like extensions, etc.
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("trkpt".equals(reader.getLocalName())) {
                    break;
                }
            }
        }

        // Must have valid time to create GPS point
        if (time == null) {
            return null;
        }

        return new GpxPoint(lat, lon, time, elevation, speed, "trackpoint");
    }

    /**
     * Parse a waypoint element (<wpt lat="..." lon="...">)
     */
    private GpxPoint parseWaypoint(XMLStreamReader reader) throws XMLStreamException {
        // Get lat/lon from attributes
        Double lat = getDoubleAttribute(reader, "lat");
        Double lon = getDoubleAttribute(reader, "lon");

        if (lat == null || lon == null) {
            skipElement(reader, "wpt");
            return null;
        }

        // Parse child elements for time, elevation
        Instant time = null;
        Double elevation = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String elementName = reader.getLocalName();

                switch (elementName) {
                    case "time" -> time = parseTime(reader);
                    case "ele" -> elevation = parseDoubleElement(reader);
                    // Skip other elements like name, desc, sym, etc.
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("wpt".equals(reader.getLocalName())) {
                    break;
                }
            }
        }

        // Must have valid time to create GPS point
        if (time == null) {
            return null;
        }

        return new GpxPoint(lat, lon, time, elevation, null, "waypoint");
    }

    /**
     * Get a double attribute from current element
     */
    private Double getDoubleAttribute(XMLStreamReader reader, String attributeName) {
        String value = reader.getAttributeValue(null, attributeName);
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.debug("Failed to parse {} attribute: {}", attributeName, value);
            return null;
        }
    }

    /**
     * Parse time element text content
     */
    private Instant parseTime(XMLStreamReader reader) throws XMLStreamException {
        String timeText = getElementText(reader);
        if (timeText == null || timeText.isEmpty()) {
            return null;
        }

        try {
            return Instant.parse(timeText);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse time: {}", timeText);
            return null;
        }
    }

    /**
     * Parse element with double content
     */
    private Double parseDoubleElement(XMLStreamReader reader) throws XMLStreamException {
        String text = getElementText(reader);
        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            log.debug("Failed to parse double element: {}", text);
            return null;
        }
    }

    /**
     * Get text content of current element
     */
    private String getElementText(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder text = new StringBuilder();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                text.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }

        return text.toString().trim();
    }

    /**
     * Skip entire element and its children
     */
    private void skipElement(XMLStreamReader reader, String elementName) throws XMLStreamException {
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }
}
