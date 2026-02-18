package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImmichSearchRequest {
    private String takenAfter;
    private String takenBefore;
    private String type;
    
    @JsonProperty("withExif")
    private boolean withExif;
}