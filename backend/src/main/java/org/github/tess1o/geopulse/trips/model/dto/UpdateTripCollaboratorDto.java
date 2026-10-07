package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorAccessRole;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTripCollaboratorDto {

    @NotNull(message = "accessRole is required")
    @Schema(examples = "VIEW")
    private TripCollaboratorAccessRole accessRole;
}
