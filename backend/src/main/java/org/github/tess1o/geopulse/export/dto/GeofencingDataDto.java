package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeofencingDataDto {
    private String dataType;
    private Instant exportDate;
    private List<GeofenceRuleDto> rules;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class GeofenceRuleDto {
        private Long id;
        private String name;
        private Double northEastLat;
        private Double northEastLon;
        private Double southWestLat;
        private Double southWestLon;
        private Boolean monitorEnter;
        private Boolean monitorLeave;
        private Integer cooldownSeconds;
        private Long enterTemplateId;
        private Long leaveTemplateId;
        private String status;
        private List<SubjectDto> subjects;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SubjectDto {
        private UUID userId;
        private String email;
        private Instant createdAt;
    }
}
