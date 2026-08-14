package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A compact visit row returned by the map location lookup. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationLookupVisitDTO {
    private Long id;
    private Instant timestamp;
    private long stayDuration;
    private double latitude;
    private double longitude;
    private String locationName;
    private Double distanceMeters;
    private Long favoriteId;
    private Long geocodingId;
}
