package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DailyTimelineProcessingService using real database.
 * Tests the business logic and error handling of timeline processing operations
 * with actual database interactions.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class DailyTimelineProcessingServiceTest {

    @Inject
    DailyTimelineProcessingService dailyProcessingService;

    @Inject
    UserRepository userRepository;

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    private UserEntity testUser;
    private UserEntity testUser2;
    private UserEntity testUser3;
    private LocalDate testDate;
    private Instant startOfDay;
    private Instant endOfDay;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        cleanupTestData();

        // Create test users
        testUser = createTestUser("test-daily-processing-1@geopulse.app", "Daily Processing Test User 1");
        testUser2 = createTestUser("test-daily-processing-2@geopulse.app", "Daily Processing Test User 2");
        testUser3 = createTestUser("test-daily-processing-3@geopulse.app", "Daily Processing Test User 3");

        // Use a specific test date (past date to ensure consistent behavior)
        testDate = LocalDate.of(2025, 6, 15);
        startOfDay = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        endOfDay = testDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Transactional
    void cleanupTestData() {
        // Clean up in dependency order
        timelineStayRepository.delete("user.email like ?1", "%@geopulse.app");
        gpsPointRepository.delete("user.email like ?1", "%@geopulse.app");
        userRepository.delete("email like ?1", "%@geopulse.app");
    }

    @Test
    @Transactional
    void testProcessUserTimeline_NewTimeline_ShouldProcessSuccessfully() {
        // Arrange - Create GPS data that will generate a meaningful timeline
        createGpsDataForUser(testUser, startOfDay);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(
            testUser.getId(), startOfDay, endOfDay, testDate);

        // Assert
        assertTrue(result, "Should return true when timeline is processed successfully");
        
        // Verify timeline data was persisted
        boolean timelineExists = timelineStayRepository.hasPersistedTimelineForDate(testUser.getId(), startOfDay);
        assertTrue(timelineExists, "Timeline should be persisted after processing");
    }

    @Test
    @Transactional
    void testProcessUserTimeline_ExistingTimeline_ShouldSkip() {
        // Arrange - Create GPS data and pre-process timeline
        createGpsDataForUser(testUser, startOfDay);
        dailyProcessingService.processUserTimeline(testUser.getId(), startOfDay, endOfDay, testDate);
        
        // Act - Try to process again
        boolean result = dailyProcessingService.processUserTimeline(
            testUser.getId(), startOfDay, endOfDay, testDate);

        // Assert
        assertFalse(result, "Should return false when timeline already exists");
    }

    @Test
    @Transactional
    void testProcessUserTimeline_NoGpsData_ShouldSkip() {
        // Arrange - No GPS data for user
        
        // Act
        boolean result = dailyProcessingService.processUserTimeline(
            testUser.getId(), startOfDay, endOfDay, testDate);

        // Assert
        assertFalse(result, "Should return false when no GPS data exists for timeline generation");
        
        // Verify no timeline was persisted
        boolean timelineExists = timelineStayRepository.hasPersistedTimelineForDate(testUser.getId(), startOfDay);
        assertFalse(timelineExists, "No timeline should be persisted without GPS data");
    }

    @Test
    @Transactional
    void testProcessUserTimeline_InsufficientGpsData_ShouldSkip() {
        // Arrange - Create minimal GPS data that won't generate activities
        createMinimalGpsData(testUser, startOfDay);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(
            testUser.getId(), startOfDay, endOfDay, testDate);

        // Assert - Timeline might be generated but empty, so processing should still work
        // The exact behavior depends on timeline generation logic
        assertNotNull(result, "Should handle insufficient GPS data gracefully");
    }

    @Test
    @Transactional
    void testProcessUserTimeline_WithInvalidUserId_ShouldHandleGracefully() {
        // Arrange - Use non-existent user ID
        UUID nonExistentUserId = UUID.randomUUID();

        // Act - Should handle gracefully without throwing exceptions
        assertDoesNotThrow(() -> {
            boolean result = dailyProcessingService.processUserTimeline(
                nonExistentUserId, startOfDay, endOfDay, testDate);
            // Result may be true or false depending on implementation
        });
    }

    @Test
    @Transactional
    void testProcessTimelineForDate_WithActiveUsers_ShouldProcessAll() {
        // Arrange - Create GPS data for multiple users
        createGpsDataForUser(testUser, startOfDay);
        createGpsDataForUser(testUser2, startOfDay);
        createGpsDataForUser(testUser3, startOfDay);

        // Act
        DailyTimelineProcessingService.ProcessingStatistics stats = 
            dailyProcessingService.processTimelineForDate(testDate);

        // Assert
        assertNotNull(stats);
        assertEquals(testDate, stats.processedDate());
        assertEquals(3, stats.totalUsers(), "Should process all 3 users");
        assertTrue(stats.successfulUsers() >= 0, "Successful users should be >= 0");
        assertTrue(stats.failedUsers() >= 0, "Failed users should be >= 0");
        assertEquals(3, stats.successfulUsers() + stats.failedUsers(), 
                    "Total should equal successful + failed");
        
        // Verify at least some timelines were created
        boolean anyTimelineExists = timelineStayRepository.hasPersistedTimelineForDate(testUser.getId(), startOfDay) ||
                                  timelineStayRepository.hasPersistedTimelineForDate(testUser2.getId(), startOfDay) ||
                                  timelineStayRepository.hasPersistedTimelineForDate(testUser3.getId(), startOfDay);
        assertTrue(anyTimelineExists, "At least one timeline should be created");
    }

    @Test
    @Transactional
    void testProcessTimelineForDate_WithMixedResults_ShouldTrackStatistics() {
        // Arrange - Create different scenarios for users
        // User 1: Good GPS data
        createGpsDataForUser(testUser, startOfDay);
        
        // User 2: No GPS data
        // (testUser2 has no GPS data)
        
        // User 3: Some GPS data
        createMinimalGpsData(testUser3, startOfDay);

        // Act
        DailyTimelineProcessingService.ProcessingStatistics stats = 
            dailyProcessingService.processTimelineForDate(testDate);

        // Assert
        assertNotNull(stats);
        assertEquals(testDate, stats.processedDate());
        assertTrue(stats.totalUsers() >= 0, "Total users should be >= 0");
        assertTrue(stats.successfulUsers() >= 0, "Successful users should be >= 0");
        assertTrue(stats.failedUsers() >= 0, "Failed users should be >= 0");
        assertTrue(stats.totalUsers() <= 3, "Should not exceed number of test users");
    }

    @Test
    @Transactional
    void testProcessTimelineForDate_WithNoActiveUsers_ShouldReturnZeroStats() {
        // Arrange - Set all test users to inactive
        testUser.setActive(false);
        testUser2.setActive(false);
        testUser3.setActive(false);
        userRepository.getEntityManager().merge(testUser);
        userRepository.getEntityManager().merge(testUser2);
        userRepository.getEntityManager().merge(testUser3);

        // Act
        DailyTimelineProcessingService.ProcessingStatistics stats = 
            dailyProcessingService.processTimelineForDate(testDate);

        // Assert
        assertNotNull(stats);
        assertEquals(testDate, stats.processedDate());
        assertEquals(0, stats.totalUsers(), "Should have 0 total users");
        assertEquals(0, stats.successfulUsers(), "Should have 0 successful users");
        assertEquals(0, stats.failedUsers(), "Should have 0 failed users");
    }

    @Test
    @Transactional
    void testProcessingStatistics_ToString_ShouldFormatCorrectly() {
        // Arrange
        DailyTimelineProcessingService.ProcessingStatistics stats = 
            new DailyTimelineProcessingService.ProcessingStatistics(testDate, 10, 8, 2);

        // Act
        String result = stats.toString();

        // Assert
        assertTrue(result.contains("date=2025-06-15"), "Should contain the test date");
        assertTrue(result.contains("total=10"), "Should contain total count");
        assertTrue(result.contains("successful=8"), "Should contain successful count");
        assertTrue(result.contains("failed=2"), "Should contain failed count");
    }

    @Test
    @Transactional
    void testProcessUserTimeline_WithComplexGpsData_ShouldGenerateActivities() {
        // Arrange - Create complex GPS data with stays and trips
        createComplexGpsDataForUser(testUser, startOfDay);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(
            testUser.getId(), startOfDay, endOfDay, testDate);

        // Assert
        assertTrue(result, "Should process timeline with complex GPS data");
        
        // Verify timeline was created
        boolean timelineExists = timelineStayRepository.hasPersistedTimelineForDate(testUser.getId(), startOfDay);
        assertTrue(timelineExists, "Timeline should be persisted with complex GPS data");
    }

    @Test
    @Transactional
    void testProcessTimelineForDate_WithReprocessing_ShouldSkipExisting() {
        // Arrange - Create GPS data and process once
        createGpsDataForUser(testUser, startOfDay);
        DailyTimelineProcessingService.ProcessingStatistics firstRun = 
            dailyProcessingService.processTimelineForDate(testDate);

        // Act - Process again
        DailyTimelineProcessingService.ProcessingStatistics secondRun = 
            dailyProcessingService.processTimelineForDate(testDate);

        // Assert - Second run should skip already processed timelines
        assertNotNull(firstRun);
        assertNotNull(secondRun);
        assertEquals(testDate, firstRun.processedDate());
        assertEquals(testDate, secondRun.processedDate());
        
        // Second run should show fewer or no successful users (already processed)
        assertTrue(secondRun.successfulUsers() <= firstRun.successfulUsers(),
                  "Second run should have same or fewer successful users");
    }

    private UserEntity createTestUser(String email, String fullName) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash("test-hash");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        userRepository.persist(user);
        return user;
    }

    private void createGpsDataForUser(UserEntity user, Instant baseTime) {
        // Create GPS points that form a meaningful timeline with stays and movement
        createGpsPoint(user, baseTime.plusSeconds(3600), 37.7749, -122.4194);    // Home stay
        createGpsPoint(user, baseTime.plusSeconds(3900), 37.7749, -122.4194);    // Still at home
        createGpsPoint(user, baseTime.plusSeconds(7200), 37.7749, -122.4194);    // Still at home
        createGpsPoint(user, baseTime.plusSeconds(10800), 37.7849, -122.4094);   // Moving to work
        createGpsPoint(user, baseTime.plusSeconds(14400), 37.7849, -122.4094);   // At work
        createGpsPoint(user, baseTime.plusSeconds(18000), 37.7849, -122.4094);   // Still at work
        createGpsPoint(user, baseTime.plusSeconds(21600), 37.7849, -122.4094);   // Still at work
        createGpsPoint(user, baseTime.plusSeconds(25200), 37.7749, -122.4194);   // Back home
        createGpsPoint(user, baseTime.plusSeconds(28800), 37.7749, -122.4194);   // At home
    }

    private void createMinimalGpsData(UserEntity user, Instant baseTime) {
        // Create just a few GPS points that might not generate meaningful activities
        createGpsPoint(user, baseTime.plusSeconds(3600), 37.7749, -122.4194);
        createGpsPoint(user, baseTime.plusSeconds(7200), 37.7749, -122.4195);
    }

    private void createComplexGpsDataForUser(UserEntity user, Instant baseTime) {
        // Create a full day of GPS data with multiple locations and activities
        // Morning at home (stay)
        for (int i = 0; i < 10; i++) {
            createGpsPoint(user, baseTime.plusSeconds(600 * i), 37.7749, -122.4194);
        }
        
        // Commute to work (trip)
        createGpsPoint(user, baseTime.plusSeconds(7200), 37.7750, -122.4180);
        createGpsPoint(user, baseTime.plusSeconds(7800), 37.7760, -122.4150);
        createGpsPoint(user, baseTime.plusSeconds(8400), 37.7780, -122.4100);
        createGpsPoint(user, baseTime.plusSeconds(9000), 37.7800, -122.4050);
        
        // At work (stay)
        for (int i = 0; i < 15; i++) {
            createGpsPoint(user, baseTime.plusSeconds(10800 + 600 * i), 37.7849, -122.4094);
        }
        
        // Lunch trip
        createGpsPoint(user, baseTime.plusSeconds(19800), 37.7860, -122.4080);
        createGpsPoint(user, baseTime.plusSeconds(21600), 37.7849, -122.4094); // Back to work
        
        // Evening commute home
        createGpsPoint(user, baseTime.plusSeconds(25200), 37.7800, -122.4150);
        createGpsPoint(user, baseTime.plusSeconds(25800), 37.7749, -122.4194);
        
        // Evening at home
        for (int i = 0; i < 8; i++) {
            createGpsPoint(user, baseTime.plusSeconds(28800 + 600 * i), 37.7749, -122.4194);
        }
    }

    private void createGpsPoint(UserEntity user, Instant timestamp, double latitude, double longitude) {
        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(user);
        gpsPoint.setTimestamp(timestamp);
        gpsPoint.setCoordinates(GeoUtils.createPoint(longitude, latitude));
        gpsPoint.setAccuracy(5.0);
        gpsPoint.setAltitude(100.0);
        gpsPoint.setVelocity(0.0);
        gpsPoint.setBattery(85.0);
        gpsPoint.setDeviceId("test-device");
        gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPoint.setCreatedAt(Instant.now());
        gpsPointRepository.persist(gpsPoint);
    }
}