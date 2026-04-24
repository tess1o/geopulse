package org.github.tess1o.geopulse.geocoding.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-agnostic forward-search result for location queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeocodingSearchResult {
    private String title;
    private Double latitude;
    private Double longitude;
    private String city;
    private String country;
    private String providerName;
}
