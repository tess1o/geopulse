package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.util.TimelineGpsAccuracyFilter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates altitude evidence for a trip from its GPS points.
 * <p>
 * Aircraft that fly too slowly for the speed rules (gliders, balloons) still look nothing like ground
 * travel in altitude: they spend most of the trip far above where it started and ended, and climb or sink
 * much faster than any road allows. The evidence captures both:
 * <ul>
 *   <li>Sustained height: the height above the higher endpoint the trip stays at for at least a quarter of
 *       its time. Using the higher endpoint keeps trips that simply end on a hill near zero.</li>
 *   <li>Vertical speed: the 90th percentile of climb or sink rate over windows of about a minute, which
 *       separates flights from mountain roads that also gain a lot of height, but slowly.</li>
 * </ul>
 * Altitudes are median-smoothed first, so a single bad reading cannot create either signal.
 * <p>
 * Points less accurate than the timeline's accuracy threshold are left out, as timeline generation leaves them
 * out. Callers that reload a trip's raw interval (merged trips, reclassification) get the same evidence as the
 * timeline generation itself this way.
 */
@ApplicationScoped
public class TripAltitudeStatisticsCalculator {

    static final int MIN_ALTITUDE_POINTS = 10;
    static final double MIN_ALTITUDE_COVERAGE = 0.5;
    static final double SUSTAINED_HEIGHT_TIME_SHARE = 0.25;
    static final double VERTICAL_SPEED_PERCENTILE = 0.9;
    static final Duration VERTICAL_SPEED_WINDOW = Duration.ofSeconds(60);
    static final Duration MAX_VERTICAL_SPEED_WINDOW = Duration.ofMinutes(10);

    private static final int SMOOTHING_RADIUS = 2;
    private static final int ENDPOINT_POINTS = 3;
    private static final double MAX_INTERVAL_WEIGHT_SECONDS = 300.0;

    /**
     * @param gpsPoints trip points ordered by timestamp
     * @param config    timeline configuration holding the accuracy threshold
     * @return altitude evidence; metrics stay null when too few points carry an altitude
     */
    public TripAltitudeStatistics calculateStatistics(List<GPSPoint> gpsPoints, TimelineConfig config) {
        List<GPSPoint> accuratePoints = gpsPoints == null ? List.of() : gpsPoints.stream()
                .filter(point -> TimelineGpsAccuracyFilter.shouldInclude(point, config))
                .toList();
        if (accuratePoints.isEmpty()) {
            return new TripAltitudeStatistics(null, null, 0);
        }

        List<GPSPoint> usable = accuratePoints.stream()
                .filter(TripAltitudeStatisticsCalculator::hasUsableAltitude)
                .toList();
        int sampleCount = usable.size();
        if (sampleCount < MIN_ALTITUDE_POINTS || (double) sampleCount / accuratePoints.size() < MIN_ALTITUDE_COVERAGE) {
            return new TripAltitudeStatistics(null, null, sampleCount);
        }

        double[] altitudes = smooth(usable);
        double reference = Math.max(
                median(Arrays.copyOfRange(altitudes, 0, ENDPOINT_POINTS)),
                median(Arrays.copyOfRange(altitudes, sampleCount - ENDPOINT_POINTS, sampleCount)));

        Double sustainedHeight = sustainedHeight(usable, altitudes, reference);
        Double verticalSpeed = verticalSpeedPercentile(usable, altitudes);
        if (sustainedHeight == null || verticalSpeed == null) {
            return new TripAltitudeStatistics(null, null, sampleCount);
        }
        return new TripAltitudeStatistics(sustainedHeight, verticalSpeed, sampleCount);
    }

    /**
     * Exactly 0 is what many Android trackers send when the fix has no altitude, so it is treated as missing.
     */
    private static boolean hasUsableAltitude(GPSPoint point) {
        Double altitude = point.getAltitude();
        return point.getTimestamp() != null && altitude != null && altitude != 0.0 && Double.isFinite(altitude);
    }

    private static double[] smooth(List<GPSPoint> points) {
        double[] smoothed = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            int from = Math.max(0, i - SMOOTHING_RADIUS);
            int to = Math.min(points.size(), i + SMOOTHING_RADIUS + 1);
            double[] window = new double[to - from];
            for (int j = from; j < to; j++) {
                window[j - from] = points.get(j).getAltitude();
            }
            smoothed[i] = median(window);
        }
        return smoothed;
    }

    /**
     * Time-weighted percentile of height above the reference: the height the trip stays at or above for
     * {@link #SUSTAINED_HEIGHT_TIME_SHARE} of its time.
     */
    private static Double sustainedHeight(List<GPSPoint> points, double[] altitudes, double reference) {
        int size = points.size();
        double[] weights = new double[size];
        double totalWeight = 0.0;
        for (int i = 0; i < size - 1; i++) {
            double halfInterval = intervalSeconds(points.get(i), points.get(i + 1)) / 2.0;
            weights[i] += halfInterval;
            weights[i + 1] += halfInterval;
            totalWeight += 2 * halfInterval;
        }
        if (totalWeight <= 0) {
            return null;
        }

        List<Integer> byHeightDesc = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            byHeightDesc.add(i);
        }
        byHeightDesc.sort(Comparator.comparingDouble((Integer i) -> altitudes[i]).reversed());

        double accumulated = 0.0;
        for (int index : byHeightDesc) {
            accumulated += weights[index];
            if (accumulated >= totalWeight * SUSTAINED_HEIGHT_TIME_SHARE) {
                return altitudes[index] - reference;
            }
        }
        return altitudes[byHeightDesc.getLast()] - reference;
    }

    /**
     * Each window runs from a reading to the first later reading at least {@link #VERTICAL_SPEED_WINDOW} away.
     * Windows stretched over a data gap say nothing about the climb rate and are skipped.
     */
    private static Double verticalSpeedPercentile(List<GPSPoint> points, double[] altitudes) {
        List<Double> speeds = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < points.size(); i++) {
            j = Math.max(j, i + 1);
            while (j < points.size() && secondsBetween(points.get(i), points.get(j)) < VERTICAL_SPEED_WINDOW.toSeconds()) {
                j++;
            }
            if (j >= points.size()) {
                break;
            }
            double seconds = secondsBetween(points.get(i), points.get(j));
            if (seconds <= MAX_VERTICAL_SPEED_WINDOW.toSeconds()) {
                speeds.add(Math.abs(altitudes[j] - altitudes[i]) / seconds);
            }
        }
        if (speeds.isEmpty()) {
            return null;
        }
        speeds.sort(Double::compare);
        return speeds.get((int) (VERTICAL_SPEED_PERCENTILE * (speeds.size() - 1)));
    }

    private static double secondsBetween(GPSPoint from, GPSPoint to) {
        return Duration.between(from.getTimestamp(), to.getTimestamp()).toMillis() / 1000.0;
    }

    private static double intervalSeconds(GPSPoint from, GPSPoint to) {
        return Math.min(Math.max(secondsBetween(from, to), 0.0), MAX_INTERVAL_WEIGHT_SECONDS);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
