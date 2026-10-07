package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripVisitOverrideRequestDto {
    @Schema(examples = "CONFIRM_VISITED")
    private String action; // CONFIRM_VISITED, REJECT_VISIT, RESET_TO_AUTO
    @Schema(examples = "2025-06-07T18:30:00Z")
    private Instant visitedAt;
}

