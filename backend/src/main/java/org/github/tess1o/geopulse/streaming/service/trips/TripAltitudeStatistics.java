package org.github.tess1o.geopulse.streaming.service.trips;

/**
 * Altitude evidence used for altitude-based flight classification.
 *
 * @param sustainedHeightMeters height above the higher of the trip's start and end altitude that the trip
 *                              stays at for at least a quarter of its time
 * @param verticalSpeedP90      90th percentile of vertical speed in m/s, measured over windows of about a minute
 * @param altitudeSampleCount   number of GPS points with a usable altitude; null when the evidence was never calculated
 */
public record TripAltitudeStatistics(
        Double sustainedHeightMeters,
        Double verticalSpeedP90,
        Integer altitudeSampleCount
) {
    public static TripAltitudeStatistics unavailable() {
        return new TripAltitudeStatistics(null, null, null);
    }

    public boolean hasEvidence() {
        return sustainedHeightMeters != null && verticalSpeedP90 != null;
    }
}
