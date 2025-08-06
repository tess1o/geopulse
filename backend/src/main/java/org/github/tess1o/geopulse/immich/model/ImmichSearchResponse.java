package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ImmichSearchResponse {
    @JsonProperty("assets")
    private ImmichSearchAssets assets;
    
    @Data
    public static class ImmichSearchAssets {
        private Integer total;
        private Integer count;
        private List<ImmichAsset> items;
    }
}