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
public class TimelineDataDto {
    private String dataType;
    private Instant exportDate;
    private Instant startDate;
    private Instant endDate;
    private List<StayDto> stays;
    private List<TripDto> trips;
    private List<DataGapDto> dataGaps;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class StayDto {
        private long id;
        private Instant timestamp;
        private Instant endTime;
        private Double latitude;
        private Double longitude;
        
        /**
         * Duration of stay in seconds
         */
        private Long duration;
        private String address;
        private Long favoriteId;
        private Long geocodingId;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TripDto {
        private long id;
        private Instant timestamp;
        private Instant endTime;
        private Double startLatitude;
        private Double startLongitude;
        private Double endLatitude;
        private Double endLongitude;
        
        /**
         * Distance traveled in meters
         */
        private Long distance;
        
        /**
         * Duration of trip in seconds
         */
        private Long duration;
        private String transportMode;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DataGapDto {
        private long id;
        private Instant startTime;
        private Instant endTime;
        private long durationSeconds;
        private Instant createdAt;
    }
}