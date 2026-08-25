package org.github.tess1o.geopulse.gps.integrations.geojson.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes GeoJSON properties that may be scalars or coordinate-aligned arrays.
 */
public class GeoJsonPropertiesDeserializer extends JsonDeserializer<GeoJsonProperties> {

    @Override
    public GeoJsonProperties deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode root = parser.getCodec().readTree(parser);
        GeoJsonProperties properties = new GeoJsonProperties();

        properties.setTimestamp(readString(root, "timestamp", "time"));
        properties.setTimestampValues(readStringArray(root, "timestamp", "time"));
        properties.setAltitude(readDouble(root, "altitude"));
        properties.setAltitudeValues(readDoubleArray(root, "altitude"));
        properties.setVelocity(readDouble(root, "velocity", "speed"));
        properties.setVelocityValues(readDoubleArray(root, "velocity", "speed"));
        properties.setAccuracy(readDouble(root, "accuracy"));
        properties.setAccuracyValues(readDoubleArray(root, "accuracy"));
        properties.setDeviceId(readString(root, "deviceId", "device_id"));
        properties.setDeviceIdValues(readStringArray(root, "deviceId", "device_id"));
        properties.setSourceType(readString(root, "sourceType", "source_type"));
        properties.setSourceTypeValues(readStringArray(root, "sourceType", "source_type"));
        properties.setVerticalAccuracy(readDouble(root, "verticalAccuracy", "vertical_accuracy"));
        properties.setVerticalAccuracyValues(readDoubleArray(root, "verticalAccuracy", "vertical_accuracy"));
        properties.setBattery(readInteger(root, "battery"));
        properties.setBatteryValues(readIntegerArray(root, "battery"));
        properties.setCourse(readDouble(root, "course", "bearing"));
        properties.setCourseValues(readDoubleArray(root, "course", "bearing"));

        return properties;
    }

    private static JsonNode findField(JsonNode root, String... names) {
        if (root == null || root.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static String readString(JsonNode root, String... names) {
        JsonNode value = findField(root, names);
        if (value == null) {
            return null;
        }
        if (value.isArray()) {
            return firstString(value);
        }
        return value.asText();
    }

    private static Double readDouble(JsonNode root, String... names) {
        JsonNode value = findField(root, names);
        if (value == null) {
            return null;
        }
        if (value.isArray()) {
            return firstDouble(value);
        }
        return toDouble(value);
    }

    private static Integer readInteger(JsonNode root, String... names) {
        JsonNode value = findField(root, names);
        if (value == null) {
            return null;
        }
        if (value.isArray()) {
            return firstInteger(value);
        }
        return toInteger(value);
    }

    private static List<String> readStringArray(JsonNode root, String... names) {
        JsonNode value = findField(root, names);
        if (value == null || !value.isArray()) {
            return null;
        }

        List<String> values = new ArrayList<>();
        value.forEach(item -> values.add(item == null || item.isNull() ? null : item.asText()));
        return values;
    }

    private static List<Double> readDoubleArray(JsonNode root, String... names) {
        JsonNode value = findField(root, names);
        if (value == null || !value.isArray()) {
            return null;
        }

        List<Double> values = new ArrayList<>();
        value.forEach(item -> values.add(toDouble(item)));
        return values;
    }

    private static List<Integer> readIntegerArray(JsonNode root, String... names) {
        JsonNode value = findField(root, names);
        if (value == null || !value.isArray()) {
            return null;
        }

        List<Integer> values = new ArrayList<>();
        value.forEach(item -> values.add(toInteger(item)));
        return values;
    }

    private static String firstString(JsonNode values) {
        for (JsonNode value : values) {
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private static Double firstDouble(JsonNode values) {
        for (JsonNode value : values) {
            Double parsed = toDouble(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Integer firstInteger(JsonNode values) {
        for (JsonNode value : values) {
            Integer parsed = toInteger(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Double toDouble(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Integer toInteger(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
