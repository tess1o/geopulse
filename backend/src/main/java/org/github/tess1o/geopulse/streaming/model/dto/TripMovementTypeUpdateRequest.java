package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class TripMovementTypeUpdateRequest {
    @Schema(examples = "BICYCLE")
    private String movementType;
}
