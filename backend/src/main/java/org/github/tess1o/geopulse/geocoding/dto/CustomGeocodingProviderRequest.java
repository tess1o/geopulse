package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CustomGeocodingProviderRequest {
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "Provider name must use lowercase letters, numbers, and hyphens")
    private String name;

    @NotBlank
    @Size(max = 50)
    private String displayName;

    @NotBlank
    @Pattern(regexp = "^(photon|nominatim)$", message = "Type must be photon or nominatim")
    private String type;

    @NotBlank
    @Size(max = 500)
    private String url;

    private Boolean enabled = true;

    @Size(max = 50)
    private String language;

    private Map<String, String> headers;

    private Integer delayMs;
}
