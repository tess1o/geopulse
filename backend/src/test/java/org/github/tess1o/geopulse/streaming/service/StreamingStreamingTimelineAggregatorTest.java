package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
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
class StreamingStreamingTimelineAggregatorTest {

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

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up previous test data
        timelineDataGapRepository.deleteAll();
        timelineTripRepository.deleteAll();
        timelineStayRepository.deleteAll();
        gpsPointRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("service-test@geopulse.app");
        testUser.setFullName("Service Test User");
        testUser.setPasswordHash("test");
        userRepository.persist(testUser);
    }

    @Test
    @Transactional
    void shouldRegenerateFullTimeline() {
        // Create GPS data spanning multiple days
        createMultiDayGpsData();

        // Regenerate full timeline
        boolean success = streamingTimelineGenerationService.regenerateFullTimeline(testUser.getId());

        assertThat(success).isTrue();

        // Should have timeline data for all days with GPS data
        var allStays = timelineStayRepository.listAll();
        assertThat(allStays).isNotEmpty();

        // Should span multiple days
        var timestamps = allStays.stream().map(stay -> stay.getTimestamp()).toList();
        var minTime = timestamps.stream().min(Instant::compareTo).orElse(Instant.EPOCH);
        var maxTime = timestamps.stream().max(Instant::compareTo).orElse(Instant.EPOCH);

        assertThat(java.time.Duration.between(minTime, maxTime).toDays()).isGreaterThan(0);
    }

    // Helper methods for creating test data

    private void createMultiDayGpsData() {
        List<GpsPointEntity> allPoints = new ArrayList<>();

        // Day 1: Home stay
        allPoints.addAll(createStationaryPoints(40.7589, -73.9851,
            "2024-08-15T08:00:00Z", "2024-08-15T18:00:00Z", 60));

        // Day 2: Office stay
        allPoints.addAll(createStationaryPoints(40.7505, -73.9934,
            "2024-08-16T09:00:00Z", "2024-08-16T17:00:00Z", 60));

        for (GpsPointEntity point : allPoints) {
            gpsPointRepository.persist(point);
        }
    }

    private List<GpsPointEntity> createStationaryPoints(double lat, double lon,
                                                       String startTime, String endTime,
                                                       int intervalMinutes) {
        List<GpsPointEntity> points = new ArrayList<>();
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);

        Instant current = start;
        while (current.isBefore(end) || current.equals(end)) {
            GpsPointEntity point = new GpsPointEntity();
            point.setUser(testUser);
            point.setTimestamp(current);
            point.setCoordinates(createPoint(lon, lat));
            point.setAccuracy(5.0);
            point.setVelocity(0.0);

            points.add(point);
            current = current.plusSeconds(intervalMinutes * 60L);
        }

        return points;
    }

    private Point createPoint(double lon, double lat) {
        return geometryFactory.createPoint(new Coordinate(lon, lat));
    }
}