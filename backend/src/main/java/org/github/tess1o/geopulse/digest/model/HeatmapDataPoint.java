package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single point on the heatmap. Intensity is the total dwell time in seconds
 * at
 * that location, normalised on the frontend to the [0,1] range for rendering.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HeatmapDataPoint {
    /** Latitude */
    private double lat;
    /** Longitude */
    private double lng;
    /** Total time spent at this location (seconds) – used as heatmap intensity */
    private long durationSeconds;
    /** Number of separate visits – alternative intensity axis */
    private long visits;
    /** Human-readable location name, may be null for unnamed GPS clusters */
    private String name;
}
