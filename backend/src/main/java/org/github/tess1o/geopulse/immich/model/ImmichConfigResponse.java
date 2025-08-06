package org.github.tess1o.geopulse.immich.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImmichConfigResponse {
    private String serverUrl;
    private Boolean enabled;
}