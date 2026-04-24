package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanSearchResultDto {
    private String sourceType;
    private String title;
    private String subtitle;
    private Double latitude;
    private Double longitude;
    private Long favoriteId;
    private String favoriteType;
    private Long geocodingId;
    private String providerName;
}
