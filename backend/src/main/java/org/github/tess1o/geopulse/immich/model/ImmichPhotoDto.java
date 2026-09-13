package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Integer width;
    private Integer height;
    @JsonProperty("isFavorite")
    private Boolean isFavorite;
    private Integer rating;
    private String thumbnailUrl;
    private String previewUrl;
    private String downloadUrl;
}
