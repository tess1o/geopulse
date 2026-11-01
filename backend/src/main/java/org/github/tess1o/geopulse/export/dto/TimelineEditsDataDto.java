package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEditsDataDto {
    private String dataType;
    private Instant exportDate;
    private List<EditDto> edits;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EditDto {
        private Long id;
        private long seq;
        private TimelineEditKind kind;
        private Instant anchorStart;
        private Instant anchorEnd;
        private Instant affectedFrom;
        private Instant affectedTo;
        private Map<String, Object> payload;
        private Map<String, Object> beforeValues;
        private Long favoriteId;
        private Long geocodingId;
        private Double latitude;
        private Double longitude;
        private String placeName;
        private TimelineEditStatus status;
        private Long supersededById;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
