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
public class RawGpsDataDto {
    private String dataType;
    private Instant exportDate;
    private Instant startDate;
    private Instant endDate;
    private List<GpsPointDto> points;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class GpsPointDto {
        private Long id;
        private Instant timestamp;
        private Double latitude;
        private Double longitude;
        private Double accuracy;
        private Double altitude;
        private Double speed;
        private String source;
        private String deviceId;
        private Double battery;
    }
}