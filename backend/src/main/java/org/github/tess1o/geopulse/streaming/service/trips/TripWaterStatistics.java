package org.github.tess1o.geopulse.streaming.service.trips;

/**
 * Water-surface evidence used for conservative boat classification.
 *
 * @param waterDistanceMeters total usable path distance classified as water
 * @param waterDistanceRatio ratio of usable path distance classified as water
 * @param longestWaterSegmentMeters longest continuous water segment distance
 * @param waterSampleCount number of usable GPS/path segments sampled
 * @param evidenceAvailable true when enough data existed to calculate evidence
 */
public record TripWaterStatistics(
        Double waterDistanceMeters,
        Double waterDistanceRatio,
        Double longestWaterSegmentMeters,
        Integer waterSampleCount,
        Boolean evidenceAvailable
) {
    public static TripWaterStatistics unavailable() {
        return new TripWaterStatistics(null, null, null, null, false);
    }

    public boolean hasEvidence() {
        return Boolean.TRUE.equals(evidenceAvailable)
                && waterDistanceMeters != null
                && waterDistanceRatio != null
                && longestWaterSegmentMeters != null
                && waterSampleCount != null
                && waterSampleCount > 0;
    }
}
