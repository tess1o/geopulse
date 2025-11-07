package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.time.Instant;
import java.util.List;

/**
 * DTO for filtering GPS points.
 * All fields are optional - only non-null values are applied as filters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsPointFilterDTO {

    /**
     * Start of time range (inclusive)
     */
    private Instant startTime;

    /**
     * End of time range (inclusive)
     */
    private Instant endTime;

    /**
     * Minimum accuracy in meters (inclusive)
     */
    private Double accuracyMin;

    /**
     * Maximum accuracy in meters (inclusive)
     */
    private Double accuracyMax;

    /**
     * Minimum speed in km/h (inclusive)
     */
    private Double speedMin;

    /**
     * Maximum speed in km/h (inclusive)
     */
    private Double speedMax;

    /**
     * List of source types to include.
     * If null or empty, all source types are included.
     */
    private List<GpsSourceType> sourceTypes;

    /**
     * List of specific GPS point IDs to include.
     * When provided, all other filters are ignored and only these specific points are returned.
     */
    private List<Long> gpsPointIds;

    /**
     * Check if any filters are active
     */
    public boolean hasFilters() {
        return accuracyMin != null || accuracyMax != null ||
               speedMin != null || speedMax != null ||
               (sourceTypes != null && !sourceTypes.isEmpty()) ||
               (gpsPointIds != null && !gpsPointIds.isEmpty());
    }

    /**
     * Check if ID-based filtering is active
     */
    public boolean hasIdFilter() {
        return gpsPointIds != null && !gpsPointIds.isEmpty();
    }
}
