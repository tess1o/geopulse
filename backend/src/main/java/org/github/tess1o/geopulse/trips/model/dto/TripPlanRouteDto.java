package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemTravelMode;

import java.util.List;

/** The line through a trip's located plan items, in plan order, one leg per consecutive pair. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPlanRouteDto {
    /** Whether road routing is configured; when false every leg is a straight line. */
    private boolean routingAvailable;
    private List<Leg> legs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Leg {
        private Long fromItemId;
        private Long toItemId;
        /** The destination stop's own choice; null = automatic. */
        private TripPlanItemTravelMode requestedMode;
        /** The mode actually used for this leg. */
        private TripPlanItemTravelMode resolvedMode;
        /** True when the geometry follows roads/paths; false for a straight line. */
        private boolean routed;
        /** {@code [latitude, longitude]} pairs. */
        private List<double[]> coordinates;
        private Double distanceMeters;
        /** Travel time; only known for routed legs. */
        private Double durationSeconds;
    }
}
