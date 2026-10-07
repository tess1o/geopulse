package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class CustomGeocodingProviderRequest {
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "Provider name must use lowercase letters, numbers, and hyphens")
    @Schema(examples = "my-nominatim")
    private String name;

    @NotBlank
    @Size(max = 50)
    @Schema(examples = "Self-hosted Nominatim")
    private String displayName;

    @NotBlank
    @Pattern(regexp = "^(photon|nominatim)$", message = "Type must be photon or nominatim")
    @Schema(examples = "nominatim")
    private String type;

    @NotBlank
    @Size(max = 500)
    @Schema(examples = "https://nominatim.example.com")
    private String url;

    @Schema(examples = "true")
    private Boolean enabled = true;

    @Size(max = 50)
    @Schema(examples = "en")
    private String language;

    private Map<String, String> headers;

    @Schema(examples = "1000")
    private Integer delayMs;
}
