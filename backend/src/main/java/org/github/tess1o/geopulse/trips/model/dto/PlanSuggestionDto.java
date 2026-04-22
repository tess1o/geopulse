package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanSuggestionDto {
    private String title;
    private Double latitude;
    private Double longitude;
    private String sourceType;
    private Long favoriteId;
    private String favoriteType;
    private Long geocodingId;
}
