package org.github.tess1o.geopulse.gpssource.model;

public record GpsFilteringDefaultsDTO(
        boolean filterInaccurateData,
        int maxAllowedAccuracy,
        int maxAllowedSpeed,
        boolean enableDuplicateDetection,
        int duplicateDetectionThresholdMinutes
) {
}
