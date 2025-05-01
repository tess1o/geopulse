package org.github.tess1o.geopulse.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing a single point in a location path.
 * Contains the essential data needed for displaying a point on a map.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationPathPointDTO {
    private Double latitude;
    private Double longitude;
    private Instant timestamp;
    private Double accuracy;
    private Double altitude;
    private Double velocity;
}