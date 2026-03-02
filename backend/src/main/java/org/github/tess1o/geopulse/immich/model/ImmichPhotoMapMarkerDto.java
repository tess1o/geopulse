package org.github.tess1o.geopulse.immich.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ImmichPhotoMapMarkerDto {
    private Double latitude;
    private Double longitude;
    private Integer count;
    private OffsetDateTime latestTakenAt;
}
