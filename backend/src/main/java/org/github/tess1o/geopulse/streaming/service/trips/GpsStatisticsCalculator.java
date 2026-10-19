package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for calculating GPS-based statistics from GPS point sequences.
 * These statistics are used for more accurate travel classification.
 * <p>
 * Statistics are made robust against short bursts of bad speed readings, which trackers
 * commonly send right after waking up and acquiring a fresh fix:
 * <ul>
 *   <li>Average speed and variance are time-weighted: each reading counts for the time it covers,
 *       so a burst of readings a few seconds apart barely moves the average.</li>
 *   <li>Max speed is the highest speed sustained for at least {@link #SUSTAINED_SPEED_WINDOW},
 *       so a single spike cannot turn a walk into a car trip.</li>
 * </ul>
 */
@ApplicationScoped
@Slf4j
public class GpsStatisticsCalculator {

    @Inject
    GpsPointRepository gpsPointRepository;

    private static final double ACCURACY_THRESHOLD_METERS = 50.0; // GPS points with accuracy > 50m are considered low quality

    /**
     * Minimum time a speed must be held to count as the trip's max speed.
     */
    static final Duration SUSTAINED_SPEED_WINDOW = Duration.ofSeconds(30);

    /**
     * Upper bound on the time a single reading can represent when weighting.
     * Keeps one reading next to a data gap from dominating the average.
     */
    private static final double MAX_INTERVAL_WEIGHT_SECONDS = 300.0;

    /**
     * Trips with fewer points than this keep the raw max speed - there is not enough data
     * to confirm a sustained peak, and dropping the only evidence would hurt more than help.
     */
    private static final int MIN_POINTS_FOR_SUSTAINED_MAX = 3;

    public TripGpsStatistics calculateStatistics(UUID userId, Instant startTime, Instant endTime) {
        List<GPSPoint> points = gpsPointRepository.findEssentialPointsInInterval(userId, startTime, endTime);
        return calculateStatistics(points);

    }

    /**
     * Calculate comprehensive GPS statistics from a list of GPS points.
     *
     * @param gpsPoints list of GPS points with speed and accuracy data, ordered by timestamp
     * @return GPS statistics object
     */
    public TripGpsStatistics calculateStatistics(List<GPSPoint> gpsPoints) {
        if (gpsPoints == null || gpsPoints.isEmpty()) {
            return TripGpsStatistics.empty();
        }

        // Filter out points without speed data
        List<GPSPoint> validSpeedPoints = gpsPoints.stream()
                .filter(point -> point.getSpeed() >= 0) // GPS speed should be non-negative
                .toList();

        if (validSpeedPoints.isEmpty()) {
            log.debug("No valid GPS speed data found in {} points", gpsPoints.size());
            return TripGpsStatistics.empty();
        }

        int lowAccuracyCount = 0;
        for (GPSPoint point : validSpeedPoints) {
            if (point.getAccuracy() > ACCURACY_THRESHOLD_METERS) {
                lowAccuracyCount++;
            }
        }

        double[] weights = calculateTimeWeights(validSpeedPoints);
        double totalWeight = 0.0;
        double weightedSpeedSum = 0.0;
        for (int i = 0; i < validSpeedPoints.size(); i++) {
            totalWeight += weights[i];
            weightedSpeedSum += weights[i] * validSpeedPoints.get(i).getSpeed();
        }
        double avgSpeed = weightedSpeedSum / totalWeight; // Speed in m/s

        double weightedSquaredDifferences = 0.0;
        for (int i = 0; i < validSpeedPoints.size(); i++) {
            double diff = validSpeedPoints.get(i).getSpeed() - avgSpeed;
            weightedSquaredDifferences += weights[i] * diff * diff;
        }
        double speedVariance = weightedSquaredDifferences / totalWeight;

        double maxSpeed = calculateSustainedMaxSpeed(validSpeedPoints);
        return new TripGpsStatistics(avgSpeed, maxSpeed, speedVariance, lowAccuracyCount);
    }

    /**
     * Weights each reading by the time it represents: half the interval to the previous point
     * plus half the interval to the next one. Falls back to equal weights when timestamps are
     * missing or the points span no time.
     */
    private double[] calculateTimeWeights(List<GPSPoint> points) {
        int size = points.size();
        double[] weights = new double[size];

        if (size > 1 && hasUsableTimestamps(points)) {
            double totalWeight = 0.0;
            for (int i = 0; i < size - 1; i++) {
                double halfInterval = intervalSeconds(points.get(i), points.get(i + 1)) / 2.0;
                weights[i] += halfInterval;
                weights[i + 1] += halfInterval;
                totalWeight += 2 * halfInterval;
            }
            if (totalWeight > 0) {
                return weights;
            }
        }

        Arrays.fill(weights, 1.0);
        return weights;
    }

    private boolean hasUsableTimestamps(List<GPSPoint> points) {
        return points.stream().allMatch(point -> point.getTimestamp() != null);
    }

    private double intervalSeconds(GPSPoint from, GPSPoint to) {
        double seconds = Duration.between(from.getTimestamp(), to.getTimestamp()).toMillis() / 1000.0;
        return Math.min(Math.max(seconds, 0.0), MAX_INTERVAL_WEIGHT_SECONDS);
    }

    /**
     * Highest speed held for at least {@link #SUSTAINED_SPEED_WINDOW}. For each reading, the
     * window runs to the first later reading at least that far away, and the speed held over
     * it is the lowest reading inside. A spike followed by normal readings is therefore capped
     * at the normal speed, while a real vehicle reports high speeds on consecutive readings.
     * Falls back to the raw max when the trip is too short or sparse to fill a window.
     */
    private double calculateSustainedMaxSpeed(List<GPSPoint> points) {
        double rawMaxSpeed = points.stream().mapToDouble(GPSPoint::getSpeed).max().orElse(0.0);
        if (points.size() < MIN_POINTS_FOR_SUSTAINED_MAX || !hasUsableTimestamps(points)) {
            return rawMaxSpeed;
        }

        double sustainedMaxSpeed = -1.0;
        for (int i = 0; i < points.size(); i++) {
            Instant windowEnd = points.get(i).getTimestamp().plus(SUSTAINED_SPEED_WINDOW);
            double heldSpeed = points.get(i).getSpeed();
            boolean windowFilled = false;
            for (int j = i + 1; j < points.size(); j++) {
                heldSpeed = Math.min(heldSpeed, points.get(j).getSpeed());
                if (!points.get(j).getTimestamp().isBefore(windowEnd)) {
                    windowFilled = true;
                    break;
                }
            }
            if (!windowFilled) {
                break; // Later readings are even closer to the end of the trip
            }
            sustainedMaxSpeed = Math.max(sustainedMaxSpeed, heldSpeed);
        }

        return sustainedMaxSpeed >= 0 ? sustainedMaxSpeed : rawMaxSpeed;
    }
}
