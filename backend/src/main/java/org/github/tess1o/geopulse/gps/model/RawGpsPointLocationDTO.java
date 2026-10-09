package org.github.tess1o.geopulse.gps.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawGpsPointLocationDTO {
    private String locationName;
    private String sourceType;
    private Long favoriteId;
    private Long geocodingId;
    private Double anchorLatitude;
    private Double anchorLongitude;

    /** Local timezone of the GPS point. Only set when the client requests location timezones. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocationTimezoneDTO locationTimezone;
}
