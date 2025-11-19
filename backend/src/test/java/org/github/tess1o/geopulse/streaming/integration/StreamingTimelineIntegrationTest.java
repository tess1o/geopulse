package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
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
    CleanupHelper cleanupHelper;

    @Inject
    EntityManager entityManager;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private UserEntity testUserEnabled;
    private UserEntity testUserDisabled;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupHelper.cleanupAll();
        // Create test user
        testUserEnabled = createTestUser("streaming-test-enabled@geopulse.app", true);
        testUserDisabled = createTestUser("streaming-test-disabled@geopulse.app", false);
        userRepository.persist(testUserEnabled);
        userRepository.persist(testUserDisabled);
    }

    @Test
    @Transactional
    void shouldGenerateStayAndTrip_WithRealGpsData() {
        // Create real GPS points: home stay, trip to office, office stay
        List<GpsPointEntity> allPoints = new ArrayList<>();

        // Home stay: 08:00-10:00 (stationary points every 5 minutes)
        List<GpsPointEntity> homePoints = createStationaryPoints(
                testUserEnabled, HOME_LAT, HOME_LON,
                "2024-08-15T08:00:00Z", "2024-08-15T10:00:00Z", 5
        );
        allPoints.addAll(homePoints);

        // Trip: 10:00-10:30 (moving points every 2 minutes)
        List<GpsPointEntity> tripPoints = createMovingPoints(
                testUserEnabled, HOME_LAT, HOME_LON, OFFICE_LAT, OFFICE_LON,
                "2024-08-15T10:01:00Z", "2024-08-15T10:30:00Z"
        );
        allPoints.addAll(tripPoints);

        // Office stay: 10:30-15:00 (stationary points every 10 minutes)
        List<GpsPointEntity> officePoints = createStationaryPoints(
                testUserEnabled, OFFICE_LAT, OFFICE_LON,
                "2024-08-15T10:31:00Z", "2024-08-15T15:00:00Z", 10
        );
        allPoints.addAll(officePoints);

        // Persist all GPS points
        for (GpsPointEntity point : allPoints) {
            gpsPointRepository.persist(point);
        }

        // Process timeline using streaming algorithm
        boolean success = streamingTimelineGenerationService.regenerateFullTimeline(testUserEnabled.getId());
        assertThat(success).isTrue();

        // Verify timeline structure
        var stays = timelineStayRepository.listAll();
        var trips = timelineTripRepository.listAll();
        assertThat(stays).hasSize(2);
        assertThat(trips).hasSize(1);
    }

    @Test
    @Transactional
    void shouldCreateDataGap_WhenOvernightWithNoData_AndInferenceDisabled() {
        // Given: Points before and after an overnight gap at the same location
        // But gap stay inference is disabled (default)
        List<GpsPointEntity> allPoints = new ArrayList<>();

        // Evening at home: 20:00-21:00 (points every 10 minutes)
        List<GpsPointEntity> eveningPoints = createStationaryPoints(
                testUserDisabled, HOME_LAT, HOME_LON,
                "2024-08-15T20:00:00Z", "2024-08-15T21:00:00Z", 10
        );
        allPoints.addAll(eveningPoints);

        // Next morning at home: 08:00-09:00 (same location after 11-hour gap)
        List<GpsPointEntity> morningPoints = createStationaryPoints(
                testUserDisabled, HOME_LAT, HOME_LON,
                "2024-08-16T08:00:00Z", "2024-08-16T09:00:00Z", 10
        );
        allPoints.addAll(morningPoints);

        // Persist all GPS points
        for (GpsPointEntity point : allPoints) {
            gpsPointRepository.persist(point);
        }

        // When: Process timeline (gap stay inference disabled by default)
        boolean success = streamingTimelineGenerationService.regenerateFullTimeline(testUserDisabled.getId());
        assertThat(success).isTrue();

        // Then: Should have data gap for the overnight period
        var stays = timelineStayRepository.listAll();
        var gaps = timelineDataGapRepository.listAll();

        // Should have two separate stays (evening and morning) with a gap between
        assertThat(stays).hasSize(2);

        // Filter gaps that occurred during the overnight period (before morning points)
        var overnightGaps = gaps.stream()
                .filter(g -> g.getStartTime().isBefore(java.time.Instant.parse("2024-08-16T08:00:00Z")))
                .toList();
        assertThat(overnightGaps).hasSize(1);

        // Verify the gap is for the overnight period
        var gap = overnightGaps.get(0);
        assertThat(gap.getDurationSeconds()).isGreaterThan(10 * 3600); // > 10 hours
    }

    @Test
    @Transactional
    void shouldInferStay_WhenOvernightWithNoData_AndInferenceEnabled() {
        // Given: Enable gap stay inference for this user
        TimelinePreferences prefs = TimelinePreferences.builder()
                .gapStayInferenceEnabled(true)
                .gapStayInferenceMaxGapHours(24)
                .build();
        testUserEnabled.setTimelinePreferences(prefs);
        // No need to persist - entity is already managed and changes will be flushed

        List<GpsPointEntity> allPoints = new ArrayList<>();

        // Evening at home: 20:00-21:00 (points every 10 minutes)
        List<GpsPointEntity> eveningPoints = createStationaryPoints(
                testUserEnabled, HOME_LAT, HOME_LON,
                "2024-08-15T20:00:00Z", "2024-08-15T21:00:00Z", 10
        );
        allPoints.addAll(eveningPoints);

        // Next morning at home: 08:00-09:00 (same location after 11-hour gap)
        List<GpsPointEntity> morningPoints = createStationaryPoints(
                testUserEnabled, HOME_LAT, HOME_LON,
                "2024-08-16T08:00:00Z", "2024-08-16T09:00:00Z", 10
        );
        allPoints.addAll(morningPoints);

        // Persist all GPS points
        for (GpsPointEntity point : allPoints) {
            gpsPointRepository.persist(point);
        }

        // Flush to ensure preferences are visible to the timeline generation transaction
        entityManager.flush();

        // When: Process timeline with gap stay inference enabled
        boolean success = streamingTimelineGenerationService.regenerateFullTimeline(testUserEnabled.getId());
        assertThat(success).isTrue();

        // Then: Should have single continuous stay spanning overnight
        var stays = timelineStayRepository.listAll();
        var gaps = timelineDataGapRepository.listAll();

        // Should have only ONE stay (no gap during overnight, continuous stay inferred)
        assertThat(stays).hasSize(1);

        // There may be an ongoing gap from last point to now, but no gap during overnight period
        // Filter gaps that start before morning points (08:00)
        var overnightGaps = gaps.stream()
                .filter(g -> g.getStartTime().isBefore(java.time.Instant.parse("2024-08-16T08:00:00Z")))
                .toList();
        assertThat(overnightGaps).isEmpty();

        // Verify the stay spans the full period (evening to morning)
        var stay = stays.get(0);
        long stayDurationHours = stay.getStayDuration() / 3600;
        assertThat(stayDurationHours).isGreaterThanOrEqualTo(12); // Should be ~13 hours
    }

    @Test
    @Transactional
    void shouldCreateGap_WhenDifferentLocation_EvenWithInferenceEnabled() {
        // Given: Enable gap stay inference
        TimelinePreferences prefs = TimelinePreferences.builder()
                .gapStayInferenceEnabled(true)
                .gapStayInferenceMaxGapHours(24)
                .build();
        testUserEnabled.setTimelinePreferences(prefs);
        // No need to persist - entity is already managed and changes will be flushed

        List<GpsPointEntity> allPoints = new ArrayList<>();

        // Evening at home: 20:00-21:00
        List<GpsPointEntity> eveningPoints = createStationaryPoints(
                testUserEnabled, HOME_LAT, HOME_LON,
                "2024-08-15T20:00:00Z", "2024-08-15T21:00:00Z", 10
        );
        allPoints.addAll(eveningPoints);

        // Next morning at OFFICE (different location after gap)
        List<GpsPointEntity> morningPoints = createStationaryPoints(
                testUserEnabled, OFFICE_LAT, OFFICE_LON,
                "2024-08-16T08:00:00Z", "2024-08-16T09:00:00Z", 10
        );
        allPoints.addAll(morningPoints);

        // Persist all GPS points
        for (GpsPointEntity point : allPoints) {
            gpsPointRepository.persist(point);
        }

        // Flush to ensure preferences are visible to the timeline generation transaction
        entityManager.flush();

        // When: Process timeline
        boolean success = streamingTimelineGenerationService.regenerateFullTimeline(testUserEnabled.getId());
        assertThat(success).isTrue();

        // Then: Should create gap because locations are different
        var stays = timelineStayRepository.listAll();
        var gaps = timelineDataGapRepository.listAll();

        // Should have two stays (home evening, office morning) with gap between them
        assertThat(stays).hasSize(2);

        // Filter gaps that occurred during the overnight period (before morning points)
        var overnightGaps = gaps.stream()
                .filter(g -> g.getStartTime().isBefore(java.time.Instant.parse("2024-08-16T08:00:00Z")))
                .toList();
        // Should have exactly 1 gap during overnight (different locations = no inference)
        assertThat(overnightGaps).hasSize(1);
    }

    // Helper methods for creating test data

    private UserEntity createTestUser(String email, boolean enabled) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setFullName("Test User");
        user.setPasswordHash("test");

        if (enabled) {
            TimelinePreferences prefs = TimelinePreferences.builder()
                    .gapStayInferenceEnabled(true)
                    .gapStayInferenceMaxGapHours(24)
                    .build();
            user.setTimelinePreferences(prefs);
        }

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