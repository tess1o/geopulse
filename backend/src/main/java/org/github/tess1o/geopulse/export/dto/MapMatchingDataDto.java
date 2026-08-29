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
public class MapMatchingDataDto {
    private String dataType;
    private Instant exportDate;
    private Instant startDate;
    private Instant endDate;
    private List<PathMatchDto> pathMatches;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PathMatchDto {
        private Long id;
        private Long tripId;
        private Instant tripTimestamp;
        private String provider;
        private String profile;
        private String configHash;
        private String inputHash;
        private String status;
        private Integer attempts;
        private Instant nextAttemptAt;
        private Instant lastAttemptAt;
        private Instant completedAt;
        private String matchedSegmentsJson;
        private String source;
        private Integer priority;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
