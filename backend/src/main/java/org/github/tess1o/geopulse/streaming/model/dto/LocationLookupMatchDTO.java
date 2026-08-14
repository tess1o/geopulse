package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** A grouped place candidate found by the map location lookup. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationLookupMatchDTO {
    private String sourceType;
    private Long favoriteId;
    private Long geocodingId;
    private String name;
    private String matchReason;
    private Double nearestDistanceMeters;
    private long visitCount;
    private Instant firstVisit;
    private Instant lastVisit;
    private List<LocationLookupVisitDTO> visits;
}
