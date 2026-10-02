package org.github.tess1o.geopulse.immich.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestImmichConnectionResponse {
    private boolean success;
    private ImmichConnectionStatus status;
    private Integer totalAssets;
    private String details;
}
