package org.github.tess1o.geopulse.gps.integrations.traccar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraccarPosition {

    private static final List<DateTimeFormatter> OFFSET_DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX")
    );

    private static final List<DateTimeFormatter> LOCAL_DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    );

    private Long id;
    private String protocol;
    private Long deviceId;
    private Boolean valid;
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double speed; // Traccar uses knots in Position model
    private Double course;
    private String address;
    private Double accuracy;
    private Object network;
    private List<Long> geofenceIds;
    private JsonNode serverTime;
    private JsonNode deviceTime;
    private JsonNode fixTime;
    private Map<String, Object> attributes;

    public Instant resolveTimestamp() {
        Instant timestamp = parseTimestamp(fixTime);
        if (timestamp != null) {
            return timestamp;
        }

        timestamp = parseTimestamp(deviceTime);
        if (timestamp != null) {
            return timestamp;
        }

        return parseTimestamp(serverTime);
    }

    public Double resolveBatteryLevel() {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        Double batteryLevel = parseDouble(attributes.get("batteryLevel"));
        if (batteryLevel != null) {
            return batteryLevel;
        }

        // Fallback: some devices only provide "battery".
        Double battery = parseDouble(attributes.get("battery"));
        if (battery != null && battery >= 0 && battery <= 100) {
            return battery;
        }
        return null;
    }

    private static Instant parseTimestamp(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isNumber()) {
            long value = node.longValue();
            return value > 10_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
        }

        if (node.isTextual()) {
            String value = node.asText();
            if (value == null || value.isBlank()) {
                return null;
            }
            value = value.trim();

            if (value.matches("^\\d+$")) {
                long numeric = Long.parseLong(value);
                return numeric > 10_000_000_000L ? Instant.ofEpochMilli(numeric) : Instant.ofEpochSecond(numeric);
            }

            try {
                return Instant.parse(value);
            } catch (Exception ignored) {
                // Try other supported date-time formats below.
            }

            for (DateTimeFormatter formatter : OFFSET_DATE_TIME_FORMATTERS) {
                try {
                    return OffsetDateTime.parse(value, formatter).toInstant();
                } catch (Exception ignored) {
                    // Fall through.
                }
            }
            // If timezone is missing, treat incoming datetime as UTC.
            for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
                try {
                    return LocalDateTime.parse(value, formatter).toInstant(ZoneOffset.UTC);
                } catch (Exception ignored) {
                    // Try next formatter.
                }
            }
        }

        return null;
    }

    private static Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
