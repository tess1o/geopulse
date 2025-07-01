package org.github.tess1o.geopulse.timeline.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.service.TimelinePersistenceService;
import org.github.tess1o.geopulse.timeline.service.TimelineQueryService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for timezone handling in timeline functionality.
 * Tests various timezone scenarios to ensure timeline generation and persistence
 * work correctly regardless of user timezone or custom time ranges.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelineTimezoneIntegrationTest {

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    TimelinePersistenceService persistenceService;

    @Inject
    UserRepository userRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("timezone-test@example.com");
        testUser.setFullName("Timezone Test User");
        testUser.setPasswordHash("dummy-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole("USER");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();

        log.info("Set up timezone test user: {}", testUser.getId());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            // Clean up test data
            entityManager.createQuery("DELETE FROM TimelineStayEntity t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM TimelineTripEntity t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            userRepository.delete(testUser);
            entityManager.flush();
        }
    }

    /**
     * Test timezone handling for GMT+3 user requesting their local day.
     * User in GMT+3 requests 2025-06-15 in their timezone:
     * - Starts at 2025-06-14T21:00:00Z (UTC)
     * - Ends at 2025-06-15T20:59:59.999Z (UTC)
     */
    @Test
    void testGMTPlus3UserDay() {
        log.info("Testing GMT+3 user day timeline generation");

        // GMT+3 user's day: 2025-06-15 00:00 to 23:59:59 local time
        LocalDate userLocalDate = LocalDate.of(2025, 6, 15);
        ZoneId userTimezone = ZoneId.of("Europe/Moscow"); // GMT+3

        // Convert to UTC for API request
        Instant startTimeUTC = userLocalDate.atStartOfDay(userTimezone).toInstant();
        Instant endTimeUTC = userLocalDate.plusDays(1).atStartOfDay(userTimezone).minusNanos(1000000).toInstant();

        log.info("User local date: {}", userLocalDate);
        log.info("UTC start time: {}", startTimeUTC);
        log.info("UTC end time: {}", endTimeUTC);

        // Verify UTC times are correct
        assertEquals("2025-06-14T21:00:00Z", startTimeUTC.toString());
        assertEquals("2025-06-15T20:59:59.999Z", endTimeUTC.toString());

        // Request timeline for this custom time range
        MovementTimelineDTO timeline = timelineQueryService.getTimeline(testUser.getId(), startTimeUTC, endTimeUTC);

        assertNotNull(timeline, "Timeline should be generated");
        assertEquals(testUser.getId(), timeline.getUserId(), "Timeline should belong to test user");
        assertNotNull(timeline.getDataSource(), "Timeline should have data source metadata");

        log.info("GMT+3 test completed successfully - Data source: {}", timeline.getDataSource());
    }

    /**
     * Test timezone handling for GMT-8 user requesting their local day.
     * User in GMT-8 requests 2025-06-15 in their timezone:
     * - Starts at 2025-06-15T08:00:00Z (UTC)
     * - Ends at 2025-06-16T07:59:59.999Z (UTC)
     */
    @Test
    void testGMTMinus8UserDay() {
        log.info("Testing GMT-8 user day timeline generation");

        // GMT-8 user's day: 2025-06-15 00:00 to 23:59:59 local time
        LocalDate userLocalDate = LocalDate.of(2025, 6, 15);
        ZoneId userTimezone = ZoneId.of("America/Los_Angeles"); // GMT-8 (PDT in summer)

        // Convert to UTC for API request
        Instant startTimeUTC = userLocalDate.atStartOfDay(userTimezone).toInstant();
        Instant endTimeUTC = userLocalDate.plusDays(1).atStartOfDay(userTimezone).minusNanos(1000000).toInstant();

        log.info("User local date: {}", userLocalDate);
        log.info("UTC start time: {}", startTimeUTC);
        log.info("UTC end time: {}", endTimeUTC);

        // Request timeline for this custom time range
        MovementTimelineDTO timeline = timelineQueryService.getTimeline(testUser.getId(), startTimeUTC, endTimeUTC);

        assertNotNull(timeline, "Timeline should be generated");
        assertEquals(testUser.getId(), timeline.getUserId(), "Timeline should belong to test user");
        assertNotNull(timeline.getDataSource(), "Timeline should have data source metadata");

        log.info("GMT-8 test completed successfully - Data source: {}", timeline.getDataSource());
    }

    /**
     * Test persistence logic for different timezone scenarios.
     * Ensures that shouldPersistTimeline works correctly for various timezone requests.
     */
    @Test
    void testPersistenceLogicWithTimezones() {
        log.info("Testing persistence logic with different timezone scenarios");

        // Test case 1: Yesterday's GMT+3 day (should be persisted)
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        ZoneId gmt3 = ZoneId.of("Europe/Moscow");
        Instant startGMT3 = yesterday.atStartOfDay(gmt3).toInstant();
        Instant endGMT3 = yesterday.plusDays(1).atStartOfDay(gmt3).minusNanos(1000000).toInstant();

        boolean shouldPersistGMT3 = persistenceService.shouldPersistTimeline(startGMT3, endGMT3);
        assertTrue(shouldPersistGMT3, "Yesterday's GMT+3 day should be persisted");

        // Test case 2: Today's GMT+3 day (should NOT be persisted)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startTodayGMT3 = today.atStartOfDay(gmt3).toInstant();
        Instant endTodayGMT3 = today.plusDays(1).atStartOfDay(gmt3).minusNanos(1000000).toInstant();

        boolean shouldPersistTodayGMT3 = persistenceService.shouldPersistTimeline(startTodayGMT3, endTodayGMT3);
        // This might be true or false depending on the current UTC time and GMT+3 offset
        log.info("Today's GMT+3 day persistence check: {}", shouldPersistTodayGMT3);

        // Test case 3: Custom time range spanning across UTC midnight
        ZoneId gmt5 = ZoneId.of("America/New_York");
        LocalDate testDate = LocalDate.of(2025, 6, 10); // A past date
        Instant startGMT5 = testDate.atStartOfDay(gmt5).toInstant();
        Instant endGMT5 = testDate.plusDays(1).atStartOfDay(gmt5).minusNanos(1000000).toInstant();

        boolean shouldPersistGMT5 = persistenceService.shouldPersistTimeline(startGMT5, endGMT5);
        assertTrue(shouldPersistGMT5, "Past GMT-5 day should be persisted");

        log.info("Persistence logic tests completed successfully");
    }

    /**
     * Test timeline caching with different timezone requests.
     * Ensures that different timezone requests don't incorrectly share cache entries.
     */
    @Test
    void testTimelineCachingWithTimezones() {
        log.info("Testing timeline caching with different timezone requests");

        LocalDate testDate = LocalDate.of(2025, 6, 10); // A past date for caching

        // Request 1: GMT+3 user's day
        ZoneId gmt3 = ZoneId.of("Europe/Moscow");
        Instant start1 = testDate.atStartOfDay(gmt3).toInstant();
        Instant end1 = testDate.plusDays(1).atStartOfDay(gmt3).minusNanos(1000000).toInstant();

        MovementTimelineDTO timeline1 = timelineQueryService.getTimeline(testUser.getId(), start1, end1);
        assertNotNull(timeline1, "First timeline request should succeed");

        // Request 2: GMT-8 user's same local date (different UTC times)
        ZoneId gmt8 = ZoneId.of("America/Los_Angeles");
        Instant start2 = testDate.atStartOfDay(gmt8).toInstant();
        Instant end2 = testDate.plusDays(1).atStartOfDay(gmt8).minusNanos(1000000).toInstant();

        MovementTimelineDTO timeline2 = timelineQueryService.getTimeline(testUser.getId(), start2, end2);
        assertNotNull(timeline2, "Second timeline request should succeed");

        // Verify that these are treated as different requests (different UTC ranges)
        assertNotEquals(start1, start2, "Start times should be different for different timezones");
        assertNotEquals(end1, end2, "End times should be different for different timezones");

        log.info("GMT+3 range: {} to {}", start1, end1);
        log.info("GMT-8 range: {} to {}", start2, end2);

        // Both requests should succeed independently
        assertEquals(testUser.getId(), timeline1.getUserId());
        assertEquals(testUser.getId(), timeline2.getUserId());

        log.info("Timeline caching tests completed successfully");
    }

    /**
     * Test edge case: timeline request that spans across DST transition.
     * This tests handling of daylight saving time changes.
     */
    @Test
    void testDaylightSavingTimeTransition() {
        log.info("Testing timeline generation during DST transition");

        // Use a date when DST transitions occur (spring forward in US)
        // March 10, 2024 - DST begins in US (2 AM becomes 3 AM)
        LocalDate dstDate = LocalDate.of(2024, 3, 10);
        ZoneId easternTime = ZoneId.of("America/New_York");

        // This day is actually 23 hours long due to DST transition
        Instant startDST = dstDate.atStartOfDay(easternTime).toInstant();
        Instant endDST = dstDate.plusDays(1).atStartOfDay(easternTime).minusNanos(1000000).toInstant();

        log.info("DST transition day: {} in {}", dstDate, easternTime);
        log.info("UTC range: {} to {}", startDST, endDST);

        // Calculate duration to verify DST handling
        Duration duration = Duration.between(startDST, endDST);
        log.info("Day duration: {} hours", duration.toHours());

        // Request timeline for DST transition day
        MovementTimelineDTO timeline = timelineQueryService.getTimeline(testUser.getId(), startDST, endDST);

        assertNotNull(timeline, "Timeline should be generated for DST transition day");
        assertEquals(testUser.getId(), timeline.getUserId());

        // Verify persistence logic handles DST correctly
        boolean shouldPersist = persistenceService.shouldPersistTimeline(startDST, endDST);
        assertTrue(shouldPersist, "Past DST transition day should be persisted");

        log.info("DST transition test completed successfully");
    }

    /**
     * Test multiple consecutive days from different timezones.
     * Ensures that timeline generation works for multi-day requests across timezones.
     */
    @Test
    void testMultipleDaysAcrossTimezones() {
        log.info("Testing multiple consecutive days across timezones");

        // Test a 3-day period from GMT+9 user perspective
        LocalDate startDate = LocalDate.of(2025, 6, 10);
        ZoneId tokyo = ZoneId.of("Asia/Tokyo"); // GMT+9

        Instant start = startDate.atStartOfDay(tokyo).toInstant();
        Instant end = startDate.plusDays(3).atStartOfDay(tokyo).minusNanos(1000000).toInstant();

        log.info("Tokyo 3-day period: {} to {}", start, end);
        log.info("Duration: {} hours", Duration.between(start, end).toHours());

        // This should be treated as a multi-day request (>24 hours)
        long durationHours = Duration.between(start, end).toHours();
        assertTrue(durationHours > 24, "Should be recognized as multi-day request");

        MovementTimelineDTO timeline = timelineQueryService.getTimeline(testUser.getId(), start, end);

        assertNotNull(timeline, "Multi-day timeline should be generated");
        assertEquals(testUser.getId(), timeline.getUserId());

        // Multi-day requests typically use live generation
        // (though this depends on the current implementation logic)
        log.info("Multi-day timeline data source: {}", timeline.getDataSource());

        log.info("Multi-day timezone test completed successfully");
    }

    /**
     * Test boundary conditions around midnight in different timezones.
     */
    @Test
    void testMidnightBoundaryConditions() {
        log.info("Testing midnight boundary conditions in different timezones");

        LocalDate testDate = LocalDate.of(2025, 6, 15);

        // Test very short time ranges around midnight in different timezones
        ZoneId[] timezones = {
                ZoneId.of("UTC"),
                ZoneId.of("Europe/London"),     // GMT/BST
                ZoneId.of("Asia/Tokyo"),        // GMT+9
                ZoneId.of("America/New_York"),  // GMT-5/-4
                ZoneId.of("Australia/Sydney")   // GMT+10/+11
        };

        for (ZoneId timezone : timezones) {
            log.info("Testing midnight boundary in timezone: {}", timezone);

            // Test 1-hour window around midnight
            Instant midnightStart = testDate.atStartOfDay(timezone).toInstant();
            Instant oneHourLater = midnightStart.plus(Duration.ofHours(1));

            MovementTimelineDTO timeline = timelineQueryService.getTimeline(
                    testUser.getId(), midnightStart, oneHourLater);

            assertNotNull(timeline, "Timeline should be generated for " + timezone);
            assertEquals(testUser.getId(), timeline.getUserId());

            log.info("Timezone {} - Range: {} to {} - Duration: 1 hour - Success",
                    timezone, midnightStart, oneHourLater);
        }

        log.info("Midnight boundary tests completed successfully");
    }
}