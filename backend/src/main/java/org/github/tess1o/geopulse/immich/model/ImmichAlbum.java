package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImmichAlbum {
    private String id;
    private String albumName;
    private Integer assetCount;
}
