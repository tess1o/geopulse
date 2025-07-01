package org.github.tess1o.geopulse.timeline.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.model.LocationSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.service.TimelineInvalidationService;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for timeline invalidation when user timeline preferences are updated.
 * Tests the event-driven invalidation system for preference changes.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelinePreferencesInvalidationIntegrationTest {

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineInvalidationService invalidationService;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;

    // Test coordinates
    private static final double TEST_LAT = 52.520008;
    private static final double TEST_LON = 13.404954;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user with unique email
        testUser = new UserEntity();
        testUser.setEmail("preferences-invalidation-test-" + System.currentTimeMillis() + "@example.com");
        testUser.setFullName("Preferences Invalidation Test User");
        testUser.setPasswordHash("dummy-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole("USER");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();

        log.info("Set up preferences invalidation test user: {}", testUser.getId());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            // Clean up test data
            entityManager.createQuery("DELETE FROM TimelineStayEntity t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            
            userRepository.delete(testUser);
            entityManager.flush();
        }
    }

    /**
     * Test that updating timeline preferences triggers invalidation of all user's timeline data.
     * This is the core functionality we're implementing.
     */
    @Test
    @Transactional
    void testTimelinePreferencesUpdateTriggersGlobalInvalidation() {
        log.info("Testing timeline preferences update triggers global invalidation");

        // Create multiple timeline stays across different dates
        LocalDate date1 = LocalDate.of(2025, 6, 10);
        LocalDate date2 = LocalDate.of(2025, 6, 11);
        LocalDate date3 = LocalDate.of(2025, 6, 12);

        TimelineStayEntity stay1 = createTestTimelineStay(date1.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), "Location 1");
        TimelineStayEntity stay2 = createTestTimelineStay(date2.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), "Location 2");
        TimelineStayEntity stay3 = createTestTimelineStay(date3.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), "Location 3");
        entityManager.flush();

        // Verify initial state - all stays should not be stale
        assertFalse(stay1.getIsStale(), "Initial stay 1 should not be stale");
        assertFalse(stay2.getIsStale(), "Initial stay 2 should not be stale");
        assertFalse(stay3.getIsStale(), "Initial stay 3 should not be stale");

        // Get initial queue size
        int initialQueueSize = invalidationService.getQueueSize();
        log.info("Initial invalidation queue size: {}", initialQueueSize);

        // Update timeline preferences - this should trigger the event
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setStaypointDetectionAlgorithm("enhanced"); // Change from default
        request.setStaypointVelocityThreshold(15.0); // Change from default
        request.setTripMinDistanceMeters(150); // Change from default

        userService.updateTimelinePreferences(testUser.getId(), request);
        entityManager.flush();

        // Verify all stays are now marked as stale
        entityManager.refresh(stay1);
        entityManager.refresh(stay2);
        entityManager.refresh(stay3);

        assertTrue(stay1.getIsStale(), "Stay 1 should be marked as stale after preference update");
        assertTrue(stay2.getIsStale(), "Stay 2 should be marked as stale after preference update");
        assertTrue(stay3.getIsStale(), "Stay 3 should be marked as stale after preference update");

        // Verify invalidation queue has new items
        int finalQueueSize = invalidationService.getQueueSize();
        assertTrue(finalQueueSize > initialQueueSize, "Invalidation queue should have new items");

        log.info("Preferences update invalidation test completed - queue size increased from {} to {}", 
                initialQueueSize, finalQueueSize);
    }

    /**
     * Test that resetting timeline preferences to defaults also triggers invalidation.
     */
    @Test
    @Transactional
    void testTimelinePreferencesResetTriggersInvalidation() {
        log.info("Testing timeline preferences reset triggers invalidation");

        // Set some custom preferences first using the service to avoid detached entity issues
        UpdateTimelinePreferencesRequest initialRequest = new UpdateTimelinePreferencesRequest();
        initialRequest.setStaypointDetectionAlgorithm("enhanced");
        initialRequest.setTripMinDistanceMeters(300);
        userService.updateTimelinePreferences(testUser.getId(), initialRequest);
        entityManager.flush();

        // Create timeline stays
        TimelineStayEntity stay1 = createTestTimelineStay(
            LocalDate.of(2025, 6, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), 
            "Location 1"
        );
        TimelineStayEntity stay2 = createTestTimelineStay(
            LocalDate.of(2025, 6, 11).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), 
            "Location 2"
        );
        entityManager.flush();

        // Verify initial state
        assertFalse(stay1.getIsStale(), "Initial stay should not be stale");
        assertFalse(stay2.getIsStale(), "Initial stay should not be stale");

        int initialQueueSize = invalidationService.getQueueSize();

        // Reset preferences to defaults - this should trigger the event
        userService.resetTimelinePreferencesToDefaults(testUser.getId());
        entityManager.flush();

        // Verify all stays are marked as stale
        entityManager.refresh(stay1);
        entityManager.refresh(stay2);

        assertTrue(stay1.getIsStale(), "Stay 1 should be marked as stale after preferences reset");
        assertTrue(stay2.getIsStale(), "Stay 2 should be marked as stale after preferences reset");

        // Verify queue size increased
        int finalQueueSize = invalidationService.getQueueSize();
        assertTrue(finalQueueSize > initialQueueSize, "Queue should have new items after reset");

        log.info("Preferences reset invalidation test completed successfully");
    }

    /**
     * Test that timeline preferences invalidation affects ALL user stays, not just recent ones.
     * This verifies the global scope of preference-based invalidation.
     */
    @Test
    @Transactional
    void testPreferencesInvalidationIsGlobal() {
        log.info("Testing that preferences invalidation affects all user timeline data globally");

        // Create timeline stays spanning a large time range
        LocalDate oldDate = LocalDate.of(2024, 1, 15); // Old data
        LocalDate mediumDate = LocalDate.of(2024, 6, 15); // Medium old data
        LocalDate recentDate = LocalDate.of(2025, 6, 15); // Recent data

        TimelineStayEntity oldStay = createTestTimelineStay(
            oldDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), 
            "Old Location"
        );
        TimelineStayEntity mediumStay = createTestTimelineStay(
            mediumDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), 
            "Medium Location"
        );
        TimelineStayEntity recentStay = createTestTimelineStay(
            recentDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), 
            "Recent Location"
        );
        entityManager.flush();

        // Verify initial state
        assertFalse(oldStay.getIsStale(), "Old stay should not be stale initially");
        assertFalse(mediumStay.getIsStale(), "Medium stay should not be stale initially");
        assertFalse(recentStay.getIsStale(), "Recent stay should not be stale initially");

        // Update preferences
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setIsMergeEnabled(false); // Change merging behavior
        request.setMergeMaxDistanceMeters(500); // Change merge distance

        userService.updateTimelinePreferences(testUser.getId(), request);
        entityManager.flush();

        // Verify ALL stays across the entire time range are marked as stale
        entityManager.refresh(oldStay);
        entityManager.refresh(mediumStay);
        entityManager.refresh(recentStay);

        assertTrue(oldStay.getIsStale(), "Old stay (2024) should be marked as stale");
        assertTrue(mediumStay.getIsStale(), "Medium stay (mid-2024) should be marked as stale");
        assertTrue(recentStay.getIsStale(), "Recent stay (2025) should be marked as stale");

        log.info("Global invalidation test completed - all timeline data across time ranges affected");
    }

    /**
     * Test timeline preferences invalidation with a user who has no timeline data.
     * Should complete successfully without errors.
     */
    @Test
    @Transactional
    void testPreferencesInvalidationWithNoTimelineData() {
        log.info("Testing preferences invalidation when user has no timeline data");

        // Verify user has no timeline stays
        List<TimelineStayEntity> existingStays = stayRepository.findByUser(testUser.getId());
        assertTrue(existingStays.isEmpty(), "User should have no existing timeline data");

        int initialQueueSize = invalidationService.getQueueSize();

        // Update preferences - should complete without errors
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setStaypointDetectionAlgorithm("enhanced");

        assertDoesNotThrow(() -> {
            userService.updateTimelinePreferences(testUser.getId(), request);
            entityManager.flush();
        }, "Preferences update should not throw with no timeline data");

        // Queue size should not change since there's nothing to invalidate
        int finalQueueSize = invalidationService.getQueueSize();
        assertEquals(initialQueueSize, finalQueueSize, "Queue size should not change when no timeline data exists");

        log.info("No timeline data test completed successfully");
    }

    /**
     * Test that multiple preference updates don't cause duplicate invalidation.
     * Verifies that the system handles rapid consecutive updates gracefully.
     */
    @Test
    @Transactional
    void testMultiplePreferenceUpdatesHandling() {
        log.info("Testing multiple rapid preference updates");

        // Create a single timeline stay
        TimelineStayEntity stay = createTestTimelineStay(
            LocalDate.of(2025, 6, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), 
            "Test Location"
        );
        entityManager.flush();

        assertFalse(stay.getIsStale(), "Stay should not be stale initially");

        int initialQueueSize = invalidationService.getQueueSize();

        // Perform multiple preference updates in succession
        UpdateTimelinePreferencesRequest request1 = new UpdateTimelinePreferencesRequest();
        request1.setStaypointDetectionAlgorithm("enhanced");
        userService.updateTimelinePreferences(testUser.getId(), request1);

        UpdateTimelinePreferencesRequest request2 = new UpdateTimelinePreferencesRequest();
        request2.setTripMinDistanceMeters(200);
        userService.updateTimelinePreferences(testUser.getId(), request2);

        UpdateTimelinePreferencesRequest request3 = new UpdateTimelinePreferencesRequest();
        request3.setIsMergeEnabled(false);
        userService.updateTimelinePreferences(testUser.getId(), request3);

        entityManager.flush();

        // Verify stay is marked as stale (but we can't verify it wasn't invalidated multiple times)
        entityManager.refresh(stay);
        assertTrue(stay.getIsStale(), "Stay should be marked as stale after multiple updates");

        // Verify queue has items (the system should handle duplicates gracefully in background processing)
        int finalQueueSize = invalidationService.getQueueSize();
        assertTrue(finalQueueSize > initialQueueSize, "Queue should have items after multiple updates");

        log.info("Multiple preference updates test completed successfully");
    }

    /**
     * Helper method to create a test timeline stay entity.
     */
    private TimelineStayEntity createTestTimelineStay(Instant timestamp, String locationName) {
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setUser(testUser);
        stay.setTimestamp(timestamp);
        stay.setLatitude(TEST_LAT);
        stay.setLongitude(TEST_LON);
        stay.setLocationName(locationName);
        stay.setStayDuration(60); // 60 minutes
        stay.setLocationSource(LocationSource.GEOCODING);
        stay.setIsStale(false);
        stay.setTimelineVersion("test-version");
        stay.setLastUpdated(Instant.now());
        
        stayRepository.persist(stay);
        return stay;
    }
}