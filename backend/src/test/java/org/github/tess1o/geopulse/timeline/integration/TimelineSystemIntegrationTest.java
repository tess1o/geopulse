package org.github.tess1o.geopulse.timeline.integration;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.timeline.assembly.TimelineDataService;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.service.TimelineCacheService;
import org.github.tess1o.geopulse.timeline.service.TimelineQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete timeline system.
 * Tests the end-to-end workflow of the new simplified timeline system.
 */
@QuarkusTest
@Transactional
class TimelineSystemIntegrationTest {

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    TimelineCacheService cacheService;

    @Inject
    TimelineService timelineGenerationService;

    @Inject
    TimelineDataService timelineDataService;

    @Inject
    org.github.tess1o.geopulse.user.repository.UserRepository userRepository;

    @AfterEach
    @Transactional
    void cleanupTestUsers() {
        // Clean up test users created during tests
        userRepository.delete("email LIKE ?1", "test-%@geopulse.app");
    }

    @Test
    @Transactional
    void testTodayTimeline_ShouldAlwaysGenerateLive() {
        // Arrange - Create a test user first
        org.github.tess1o.geopulse.user.model.UserEntity testUser = new org.github.tess1o.geopulse.user.model.UserEntity();
        testUser.setEmail("test-today@geopulse.app");
        testUser.setFullName("Today Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(java.time.Instant.now());
        userRepository.persist(testUser);
        
        UUID userId = testUser.getId();
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = todayStart.plusSeconds(86400 - 1);

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(userId, todayStart, todayEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertEquals(userId, result.getUserId());
    }

    @Test
    @Transactional
    void testPastTimeline_WithoutCache_WithoutGPS_ShouldReturnEmpty() {
        // Arrange - Create a test user first
        org.github.tess1o.geopulse.user.model.UserEntity testUser = new org.github.tess1o.geopulse.user.model.UserEntity();
        testUser.setEmail("test-past@geopulse.app");
        testUser.setFullName("Past Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(java.time.Instant.now());
        userRepository.persist(testUser);
        
        UUID userId = testUser.getId();
        Instant yesterdayStart = LocalDate.now(ZoneOffset.UTC).minusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant yesterdayEnd = yesterdayStart.plusSeconds(86400 - 1);

        // Mock no GPS data (empty path)
        // This test assumes we're testing with a user that has no GPS data

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(userId, yesterdayStart, yesterdayEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertEquals(userId, result.getUserId());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
    }

    @Test
    @Transactional
    void testPastTimeline_WithCache_ShouldReturnCached() {
        // Arrange - Create a test user first
        org.github.tess1o.geopulse.user.model.UserEntity testUser = new org.github.tess1o.geopulse.user.model.UserEntity();
        testUser.setEmail("test-cache-past@geopulse.app");
        testUser.setFullName("Cache Past Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(java.time.Instant.now());
        userRepository.persist(testUser);
        
        UUID userId = testUser.getId();
        Instant yesterdayStart = LocalDate.now(ZoneOffset.UTC).minusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant yesterdayEnd = yesterdayStart.plusSeconds(86400 - 1);

        // First, create a timeline to cache
        MovementTimelineDTO generatedTimeline = new MovementTimelineDTO(userId);
        generatedTimeline.setDataSource(TimelineDataSource.LIVE);
        
        // Save to cache
        cacheService.save(userId, yesterdayStart, yesterdayEnd, generatedTimeline);

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(userId, yesterdayStart, yesterdayEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertEquals(userId, result.getUserId());
    }

    @Test
    @Transactional
    void testFutureTimeline_ShouldReturnEmpty() {
        // Arrange - Create a test user first
        org.github.tess1o.geopulse.user.model.UserEntity testUser = new org.github.tess1o.geopulse.user.model.UserEntity();
        testUser.setEmail("test-future@geopulse.app");
        testUser.setFullName("Future Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(java.time.Instant.now());
        userRepository.persist(testUser);
        
        UUID userId = testUser.getId();
        Instant tomorrowStart = LocalDate.now(ZoneOffset.UTC).plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant tomorrowEnd = tomorrowStart.plusSeconds(86400 - 1);

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(userId, tomorrowStart, tomorrowEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertEquals(userId, result.getUserId());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
    }

    @Test
    @Transactional
    void testMixedTimeline_PastAndToday_ShouldCombine() {
        // Arrange - Create a test user first
        org.github.tess1o.geopulse.user.model.UserEntity testUser = new org.github.tess1o.geopulse.user.model.UserEntity();
        testUser.setEmail("test-mixed@geopulse.app");
        testUser.setFullName("Mixed Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(java.time.Instant.now());
        userRepository.persist(testUser);
        
        UUID userId = testUser.getId();
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        
        Instant requestStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = today.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant();

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(userId, requestStart, requestEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
        assertEquals(userId, result.getUserId());
    }

    @Test
    @Transactional
    void testMixedTimelineWithFutureDates_ShouldIgnoreFuture() {
        // Arrange - Create a test user
        org.github.tess1o.geopulse.user.model.UserEntity testUser = new org.github.tess1o.geopulse.user.model.UserEntity();
        testUser.setEmail("test-future@geopulse.app");
        testUser.setFullName("Future Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(java.time.Instant.now());
        userRepository.persist(testUser);
        
        UUID userId = testUser.getId();
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        
        Instant requestStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = tomorrow.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant();

        // Act - Should handle this as mixed request (ignore future dates)
        MovementTimelineDTO result = timelineQueryService.getTimeline(userId, requestStart, requestEnd);

        // Assert - Should return mixed timeline (not empty due to future dates)
        assertNotNull(result);
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
        assertEquals(userId, result.getUserId());
        
        // Should contain data up to today, ignoring future dates
        assertTrue(result.getStays().isEmpty() || 
                   result.getStays().stream().allMatch(stay -> 
                       !stay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now(ZoneOffset.UTC))));
    }

    @Test
    void testCacheOperations_ShouldWorkCorrectly() {
        // Arrange - create a test user first
        org.github.tess1o.geopulse.user.model.UserEntity testUser = new org.github.tess1o.geopulse.user.model.UserEntity();
        testUser.setEmail("test-cache@geopulse.app");
        testUser.setFullName("Cache Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(java.time.Instant.now());
        
        // Persist the test user
        userRepository.persist(testUser);
        
        UUID userId = testUser.getId();
        Instant start = LocalDate.now(ZoneOffset.UTC).minusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = start.plusSeconds(86400 - 1);

        // Test cache doesn't exist initially
        assertFalse(cacheService.exists(userId, start, end));

        // Create and save timeline with some data
        org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO testStay = 
            new org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO();
        testStay.setTimestamp(start);
        testStay.setStayDuration(end.getEpochSecond() - start.getEpochSecond());
        testStay.setLatitude(37.7749);
        testStay.setLongitude(-122.4194);
        testStay.setLocationName("Test Location");
        
        // Create timeline with stays and trips lists
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, 
            java.util.List.of(testStay), 
            java.util.Collections.emptyList());
        timeline.setDataSource(TimelineDataSource.CACHED);
        
        cacheService.save(userId, start, end, timeline);

        // Test cache now exists
        assertTrue(cacheService.exists(userId, start, end));

        // Test retrieval
        MovementTimelineDTO retrieved = cacheService.get(userId, start, end);
        assertNotNull(retrieved);
        assertEquals(userId, retrieved.getUserId());
        assertEquals(TimelineDataSource.CACHED, retrieved.getDataSource());

        // Test deletion
        cacheService.delete(userId, java.util.List.of(start.atZone(ZoneOffset.UTC).toLocalDate()));
        assertFalse(cacheService.exists(userId, start, end));
    }
}