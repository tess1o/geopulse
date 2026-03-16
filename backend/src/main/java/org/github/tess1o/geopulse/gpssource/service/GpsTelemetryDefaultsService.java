package org.github.tess1o.geopulse.gpssource.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gpssource.model.GpsTelemetryMappingEntry;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class GpsTelemetryDefaultsService {

    private static final List<String> DEFAULT_TRUE_VALUES = List.of("1", "true", "yes", "on");
    private static final List<String> DEFAULT_FALSE_VALUES = List.of("0", "false", "no", "off");

    public List<GpsTelemetryMappingEntry> resolveMapping(GpsSourceType sourceType, List<GpsTelemetryMappingEntry> mapping) {
        if ((sourceType == GpsSourceType.OWNTRACKS || sourceType == GpsSourceType.GPSLOGGER) &&
                mapping == null) {
            return getOwnTracksDefaults();
        }

        if (mapping == null) {
            return List.of();
        }

        return sanitizeMapping(mapping);
    }

    public List<GpsTelemetryMappingEntry> getOwnTracksDefaults() {
        return List.of(
                boolEntry("ignition", "Ignition", 10, true, true, true),
                boolEntry("armed", "Armed", 20, true, true, true),
                boolEntry("doors", "Doors", 30, true, true, true),
                numEntry("batt_v", "Battery Voltage", "V", 40, true, true, true),
                numEntry("batt_ina", "Battery INA", "V", 50, true, true, true),
                numEntry("sats", "Satellites", null, 60, true, true, true),
                numEntry("rssi", "Signal RSSI", "dBm", 70, true, true, true),
                numEntry("lte_pct", "LTE Signal", "%", 80, true, true, true),
                numEntry("pitch", "Pitch", "deg", 90, true, true, true),
                numEntry("roll", "Roll", "deg", 100, true, true, true),
                numEntry("geofence_lat", "Geofence Latitude", "deg", 110, false, false, false),
                numEntry("geofence_lon", "Geofence Longitude", "deg", 120, false, false, false),
                numEntry("geofence_radius", "Geofence Radius", "m", 130, false, false, false)
        );
    }

    public List<GpsTelemetryMappingEntry> sanitizeMapping(List<GpsTelemetryMappingEntry> mapping) {
        if (mapping == null) {
            return List.of();
        }

        return mapping.stream()
                .filter(entry -> entry != null && entry.getKey() != null && !entry.getKey().isBlank())
                .map(this::sanitizeEntry)
                .sorted(Comparator.<GpsTelemetryMappingEntry, Integer>comparing(
                        e -> e.getOrder() == null ? Integer.MAX_VALUE : e.getOrder()
                ).thenComparing(GpsTelemetryMappingEntry::getKey))
                .toList();
    }

    private GpsTelemetryMappingEntry sanitizeEntry(GpsTelemetryMappingEntry entry) {
        String normalizedType = normalizeType(entry.getType());

        return GpsTelemetryMappingEntry.builder()
                .key(entry.getKey().trim())
                .label(entry.getLabel() == null || entry.getLabel().isBlank() ? entry.getKey().trim() : entry.getLabel().trim())
                .type(normalizedType)
                .unit(entry.getUnit() == null || entry.getUnit().isBlank() ? null : entry.getUnit().trim())
                .enabled(entry.isEnabled())
                .order(entry.getOrder())
                .trueValues(normalizeValues(entry.getTrueValues()))
                .falseValues(normalizeValues(entry.getFalseValues()))
                .showInGpsData(entry.isShowInGpsData())
                .showInCurrentPopup(entry.isShowInCurrentPopup())
                .build();
    }

    private String normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "string";
        }

        String value = rawType.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "boolean", "number", "string" -> value;
            default -> "string";
        };
    }

    private List<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        List<String> normalized = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        return normalized.isEmpty() ? null : normalized;
    }

    private GpsTelemetryMappingEntry boolEntry(String key,
                                               String label,
                                               int order,
                                               boolean enabled,
                                               boolean showInGpsData,
                                               boolean showInCurrentPopup) {
        return GpsTelemetryMappingEntry.builder()
                .key(key)
                .label(label)
                .type("boolean")
                .enabled(enabled)
                .order(order)
                .trueValues(DEFAULT_TRUE_VALUES)
                .falseValues(DEFAULT_FALSE_VALUES)
                .showInGpsData(showInGpsData)
                .showInCurrentPopup(showInCurrentPopup)
                .build();
    }

    private GpsTelemetryMappingEntry numEntry(String key,
                                              String label,
                                              String unit,
                                              int order,
                                              boolean enabled,
                                              boolean showInGpsData,
                                              boolean showInCurrentPopup) {
        return GpsTelemetryMappingEntry.builder()
                .key(key)
                .label(label)
                .type("number")
                .unit(unit)
                .enabled(enabled)
                .order(order)
                .showInGpsData(showInGpsData)
                .showInCurrentPopup(showInCurrentPopup)
                .build();
    }
}
