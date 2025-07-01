package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FavoritesDataDto {
    private String dataType;
    private Instant exportDate;
    private List<FavoritePointDto> points;
    private List<FavoriteAreaDto> areas;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FavoritePointDto {
        private long id;
        private String name;
        private String city;
        private String country;
        private Double latitude;
        private Double longitude;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FavoriteAreaDto {
        private long id;
        private String name;
        private String city;
        private String country;
        private Double northEastLatitude;
        private Double northEastLongitude;
        private Double southWestLatitude;
        private Double southWestLongitude;
    }
}