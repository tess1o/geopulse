package org.github.tess1o.geopulse.service;

import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.trips.GpsStatisticsCalculator;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.streaming.service.trips.TripGpsStatistics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimelineTripClassificationTest {

    private static final TravelClassification classification = new TravelClassification();
    private static final GpsStatisticsCalculator gpsStatisticsCalculator = new GpsStatisticsCalculator();

    private static final TimelineConfig config = TimelineConfig.builder()
            .staypointRadiusMeters(200)
            .carMinAvgSpeed(7.0)
            .carMinMaxSpeed(15.0)
            .walkingMaxAvgSpeed(4.0)
            .walkingMaxMaxSpeed(7.0)
            .shortDistanceKm(1.0)
            .build();

    // Helper to create LocationPoint with relative seconds offset
    private GPSPoint point(double lon, double lat, long secondsOffset, double speed) {
        return new GPSPoint(lat, lon, speed, 0, Instant.parse("2025-01-01T00:00:00Z").plusSeconds(secondsOffset));
    }

    @Test
    void testWalkingClassification() {
        List<GPSPoint> path = List.of(
                point(0.0, 0.0, 0, 0),             // start
                point(0.0, 0.00030, 30, 1.1),        // ~33 m (4 km/h)
                point(0.0, 0.00068, 60, 1.38),        // ~42 m (5 km/h)
                point(0.0, 0.00110, 90, 1.45),        // ~47 m (5.6 km/h)
                point(0.0, 0.00145, 120, 1.29),       // ~39 m (4.6 km/h)
                point(0.0, 0.00188, 150, 1.5),       // ~48 m (5.8 km/h)
                point(0.0, 0.00220, 180, 1.1),       // ~34 m (4.1 km/h)
                point(0.0, 0.00260, 210, 1.4),       // ~44 m (5.3 km/h)
                point(0.0, 0.00290, 240, 1.1),       // ~33 m (4 km/h)
                point(0.0, 0.00330, 270, 1.3),       // ~44 m (5.3 km/h)
                point(0.0, 0.00365, 300, 1.4)        // ~39 m (4.6 km/h)
        );

        TripGpsStatistics statistics = gpsStatisticsCalculator.calculateStatistics(path);
        double tripDistance = calculateTripDistance(path);
        TripType tripType = classification.classifyTravelType(statistics, Double.valueOf(tripDistance).longValue(), config);

        assertEquals(TripType.WALK, tripType);
    }

    @Test
    void testCarClassification() {
        List<GPSPoint> path = List.of(
                point(0.0, 0.0, 0, 0),
                point(0.0, 0.01, 30, 37),  // ~1.11 km in 30s → 133 km/h (too fast, filtered out)
                point(0.0, 0.005, 90, 11), // ~555 m in 60s → 33 km/h (valid)
                point(0.0, 0.02, 180, 22)  // ~1.66 km in 90s → 66 km/h
        );
        TripGpsStatistics statistics = gpsStatisticsCalculator.calculateStatistics(path);
        double tripDistance = calculateTripDistance(path);
        TripType tripType = classification.classifyTravelType(statistics, Double.valueOf(tripDistance).longValue(), config);

        assertEquals(TripType.CAR, tripType);
    }

    private double calculateTripDistance(List<? extends GpsPoint> path) {
        if (path == null || path.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 1; i < path.size(); i++) {
            GpsPoint p1 = path.get(i - 1);
            GpsPoint p2 = path.get(i);
            totalDistance += GeoUtils.haversine(p1.getLatitude(), p1.getLongitude(),
                    p2.getLatitude(), p2.getLongitude());
        }

        return totalDistance / 1000.0; // Convert meters to kilometers
    }

}
