package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripReconstructionRequestDto {

    private Long tripId;

    @NotEmpty(message = "At least one segment is required")
    @Valid
    private List<TripReconstructionSegmentDto> segments;
}
