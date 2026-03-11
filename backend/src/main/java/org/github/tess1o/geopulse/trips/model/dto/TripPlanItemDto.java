package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemOverrideState;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemVisitSource;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripPlanItemDto {
    private Long id;
    private Long tripId;
    private String title;
    private String notes;
    private Double latitude;
    private Double longitude;
    private LocalDate plannedDay;
    private TripPlanItemPriority priority;
    private Integer orderIndex;
    private Boolean isVisited;
    private Double visitConfidence;
    private TripPlanItemVisitSource visitSource;
    private Instant visitedAt;
    private TripPlanItemOverrideState manualOverrideState;
    private Long replacementItemId;
    private Instant createdAt;
    private Instant updatedAt;
}
