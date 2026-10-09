package org.github.tess1o.geopulse.streaming.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;

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

    /** Local timezone of the looked-up point, which applies to its visits. Only set when the client requests location timezones. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocationTimezoneDTO locationTimezone;
}
