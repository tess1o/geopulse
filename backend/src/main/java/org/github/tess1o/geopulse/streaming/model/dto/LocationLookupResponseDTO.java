package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response for checking recorded visits at an arbitrary map coordinate. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationLookupResponseDTO {
    private double latitude;
    private double longitude;
    private int matchRadiusMeters;
    private List<LocationLookupFavoriteDTO> favoriteMatches;
    private List<LocationLookupMatchDTO> visitMatches;
    private List<LocationLookupVisitDTO> nearestStays;
}
