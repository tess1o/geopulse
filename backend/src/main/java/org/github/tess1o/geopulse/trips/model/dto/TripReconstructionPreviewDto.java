package org.github.tess1o.geopulse.trips.model.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record TripReconstructionPreviewDto(
        long estimatedPoints,
        Instant startTime,
        Instant endTime,
        List<String> warnings
) {
}
