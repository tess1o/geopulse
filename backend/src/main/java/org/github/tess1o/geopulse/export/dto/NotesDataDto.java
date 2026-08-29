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
public class NotesDataDto {
    private String dataType;
    private Instant exportDate;
    private Instant startDate;
    private Instant endDate;
    private List<NoteDto> notes;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class NoteDto {
        private Long id;
        private String title;
        private String contentMarkdown;
        private String snippet;
        private Instant eventTime;
        private Double latitude;
        private Double longitude;
        private String locationSource;
        private String anchorType;
        private Long stayId;
        private Long tripId;
        private Instant sourceItemStartTime;
        private Long sourceItemDurationSeconds;
        private Double sourceStartLatitude;
        private Double sourceStartLongitude;
        private Double sourceEndLatitude;
        private Double sourceEndLongitude;
        private Long sourceDistanceMeters;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant deletedAt;
    }
}
