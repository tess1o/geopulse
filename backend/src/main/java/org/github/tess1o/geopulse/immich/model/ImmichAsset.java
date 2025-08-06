package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImmichAsset {
    private String id;
    private String originalFileName;
    private String type;
    
    @JsonProperty("fileCreatedAt")
    private OffsetDateTime takenAt;
    
    @JsonProperty("exifInfo")
    private ImmichExifInfo exifInfo;
}