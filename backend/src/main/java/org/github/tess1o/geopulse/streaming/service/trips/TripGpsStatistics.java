package org.github.tess1o.geopulse.streaming.service.trips;


public record TripGpsStatistics(
        Double avgGpsSpeed,      // Average GPS speed in m/s
        Double maxGpsSpeed,      // Maximum GPS speed in m/s
        Double speedVariance,    // Speed variance (consistency indicator)
        Integer lowAccuracyPointsCount // Count of low-accuracy GPS points
) {
    /**
     * Create empty statistics for cases with no valid GPS data.
     */
    public static TripGpsStatistics empty() {
        return new TripGpsStatistics(null, null, null, null);
    }

    /**
     * Check if statistics contain valid data.
     */
    public boolean hasValidData() {
        return avgGpsSpeed != null && maxGpsSpeed != null;
    }
}