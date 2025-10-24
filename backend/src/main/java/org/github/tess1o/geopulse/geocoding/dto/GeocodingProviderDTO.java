package org.github.tess1o.geopulse.geocoding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for geocoding provider information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodingProviderDTO {
    private String name;
    private String displayName;
    private Boolean enabled;
    private Boolean isPrimary;
    private Boolean isFallback;
}
