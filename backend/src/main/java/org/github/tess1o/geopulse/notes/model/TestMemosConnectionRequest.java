package org.github.tess1o.geopulse.notes.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestMemosConnectionRequest {
    @NotBlank(message = "Server URL is required")
    private String serverUrl;

    private String apiKey;
}
