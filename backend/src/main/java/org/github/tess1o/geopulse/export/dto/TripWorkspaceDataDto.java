package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TripWorkspaceDataDto {
    private String dataType;
    private Instant exportDate;
    private List<TripDto> trips;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TripDto {
        private Long id;
        private Long periodTagId;
        private String name;
        private Instant startTime;
        private Instant endTime;
        private String status;
        private String color;
        private String notes;
        private Instant createdAt;
        private Instant updatedAt;
        private List<TripPlanItemDto> planItems;
        private List<TripCollaboratorDto> collaborators;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TripPlanItemDto {
        private Long id;
        private String title;
        private String notes;
        private Double latitude;
        private Double longitude;
        private LocalDate plannedDay;
        private String priority;
        private Integer orderIndex;
        private Boolean visited;
        private Double visitConfidence;
        private String visitSource;
        private Instant visitedAt;
        private String manualOverrideState;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TripCollaboratorDto {
        private UUID userId;
        private String email;
        private String accessRole;
        private Instant createdAt;
    }
}
