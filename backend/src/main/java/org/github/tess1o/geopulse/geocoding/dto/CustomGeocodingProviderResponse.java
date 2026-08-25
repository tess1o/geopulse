package org.github.tess1o.geopulse.geocoding.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class CustomGeocodingProviderResponse {
    private Long id;
    private String name;
    private String displayName;
    private String type;
    private String url;
    private Boolean enabled;
    private String language;
    private Map<String, String> headers;
    private Integer delayMs;
    private Instant createdAt;
    private Instant updatedAt;
}
