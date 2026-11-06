package org.github.tess1o.geopulse.gps.service;

import org.github.tess1o.geopulse.gps.model.GpsPathRow;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsTelemetryDisplayDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsTelemetryMappingEntry;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceTypeTelemetryConfigService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class GpsTelemetryRenderingService {

    private final GpsSourceTypeTelemetryConfigService telemetryConfigService;

    public GpsTelemetryRenderingService(GpsSourceTypeTelemetryConfigService telemetryConfigService) {
        this.telemetryConfigService = telemetryConfigService;
    }

    public Map<Long, RenderedTelemetry> renderForPoints(UUID userId, List<GpsPointEntity> points) {
        if (points == null || points.isEmpty()) {
            return Map.of();
        }

        List<GpsPointEntity> pointsWithTelemetry = points.stream()
                .filter(point -> point.getTelemetry() != null && !point.getTelemetry().isEmpty())
                .filter(point -> point.getSourceType() != null)
                .toList();
        if (pointsWithTelemetry.isEmpty()) {
            return Map.of();
        }

        Set<GpsSourceType> sourceTypes = pointsWithTelemetry.stream()
                .map(GpsPointEntity::getSourceType)
                .distinct()
                .collect(Collectors.toSet());

        Map<GpsSourceType, List<GpsTelemetryMappingEntry>> mappingBySourceType =
                telemetryConfigService.getResolvedMappings(userId, sourceTypes);

        Map<Long, RenderedTelemetry> output = new HashMap<>();

        for (GpsPointEntity point : pointsWithTelemetry) {
            Map<String, Object> telemetry = point.getTelemetry();
            List<GpsTelemetryMappingEntry> mapping = mappingBySourceType.get(point.getSourceType());
            if (mapping == null || mapping.isEmpty()) {
                continue;
            }

            var rendered = render(mapping, telemetry);
            if (rendered != null) output.put(point.getId(), rendered);
        }

        return output;
    }

    public Map<Long, RenderedTelemetry> renderForPathRows(UUID userId,
            List<GpsPathRow> rows) {
        Set<GpsSourceType> sources = new HashSet<>();
        for (var row : rows) {
            if (row.sourceType() != null && row.telemetry() != null && !row.telemetry().isEmpty()) {
                sources.add(row.sourceType());
            }
        }
        if (sources.isEmpty()) return Map.of();
        var mappings = telemetryConfigService.getResolvedMappings(userId, sources);
        Map<Long, RenderedTelemetry> output = new HashMap<>();
        for (var row : rows) {
            var mapping = row.sourceType() == null ? null : mappings.get(row.sourceType());
            if (mapping == null || mapping.isEmpty() || row.telemetry() == null || row.telemetry().isEmpty()) continue;
            var rendered = render(mapping, row.telemetry());
            if (rendered != null) output.put(row.id(), rendered);
        }
        return output;
    }

    private RenderedTelemetry render(List<GpsTelemetryMappingEntry> mapping, Map<String, Object> telemetry) {
        var gpsData = buildDisplayList(mapping, telemetry, true);
        var currentPopup = buildDisplayList(mapping, telemetry, false);
        return gpsData.isEmpty() && currentPopup.isEmpty() ? null : new RenderedTelemetry(gpsData, currentPopup);
    }

    private List<GpsTelemetryDisplayDTO> buildDisplayList(List<GpsTelemetryMappingEntry> mapping,
                                                          Map<String, Object> telemetry,
                                                          boolean gpsDataContext) {
        List<GpsTelemetryDisplayDTO> entries = new ArrayList<>();

        for (GpsTelemetryMappingEntry mappingEntry : mapping) {
            if (!mappingEntry.isEnabled()) {
                continue;
            }
            if (gpsDataContext && !mappingEntry.isShowInGpsData()) {
                continue;
            }
            if (!gpsDataContext && !mappingEntry.isShowInCurrentPopup()) {
                continue;
            }

            Object rawValue = telemetry.get(mappingEntry.getKey());
            if (rawValue == null) {
                continue;
            }

            String type = normalizeType(mappingEntry.getType());
            String value = formatValue(type, mappingEntry, rawValue);
            if (value == null || value.isBlank()) {
                continue;
            }

            entries.add(new GpsTelemetryDisplayDTO(
                    mappingEntry.getKey(),
                    mappingEntry.getLabel() != null && !mappingEntry.getLabel().isBlank() ? mappingEntry.getLabel() : mappingEntry.getKey(),
                    value,
                    mappingEntry.getUnit(),
                    type
            ));
        }

        return entries;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "string";
        }

        String normalized = type.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "boolean", "number", "string" -> normalized;
            default -> "string";
        };
    }

    private String formatValue(String type, GpsTelemetryMappingEntry mappingEntry, Object rawValue) {
        return switch (type) {
            case "boolean" -> formatBoolean(mappingEntry, rawValue);
            case "number" -> formatNumber(rawValue);
            default -> String.valueOf(rawValue);
        };
    }

    private String formatBoolean(GpsTelemetryMappingEntry mappingEntry, Object rawValue) {
        Optional<Boolean> normalized = normalizeBoolean(mappingEntry, rawValue);
        if (normalized.isPresent()) {
            return normalized.get() ? "Yes" : "No";
        }
        return String.valueOf(rawValue);
    }

    private Optional<Boolean> normalizeBoolean(GpsTelemetryMappingEntry mappingEntry, Object rawValue) {
        if (rawValue instanceof Boolean rawBoolean) {
            return Optional.of(rawBoolean);
        }

        if (rawValue instanceof Number rawNumber) {
            return Optional.of(rawNumber.doubleValue() != 0.0d);
        }

        String rawString = String.valueOf(rawValue).trim().toLowerCase(Locale.ROOT);
        if (rawString.isBlank()) {
            return Optional.empty();
        }

        Set<String> trueValues = buildLookupSet(mappingEntry.getTrueValues(), Set.of("1", "true", "yes", "on"));
        Set<String> falseValues = buildLookupSet(mappingEntry.getFalseValues(), Set.of("0", "false", "no", "off"));

        if (trueValues.contains(rawString)) {
            return Optional.of(true);
        }
        if (falseValues.contains(rawString)) {
            return Optional.of(false);
        }

        return Optional.empty();
    }

    private Set<String> buildLookupSet(List<String> customValues, Set<String> defaults) {
        Set<String> output = new HashSet<>(defaults);
        if (customValues != null) {
            for (String customValue : customValues) {
                if (customValue != null && !customValue.isBlank()) {
                    output.add(customValue.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return output;
    }

    private String formatNumber(Object rawValue) {
        Double numericValue = tryParseDouble(rawValue);
        if (numericValue == null) {
            return String.valueOf(rawValue);
        }

        BigDecimal formatted = BigDecimal.valueOf(numericValue)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros();

        return formatted.toPlainString();
    }

    private Double tryParseDouble(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(rawValue).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record RenderedTelemetry(List<GpsTelemetryDisplayDTO> gpsData,
                                    List<GpsTelemetryDisplayDTO> currentPopup) {
    }
}
