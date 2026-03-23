package org.github.tess1o.geopulse.geofencing.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class GeofenceRuleSubjectDto {
    private UUID userId;
    private String displayName;
}
