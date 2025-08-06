package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImmichExifInfo {
    private Double latitude;
    private Double longitude;
}