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
public class TimelineOverridesDataDto {
    private String dataType;
    private Instant exportDate;
    private List<TripMovementOverrideDto> tripMovementOverrides;
    private List<DataGapStayOverrideDto> dataGapStayOverrides;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TripMovementOverrideDto {
        private Long id;
        private Long tripId;
        private String movementType;
        private Instant sourceTripTimestamp;
        private Long sourceTripDurationSeconds;
        private Long sourceDistanceMeters;
        private Double sourceStartLatitude;
        private Double sourceStartLongitude;
        private Double sourceEndLatitude;
        private Double sourceEndLongitude;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DataGapStayOverrideDto {
        private Long id;
        private Long dataGapId;
        private Long stayId;
        private String locationStrategy;
        private Long selectedFavoriteId;
        private Long selectedGeocodingId;
        private Double selectedLatitude;
        private Double selectedLongitude;
        private String selectedLocationName;
        private Instant sourceGapStartTime;
        private Instant sourceGapEndTime;
        private Long sourceGapDurationSeconds;
        private Double sourceBeforeLatitude;
        private Double sourceBeforeLongitude;
        private Double sourceAfterLatitude;
        private Double sourceAfterLongitude;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
