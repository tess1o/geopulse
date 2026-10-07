package org.github.tess1o.geopulse.immich.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class UpdateImmichConfigRequest {
    @NotBlank(message = "Server URL is required")
    @Schema(examples = "https://immich.example.com")
    private String serverUrl;
    
    @NotBlank(message = "API key is required")
    @Schema(examples = "immich-api-key")
    private String apiKey;
    
    @NotNull(message = "Enabled flag is required")
    @Schema(examples = "true")
    private Boolean enabled;
}