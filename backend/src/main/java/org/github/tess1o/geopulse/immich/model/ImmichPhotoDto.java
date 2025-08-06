package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImmichPhotoDto {
    private String id;
    private String originalFileName;
    private OffsetDateTime takenAt;
    private Double latitude;
    private Double longitude;
    private String thumbnailUrl;
    private String downloadUrl;
}