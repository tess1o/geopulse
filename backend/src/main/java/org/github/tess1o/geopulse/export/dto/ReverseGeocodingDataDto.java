package org.github.tess1o.geopulse.export.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReverseGeocodingDataDto {
    private String dataType;
    private Instant exportDate;
    private List<ReverseGeocodingLocationDto> locations;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ReverseGeocodingLocationDto {
        private long id;
        private Double requestLatitude;
        private Double requestLongitude;
        private Double resultLatitude;
        private Double resultLongitude;
        private String displayName;
        private String providerName;
        private Instant createdAt;
        private Instant lastAccessedAt;
        private String city;
        private String country;
        private Double boundingBoxNorthEastLatitude;
        private Double boundingBoxNorthEastLongitude;
        private Double boundingBoxSouthWestLatitude;
        private Double boundingBoxSouthWestLongitude;
    }
}