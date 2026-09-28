package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImmichAlbumDetail {
    private String id;
    private String albumName;
    private List<ImmichAsset> assets;
}
