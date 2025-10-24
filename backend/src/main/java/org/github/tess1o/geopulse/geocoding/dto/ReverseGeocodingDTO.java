package org.github.tess1o.geopulse.geocoding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for displaying reverse geocoding location information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodingDTO {
    private Long id;
    private Double longitude;
    private Double latitude;
    private String displayName;
    private String city;
    private String country;
    private String providerName;
    private Instant createdAt;
    private Instant lastAccessedAt;
}
