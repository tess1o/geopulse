package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripReconstructionRequestDto {

    @Schema(examples = "7")
    private Long tripId;

    @NotEmpty(message = "At least one segment is required")
    private List<@Valid TripReconstructionSegmentDto> segments;
}
