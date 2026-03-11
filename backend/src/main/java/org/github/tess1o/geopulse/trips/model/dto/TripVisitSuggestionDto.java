package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripVisitSuggestionDto {
    private Long planItemId;
    private String planItemTitle;
    private Long matchedStayId;
    private String matchedLocationName;
    private Instant matchedStayStart;
    private Long matchedStayDurationSeconds;
    private Double distanceMeters;
    private Double confidence;
    private String decision;
    private Boolean applied;
    private String reason;
}

