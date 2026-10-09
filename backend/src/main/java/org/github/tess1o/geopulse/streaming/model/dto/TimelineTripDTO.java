package org.github.tess1o.geopulse.streaming.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineTripDTO implements GpsPoint {
    private Long id;  // Database ID for the trip
    private Instant timestamp;
    private double latitude;  // Start latitude
    private double longitude; // Start longitude
    private double endLatitude;  // End latitude
    private double endLongitude; // End longitude

    /**
     * Duration of trip in seconds
     */
    private long tripDuration;

    /**
     * Distance traveled in meters
     */
    private long distanceMeters;
    private String movementType;
    private String movementTypeSource;
    private List<? extends GpsPoint> path; // Only used for TRIP items

    /**
     * Local timezones at the trip start and end. Only set when the client requests location timezones.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocationTimezoneDTO startLocationTimezone;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocationTimezoneDTO endLocationTimezone;
}
