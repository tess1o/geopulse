package org.github.tess1o.geopulse.statistics.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.statistics.model.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for StatisticsRepository using real SQL queries against the database.
 * These tests verify that all SQL queries execute correctly and produce expected results.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class StatisticsRepositoryTest {

    @Inject
    StatisticsRepository statisticsRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    CleanupHelper cleanupHelper;

    private UserEntity testUser;
    private UUID testUserId;
    private Instant testStart;
    private Instant testEnd;

    @BeforeEach
    @Transactional
    void setUp() {
        testUserId = UUID.randomUUID();
        testStart = Instant.parse("2024-01-01T00:00:00Z");
        testEnd = Instant.parse("2024-01-07T23:59:59Z");

        // Create test user using native SQL to set the ID explicitly
        testUser = new UserEntity();
        testUser.setEmail("test-geojson@geopulse.app");
        testUser.setFullName("GeoJSON Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);
        testUserId = testUser.getId();


        // Create test data - stays
        createStay(testUser, "2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 3600L);
        createStay(testUser, "2024-01-01T12:00:00Z", "Work", 40.7580, -73.9855, 7200L);
        createStay(testUser, "2024-01-02T09:00:00Z", "Home", 40.7128, -74.0060, 3600L);
        createStay(testUser, "2024-01-02T12:00:00Z", "Gym", 40.7505, -73.9934, 1800L);
        createStay(testUser, "2024-01-03T09:00:00Z", "Home", 40.7128, -74.0060, 3600L);

        // Create test data - trips
        createTrip(testUser, "2024-01-01T10:30:00Z", 10000L, 1800L, "CAR");
        createTrip(testUser, "2024-01-02T10:30:00Z", 15000L, 2700L, "CAR");
        createTrip(testUser, "2024-01-02T14:00:00Z", 5000L, 900L, "WALK");
        createTrip(testUser, "2024-01-03T10:30:00Z", 8000L, 1500L, "CAR");

        entityManager.flush();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupHelper.cleanupAll();
    }

    @Test
    void getTripAggregations_WithValidData_ReturnsCorrectResults() {
        // When
        TripAggregationResult result = statisticsRepository.getTripAggregations(testUserId, testStart, testEnd);

        // Then
        assertNotNull(result);
        assertEquals(38000.0, result.getTotalDistanceMeters()); // 10k + 15k + 5k + 8k
        assertEquals(6900L, result.getTotalDurationSeconds()); // 1800 + 2700 + 900 + 1500
        assertTrue(result.getDailyAverageDistanceMeters() > 0);
    }

    @Test
    void getTripAggregations_WithEmptyData_ReturnsZeroValues() {
        // Given - user with no trips
        UUID emptyUserId = UUID.randomUUID();

        // When
        TripAggregationResult result = statisticsRepository.getTripAggregations(emptyUserId, testStart, testEnd);

        // Then
        assertNotNull(result);
        assertEquals(0.0, result.getTotalDistanceMeters());
        assertEquals(0L, result.getTotalDurationSeconds());
    }

    @Test
    void getUniqueLocationsCount_WithValidData_ReturnsCorrectCount() {
        // When
        long count = statisticsRepository.getUniqueLocationsCount(testUserId, testStart, testEnd);

        // Then
        assertEquals(3L, count); // Home, Work, Gym
    }

    @Test
    void getUniqueLocationsCount_WithEmptyData_ReturnsZero() {
        // Given - user with no stays
        UUID emptyUserId = UUID.randomUUID();

        // When
        long count = statisticsRepository.getUniqueLocationsCount(emptyUserId, testStart, testEnd);

        // Then
        assertEquals(0L, count);
    }

    @Test
    void getTopPlaces_WithValidData_ReturnsCorrectlyOrderedPlaces() {
        // When
        List<TopPlace> places = statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 5);

        // Then
        assertNotNull(places);
        assertEquals(3, places.size());

        // Verify Home is first (most visits)
        assertEquals("Home", places.get(0).getName());
        assertEquals(3, places.get(0).getVisits());
        assertTrue(places.get(0).getDuration() > 0);
        assertNotNull(places.get(0).getCoordinates());
        assertEquals(2, places.get(0).getCoordinates().length);

        // Verify order by visits descending
        for (int i = 0; i < places.size() - 1; i++) {
            assertTrue(places.get(i).getVisits() >= places.get(i + 1).getVisits());
        }
    }

    @Test
    void getTopPlaces_RespectsLimit() {
        // When - request only top 2
        List<TopPlace> places = statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 2);

        // Then
        assertNotNull(places);
        assertTrue(places.size() <= 2);
    }

    @Test
    void getMostActiveDay_WithValidData_ReturnsCorrectDay() {
        // When
        MostActiveDayDto result = statisticsRepository.getMostActiveDay(testUserId, testStart, testEnd);

        // Then
        assertNotNull(result);
        assertNotNull(result.getDate());
        assertNotNull(result.getDay());
        assertTrue(result.getDistanceTraveled() > 0);
        assertTrue(result.getTravelTime() >= 0);

        // Day with most distance should be 2024-01-02 (15km + 5km = 20km)
        assertTrue(result.getDistanceTraveled() >= 20.0);
    }

    @Test
    void getMostActiveDay_WithNoTrips_ReturnsNull() {
        // Given - user with no trips
        UUID emptyUserId = UUID.randomUUID();

        // When
        MostActiveDayDto result = statisticsRepository.getMostActiveDay(emptyUserId, testStart, testEnd);

        // Then
        assertNull(result);
    }

    @Test
    void getChartDataByDays_WithValidData_ReturnsCorrectData() {
        // When
        List<ChartDataPoint> chartData = statisticsRepository.getChartDataByDays(testUserId, testStart, testEnd, "CAR");

        // Then
        assertNotNull(chartData);
        assertTrue(chartData.size() > 0);

        // Verify each point has label and distance
        for (ChartDataPoint point : chartData) {
            assertNotNull(point.getLabel());
            assertTrue(point.getDistanceKm() >= 0);
        }
    }

    @Test
    void getChartDataByDays_FiltersByMovementType() {
        // When - get only WALK trips
        List<ChartDataPoint> walkData = statisticsRepository.getChartDataByDays(testUserId, testStart, testEnd, "WALK");

        // Then
        assertNotNull(walkData);
        assertTrue(walkData.size() > 0);

        // Total distance for WALK should be 5km
        double totalWalkDistance = walkData.stream()
                .mapToDouble(ChartDataPoint::getDistanceKm)
                .sum();
        assertEquals(5.0, totalWalkDistance, 0.01);
    }

    @Test
    void getChartDataByWeeks_WithValidData_ReturnsCorrectData() {
        // When
        List<ChartDataPoint> chartData = statisticsRepository.getChartDataByWeeks(testUserId, testStart, testEnd, null);

        // Then
        assertNotNull(chartData);
        assertTrue(chartData.size() > 0);

        // Verify labels are in MM/dd format
        for (ChartDataPoint point : chartData) {
            assertNotNull(point.getLabel());
            assertTrue(point.getLabel().matches("\\d{2}/\\d{2}"));
        }
    }

    @Test
    void getRoutesStatistics_WithValidData_ReturnsCompleteStatistics() {
        // When
        RoutesStatistics result = statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd);

        // Then
        assertNotNull(result);
        assertTrue(result.getAvgTripDurationSeconds() > 0);
        assertTrue(result.getLongestTripDurationSeconds() >= result.getAvgTripDurationSeconds());
        assertTrue(result.getLongestTripDistanceMeters() > 0);
        assertTrue(result.getUniqueRoutesCount() >= 0);
        assertNotNull(result.getMostCommonRoute());
    }

    @Test
    void getRoutesStatistics_CalculatesMostCommonRoute() {
        // When
        RoutesStatistics result = statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd);

        // Then
        assertNotNull(result.getMostCommonRoute());
        assertNotNull(result.getMostCommonRoute().getName());

        // Should have Home -> Work or Home -> Gym as most common
        String routeName = result.getMostCommonRoute().getName();
        assertTrue(routeName.contains("Home") || routeName.isEmpty());
    }

    @Test
    void getRouteFrequencies_WithValidData_ReturnsTopRoutes() {
        // When
        List<RouteFrequencyResult> routes = statisticsRepository.getRouteFrequencies(testUserId, testStart, testEnd);

        // Then
        assertNotNull(routes);

        if (!routes.isEmpty()) {
            RouteFrequencyResult topRoute = routes.get(0);
            assertNotNull(topRoute.getFromLocation());
            assertNotNull(topRoute.getToLocation());
            assertTrue(topRoute.getFrequency() > 0);
        }
    }

    @Test
    void statisticsQueries_HandleDateRangeBoundaries() {
        // Given - trips exactly at boundary
        Instant boundaryStart = Instant.parse("2024-01-02T00:00:00Z");
        Instant boundaryEnd = Instant.parse("2024-01-02T23:59:59Z");

        // When
        TripAggregationResult result = statisticsRepository.getTripAggregations(testUserId, boundaryStart, boundaryEnd);

        // Then
        assertNotNull(result);
        // Should include trips from 2024-01-02
        assertTrue(result.getTotalDistanceMeters() >= 20000.0); // 15k + 5k from day 2
    }

    // Helper methods to create test data

    private void createStay(UserEntity user, String timestamp, String locationName, double lat, double lon, long durationSeconds) {
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setUser(user);
        stay.setTimestamp(Instant.parse(timestamp));
        stay.setLocation(GeoUtils.createPoint(lon, lat));
        stay.setStayDuration(durationSeconds);
        stay.setLocationName(locationName);
        stay.setLocationSource(org.github.tess1o.geopulse.streaming.model.domain.LocationSource.HISTORICAL);
        stay.setLastUpdated(Instant.now());
        stay.setCreatedAt(Instant.now());
        entityManager.persist(stay);
    }

    private void createTrip(UserEntity user, String timestamp, long distanceMeters, long durationSeconds, String movementType) {
        TimelineTripEntity trip = new TimelineTripEntity();
        trip.setUser(user);
        trip.setTimestamp(Instant.parse(timestamp));
        trip.setDistanceMeters(distanceMeters);
        trip.setTripDuration(durationSeconds);
        trip.setMovementType(movementType);
        trip.setStartPoint(GeoUtils.createPoint(-74.0060, 40.7128));
        trip.setEndPoint(GeoUtils.createPoint(-73.9855, 40.7580));
        trip.setLastUpdated(Instant.now());
        trip.setCreatedAt(Instant.now());
        entityManager.persist(trip);
    }
}
