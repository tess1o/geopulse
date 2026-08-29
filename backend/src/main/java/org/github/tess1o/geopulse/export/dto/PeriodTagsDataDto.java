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
public class PeriodTagsDataDto {
    private String dataType;
    private Instant exportDate;
    private Instant startDate;
    private Instant endDate;
    private List<PeriodTagDto> periodTags;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PeriodTagDto {
        private Long id;
        private String tagName;
        private Instant startTime;
        private Instant endTime;
        private String source;
        private Boolean active;
        private String color;
        private Boolean showAsPreset;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
