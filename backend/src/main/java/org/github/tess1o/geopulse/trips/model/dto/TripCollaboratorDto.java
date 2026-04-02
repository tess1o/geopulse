package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorAccessRole;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripCollaboratorDto {
    private UUID userId;
    private String fullName;
    private String email;
    private String avatar;
    private TripCollaboratorAccessRole accessRole;
    private Instant createdAt;
    private Instant updatedAt;
}
