package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO describing a photo search window around a place geometry.
 * Used to build Immich time-range queries that include nearby stays.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacePhotoSearchWindowDTO {
    private Instant minVisit;
    private Instant maxVisit;
    private long nearbyStayCount;
    private double radiusMeters;
}
