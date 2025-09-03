package org.github.tess1o.geopulse.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimelineTripClassificationTest {

    private static final TravelClassification classification = new TravelClassification(
        new VelocityAnalysisService()
    );

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static final class LocationPoint implements GpsPoint {
        private double longitude;
        private double latitude;
        private Instant timestamp;
    }

    // Helper to create LocationPoint with relative seconds offset
    private LocationPoint point(double lon, double lat, long secondsOffset) {
        return new LocationPoint(lon, lat, Instant.parse("2025-01-01T00:00:00Z").plusSeconds(secondsOffset));
    }

    @Test
    void testWalkingClassification() {
        List<LocationPoint> path = List.of(
                point(0.0, 0.0, 0),             // start
                point(0.0, 0.00030, 30),        // ~33 m (4 km/h)
                point(0.0, 0.00068, 60),        // ~42 m (5 km/h)
                point(0.0, 0.00110, 90),        // ~47 m (5.6 km/h)
                point(0.0, 0.00145, 120),       // ~39 m (4.6 km/h)
                point(0.0, 0.00188, 150),       // ~48 m (5.8 km/h)
                point(0.0, 0.00220, 180),       // ~34 m (4.1 km/h)
                point(0.0, 0.00260, 210),       // ~44 m (5.3 km/h)
                point(0.0, 0.00290, 240),       // ~33 m (4 km/h)
                point(0.0, 0.00330, 270),       // ~44 m (5.3 km/h)
                point(0.0, 0.00365, 300)        // ~39 m (4.6 km/h)
        );

        Duration duration = Duration.between(path.getFirst().getTimestamp(), path.getLast().getTimestamp());
        TimelineConfig config = TimelineConfig.builder()
                .staypointRadiusMeters(200)
                .build();
        TripType tripType = classification.classifyTravelType(path, duration, config);

        assertEquals(TripType.WALK, tripType);
    }

    @Test
    void testCarClassification() {
        List<LocationPoint> path = List.of(
                point(0.0, 0.0, 0),
                point(0.0, 0.01, 30),  // ~1.11 km in 30s → 133 km/h (too fast, filtered out)
                point(0.0, 0.005, 90), // ~555 m in 60s → 33 km/h (valid)
                point(0.0, 0.02, 180)  // ~1.66 km in 90s → 66 km/h
        );
        Duration duration = Duration.between(path.getFirst().getTimestamp(), path.getLast().getTimestamp());
        TimelineConfig config = TimelineConfig.builder()
                .staypointRadiusMeters(200)
                .build();
        TripType tripType = classification.classifyTravelType(path, duration, config);

        assertEquals(TripType.CAR, tripType);
    }

    @Test
    void testUnknownClassificationForShortTrips() {
        List<LocationPoint> path = List.of(
                point(0.0, 0.0, 0),
                point(0.0, 0.000001, 100)  // ~0.1 m in 1s → too short distance and duration
        );
        Duration duration = Duration.between(path.getFirst().getTimestamp(), path.getLast().getTimestamp());
        TimelineConfig config = TimelineConfig.builder()
                .staypointRadiusMeters(200)
                .build();
        TripType tripType = classification.classifyTravelType(path, duration, config);

        assertEquals(TripType.UNKNOWN, tripType);
    }

    @Test
    void testWalkingClassificationForShortTripWithSlightlyHighSpeed() {
        // Create points roughly 1.4 km apart over ~15 minutes (0.093 hours)
        // avg speed = 1.4 / 0.25 = 5.6 km/h (just below WALKING_MAX_AVG_SPEED + delta)
        List<LocationPoint> path = List.of(
                point(0.0, 0.0, 0),
                point(0.0, 0.0126, 900)  // ~1.4 km north in 15 minutes (900 seconds)
        );
        Duration duration = Duration.between(path.getFirst().getTimestamp(), path.getLast().getTimestamp());
        TimelineConfig config = TimelineConfig.builder()
                .staypointRadiusMeters(200)
                .build();
        TripType tripType = classification.classifyTravelType(path, duration, config);

        assertEquals(TripType.WALK, tripType);
    }
}
