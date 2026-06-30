package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Calculates water-surface evidence for trips using cached GPS point environment data.
 */
@ApplicationScoped
@Slf4j
public class TripWaterClassificationService {

    private static final double MIN_USABLE_SEGMENT_DISTANCE_METERS = 5.0;
    private static final double MAX_USABLE_SEGMENT_DISTANCE_METERS = 10_000.0;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    GpsPointEnvironmentService gpsPointEnvironmentService;

    public TripWaterStatistics calculateStatistics(UUID userId, Instant startTime, Instant endTime, TimelineConfig config) {
        if (gpsPointRepository == null || userId == null || startTime == null || endTime == null) {
            return TripWaterStatistics.unavailable();
        }
        if (!isBoatEnabled(config) || gpsPointEnvironmentService == null) {
            return TripWaterStatistics.unavailable();
        }

        String environmentDatasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
        if (environmentDatasetVersion == null) {
            return TripWaterStatistics.unavailable();
        }

        return calculateStatistics(
                gpsPointRepository.findEssentialPointsInInterval(userId, startTime, endTime, environmentDatasetVersion),
                config
        );
    }

    public TripWaterStatistics calculateStatistics(List<GPSPoint> gpsPoints, TimelineConfig config) {
        if (!isBoatEnabled(config) || gpsPoints == null || gpsPoints.size() < 2) {
            return TripWaterStatistics.unavailable();
        }

        double usableDistanceMeters = 0.0;
        double waterDistanceMeters = 0.0;
        double currentWaterSegmentMeters = 0.0;
        double longestWaterSegmentMeters = 0.0;
        int sampleCount = 0;

        for (int i = 1; i < gpsPoints.size(); i++) {
            GPSPoint previous = gpsPoints.get(i - 1);
            GPSPoint current = gpsPoints.get(i);
            double segmentDistanceMeters = previous.distanceTo(current);

            if (segmentDistanceMeters < MIN_USABLE_SEGMENT_DISTANCE_METERS
                    || segmentDistanceMeters > MAX_USABLE_SEGMENT_DISTANCE_METERS) {
                currentWaterSegmentMeters = 0.0;
                continue;
            }

            if (previous.getOnWater() == null || current.getOnWater() == null) {
                log.debug("Water evidence unavailable because GPS point environment data is missing");
                return TripWaterStatistics.unavailable();
            }

            boolean onWater = Boolean.TRUE.equals(previous.getOnWater())
                    && Boolean.TRUE.equals(current.getOnWater());

            usableDistanceMeters += segmentDistanceMeters;
            sampleCount++;

            if (onWater) {
                waterDistanceMeters += segmentDistanceMeters;
                currentWaterSegmentMeters += segmentDistanceMeters;
                longestWaterSegmentMeters = Math.max(longestWaterSegmentMeters, currentWaterSegmentMeters);
            } else {
                currentWaterSegmentMeters = 0.0;
            }
        }

        if (sampleCount == 0 || usableDistanceMeters <= 0.0) {
            return TripWaterStatistics.unavailable();
        }

        return new TripWaterStatistics(
                waterDistanceMeters,
                waterDistanceMeters / usableDistanceMeters,
                longestWaterSegmentMeters,
                sampleCount,
                true
        );
    }

    private boolean isBoatEnabled(TimelineConfig config) {
        return config != null && Boolean.TRUE.equals(config.getBoatEnabled());
    }

}
