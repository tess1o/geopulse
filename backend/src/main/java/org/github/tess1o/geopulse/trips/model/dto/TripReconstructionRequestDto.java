package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripReconstructionRequestDto {

    @Schema(examples = "7")
    private Long tripId;

    @NotEmpty(message = "At least one segment is required")
    private List<@Valid TripReconstructionSegmentDto> segments;

    /**
     * Confirms replacing the correction group named by a preview warning. A recommit already replaces its own
     * earlier reconstruction group without this field.
     */
    @Schema(description = "Correction group to replace, from a preview warning")
    private UUID replacesGroupId;
    private String previewToken;

    public TripReconstructionRequestDto(Long tripId, List<TripReconstructionSegmentDto> segments) {
        this(tripId, segments, null, null);
    }
}
