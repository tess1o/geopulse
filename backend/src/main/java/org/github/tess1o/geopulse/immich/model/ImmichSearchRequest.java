package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImmichSearchRequest {
    private String takenAfter;
    private String takenBefore;
    private String type;
    
    @JsonProperty("withExif")
    private boolean withExif;
}