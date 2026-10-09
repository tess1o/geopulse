package org.github.tess1o.geopulse.streaming.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;

import java.time.Instant;

/**
 * DTO representing a single visit to a specific place.
 * Used in the place details view to show visit history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceVisitDTO {
    private Long id;                    // Timeline stay ID
    private Instant timestamp;          // Start time of visit
    private long stayDuration;          // Duration in seconds
    private double latitude;
    private double longitude;
    private String locationName;        // Cached location name
    private String city;                // City name (from geocoding or favorite)

    /** Local timezone of the visit. Only set when the client requests location timezones. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocationTimezoneDTO locationTimezone;
}
