package org.github.tess1o.geopulse.immich.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestImmichConnectionRequest {
    @NotBlank(message = "Server URL is required")
    private String serverUrl;

    private String apiKey; // Optional - will use saved API key from DB if not provided
}
