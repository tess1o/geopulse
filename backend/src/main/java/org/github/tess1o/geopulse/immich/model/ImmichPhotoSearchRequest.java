package org.github.tess1o.geopulse.immich.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ImmichPhotoSearchRequest {
    @NotNull
    private OffsetDateTime startDate;
    
    @NotNull
    private OffsetDateTime endDate;
    
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
}