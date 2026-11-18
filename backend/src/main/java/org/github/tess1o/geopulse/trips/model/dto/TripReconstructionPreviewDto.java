package org.github.tess1o.geopulse.trips.model.dto;

import lombok.Builder;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditPreviewDTO;

import java.time.Instant;
import java.util.List;

@Builder
public record TripReconstructionPreviewDto(
        List<TimelineEditPreviewDTO.Event> events,
        Instant startTime,
        Instant endTime,
        List<ActivityCorrectionService.Warning> warnings,
        String token
) {
}
