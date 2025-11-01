package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class StreamingTimelineIntegrationTest {

    private static final double HOME_LAT = 40.7589;
    private static final double HOME_LON = -73.9851;
    private static final double OFFICE_LAT = 40.7505;
    private static final double OFFICE_LON = -73.9934;

    @Inject
    StreamingTimelineGenerationService streamingTimelineGenerationService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up previous test data
        gpsSourceRepository.deleteAll();
        timelineDataGapRepository.deleteAll();
        timelineTripRepository.deleteAll();
        timelineStayRepository.deleteAll();
        gpsPointRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = createTestUser("streaming-test@geopulse.app");
        userRepository.persist(testUser);
    }

    @Test
    @Transactional
    void shouldGenerateStayAndTrip_WithRealGpsData() {
        // Create real GPS points: home stay, trip to office, office stay
        List<GpsPointEntity> allPoints = new ArrayList<>();

        // Home stay: 08:00-10:00 (stationary points every 5 minutes)
        List<GpsPointEntity> homePoints = createStationaryPoints(
            testUser, HOME_LAT, HOME_LON,
            "2024-08-15T08:00:00Z", "2024-08-15T10:00:00Z", 5
        );
        allPoints.addAll(homePoints);

        // Trip: 10:00-10:30 (moving points every 2 minutes)
        List<GpsPointEntity> tripPoints = createMovingPoints(
            testUser, HOME_LAT, HOME_LON, OFFICE_LAT, OFFICE_LON,
            "2024-08-15T10:01:00Z", "2024-08-15T10:30:00Z"
        );
        allPoints.addAll(tripPoints);

        // Office stay: 10:30-15:00 (stationary points every 10 minutes)
        List<GpsPointEntity> officePoints = createStationaryPoints(
            testUser, OFFICE_LAT, OFFICE_LON,
            "2024-08-15T10:31:00Z", "2024-08-15T15:00:00Z", 10
        );
        allPoints.addAll(officePoints);

        // Persist all GPS points
        for (GpsPointEntity point : allPoints) {
            gpsPointRepository.persist(point);
        }

        // Process timeline using streaming algorithm
        boolean success = streamingTimelineGenerationService.regenerateFullTimeline(testUser.getId());
        assertThat(success).isTrue();

        // Verify timeline structure
        var stays = timelineStayRepository.listAll();
        var trips = timelineTripRepository.listAll();
        assertThat(stays).hasSize(2);
        assertThat(trips).hasSize(1);
    }

    // Helper methods for creating test data

    private UserEntity createTestUser(String email) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setFullName("Test User");
        user.setPasswordHash("test");
        return user;
    }

    private List<GpsPointEntity> createStationaryPoints(UserEntity user, double lat, double lon,
                                                       String startTime, String endTime, int intervalMinutes) {
        List<GpsPointEntity> points = new ArrayList<>();
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);

        Instant current = start;
        while (current.isBefore(end) || current.equals(end)) {
            GpsPointEntity point = new GpsPointEntity();
            point.setUser(user);
            point.setTimestamp(current);
            point.setCoordinates(createPoint(lon, lat)); // PostGIS uses lon, lat order
            point.setAccuracy(5.0);
            point.setVelocity(0.0); // Stationary

            points.add(point);
            current = current.plusSeconds(intervalMinutes * 60L);
        }

        return points;
    }

    private List<GpsPointEntity> createMovingPoints(UserEntity user,
                                                   double startLat, double startLon,
                                                   double endLat, double endLon,
                                                   String startTime, String endTime) {
        List<GpsPointEntity> points = new ArrayList<>();
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);

        long totalMinutes = java.time.Duration.between(start, end).toMinutes();
        int numPoints = Math.max(2, (int) (totalMinutes / 2)); // Point every 2 minutes

        for (int i = 0; i < numPoints; i++) {
            double progress = (double) i / (numPoints - 1);
            double lat = startLat + (endLat - startLat) * progress;
            double lon = startLon + (endLon - startLon) * progress;

            Instant timestamp = start.plusSeconds((long) (totalMinutes * 60 * progress));

            GpsPointEntity point = new GpsPointEntity();
            point.setUser(user);
            point.setTimestamp(timestamp);
            point.setCoordinates(createPoint(lon, lat));
            point.setAccuracy(8.0);
            point.setVelocity(10.0); // Moving at 10 m/s

            points.add(point);
        }

        return points;
    }

    private Point createPoint(double lon, double lat) {
        return geometryFactory.createPoint(new Coordinate(lon, lat));
    }
}