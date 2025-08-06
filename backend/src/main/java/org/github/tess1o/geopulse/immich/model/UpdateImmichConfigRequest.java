package org.github.tess1o.geopulse.immich.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateImmichConfigRequest {
    @NotBlank(message = "Server URL is required")
    private String serverUrl;
    
    @NotBlank(message = "API key is required")
    private String apiKey;
    
    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;
}