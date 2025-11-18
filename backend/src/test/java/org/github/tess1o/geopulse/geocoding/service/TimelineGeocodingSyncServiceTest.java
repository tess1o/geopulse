package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TimelineGeocodingSyncService.
 * Tests SQL correctness, user isolation, and update counts.
 *
 * Coverage: 12 test cases across 2 sync methods
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TimelineGeocodingSyncService Integration Tests")
class TimelineGeocodingSyncServiceTest {

    @Inject
    TimelineGeocodingSyncService syncService;

    @Inject
    EntityManager entityManager;

    @Inject
    CleanupHelper cleanupHelper;

    private static UUID USER_A_ID;
    private static UUID USER_B_ID;

    private static final double TEST_LAT = 40.7589;
    private static final double TEST_LON = -73.9851;

    @BeforeEach
    @Transactional
    void setupUsers() {
        UserEntity userA = UserEntity.builder()
                .email("user-a-sync-test@example.com")
                .fullName("User A")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(userA);

        UserEntity userB = UserEntity.builder()
                .email("user-b-sync-test@example.com")
                .fullName("User B")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(userB);

        entityManager.flush();
        USER_A_ID = userA.getId();
        USER_B_ID = userB.getId();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        cleanupHelper.cleanupAll();
    }

    // ==================== updateLocationNameForUser() Tests ====================

    @Test
    @Order(1)
    @Transactional
    @DisplayName("updateLocationNameForUser: Should update location name for user with single stay")
    void testUpdateSingleStay() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity geocoding = createGeocodingEntity(coords, "Original Name");
        entityManager.persist(geocoding);
        entityManager.flush();

        TimelineStayEntity stay = createTimelineStay(USER_A_ID, coords, geocoding, "Original Name");
        entityManager.persist(stay);
        entityManager.flush();

        // When
        int updatedCount = syncService.updateLocationNameForUser(USER_A_ID, geocoding.getId(), "Updated Name");

        // Then
        assertEquals(1, updatedCount);

        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity updatedStay = entityManager.find(TimelineStayEntity.class, stay.getId());
        assertEquals("Updated Name", updatedStay.getLocationName());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("updateLocationNameForUser: Should update multiple stays at same location")
    void testUpdateMultipleStays() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity geocoding = createGeocodingEntity(coords, "Starbucks");
        entityManager.persist(geocoding);
        entityManager.flush();

        TimelineStayEntity stay1 = createTimelineStay(USER_A_ID, coords, geocoding, "Starbucks");
        TimelineStayEntity stay2 = createTimelineStay(USER_A_ID, coords, geocoding, "Starbucks");
        TimelineStayEntity stay3 = createTimelineStay(USER_A_ID, coords, geocoding, "Starbucks");
        entityManager.persist(stay1);
        entityManager.persist(stay2);
        entityManager.persist(stay3);
        entityManager.flush();

        // When
        int updatedCount = syncService.updateLocationNameForUser(USER_A_ID, geocoding.getId(), "My Coffee Shop");

        // Then
        assertEquals(3, updatedCount);

        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity updated1 = entityManager.find(TimelineStayEntity.class, stay1.getId());
        TimelineStayEntity updated2 = entityManager.find(TimelineStayEntity.class, stay2.getId());
        TimelineStayEntity updated3 = entityManager.find(TimelineStayEntity.class, stay3.getId());

        assertEquals("My Coffee Shop", updated1.getLocationName());
        assertEquals("My Coffee Shop", updated2.getLocationName());
        assertEquals("My Coffee Shop", updated3.getLocationName());
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("updateLocationNameForUser: Should only update current user's stays (user isolation)")
    void testUserIsolationOnUpdate() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity geocoding = createGeocodingEntity(coords, "Shared Location");
        entityManager.persist(geocoding);
        entityManager.flush();

        TimelineStayEntity userAStay = createTimelineStay(USER_A_ID, coords, geocoding, "Shared Location");
        TimelineStayEntity userBStay = createTimelineStay(USER_B_ID, coords, geocoding, "Shared Location");
        entityManager.persist(userAStay);
        entityManager.persist(userBStay);
        entityManager.flush();

        // When
        int updatedCount = syncService.updateLocationNameForUser(USER_A_ID, geocoding.getId(), "User A's Name");

        // Then
        assertEquals(1, updatedCount, "Should only update User A's stay");

        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity updatedUserAStay = entityManager.find(TimelineStayEntity.class, userAStay.getId());
        TimelineStayEntity unchangedUserBStay = entityManager.find(TimelineStayEntity.class, userBStay.getId());

        assertEquals("User A's Name", updatedUserAStay.getLocationName());
        assertEquals("Shared Location", unchangedUserBStay.getLocationName(), "User B's stay should be unchanged");
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("updateLocationNameForUser: Should verify stays for other users unchanged")
    void testOtherUsersUnaffected() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity geocoding = createGeocodingEntity(coords, "Location");
        entityManager.persist(geocoding);
        entityManager.flush();

        // Create multiple stays for both users
        TimelineStayEntity userAStay1 = createTimelineStay(USER_A_ID, coords, geocoding, "Location");
        TimelineStayEntity userAStay2 = createTimelineStay(USER_A_ID, coords, geocoding, "Location");
        TimelineStayEntity userBStay1 = createTimelineStay(USER_B_ID, coords, geocoding, "Location");
        TimelineStayEntity userBStay2 = createTimelineStay(USER_B_ID, coords, geocoding, "Location");
        entityManager.persist(userAStay1);
        entityManager.persist(userAStay2);
        entityManager.persist(userBStay1);
        entityManager.persist(userBStay2);
        entityManager.flush();

        // When
        int updatedCount = syncService.updateLocationNameForUser(USER_A_ID, geocoding.getId(), "User A Custom");

        // Then
        assertEquals(2, updatedCount, "Should update exactly 2 stays for User A");

        entityManager.flush();
        entityManager.clear();

        // Verify User A's stays updated
        TimelineStayEntity ua1 = entityManager.find(TimelineStayEntity.class, userAStay1.getId());
        TimelineStayEntity ua2 = entityManager.find(TimelineStayEntity.class, userAStay2.getId());
        assertEquals("User A Custom", ua1.getLocationName());
        assertEquals("User A Custom", ua2.getLocationName());

        // Verify User B's stays unchanged
        TimelineStayEntity ub1 = entityManager.find(TimelineStayEntity.class, userBStay1.getId());
        TimelineStayEntity ub2 = entityManager.find(TimelineStayEntity.class, userBStay2.getId());
        assertEquals("Location", ub1.getLocationName());
        assertEquals("Location", ub2.getLocationName());
    }

    @Test
    @Order(5)
    @Transactional
    @DisplayName("updateLocationNameForUser: Should return 0 for user with no timeline stays")
    void testUserWithNoStays() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity geocoding = createGeocodingEntity(coords, "Location");
        entityManager.persist(geocoding);
        entityManager.flush();

        // No stays created for any user

        // When
        int updatedCount = syncService.updateLocationNameForUser(USER_A_ID, geocoding.getId(), "New Name");

        // Then
        assertEquals(0, updatedCount);
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("updateLocationNameForUser: Should return 0 for geocoding ID not referenced by any stays")
    void testGeocodingIdNotReferenced() {
        // Given
        Point coords1 = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        Point coords2 = GeoUtils.createPoint(TEST_LON + 0.001, TEST_LAT + 0.001);
        ReverseGeocodingLocationEntity geocoding1 = createGeocodingEntity(coords1, "Location 1");
        ReverseGeocodingLocationEntity geocoding2 = createGeocodingEntity(coords2, "Location 2");
        entityManager.persist(geocoding1);
        entityManager.persist(geocoding2);
        entityManager.flush();

        TimelineStayEntity stay = createTimelineStay(USER_A_ID, coords1, geocoding1, "Location 1");
        entityManager.persist(stay);
        entityManager.flush();

        // When: Try to update with geocoding2 (not referenced)
        int updatedCount = syncService.updateLocationNameForUser(USER_A_ID, geocoding2.getId(), "New Name");

        // Then
        assertEquals(0, updatedCount);

        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity unchangedStay = entityManager.find(TimelineStayEntity.class, stay.getId());
        assertEquals("Location 1", unchangedStay.getLocationName(), "Stay should be unchanged");
    }

    // ==================== switchToNewGeocodingReference() Tests ====================

    @Test
    @Order(7)
    @Transactional
    @DisplayName("switchToNewGeocodingReference: Should switch single stay from original to user copy")
    void testSwitchSingleStay() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON + 0.1, TEST_LAT + 0.1);
        ReverseGeocodingLocationEntity original = createGeocodingEntity(coords, "Original");
        ReverseGeocodingLocationEntity userCopy = createGeocodingEntity(coords, "User Copy", USER_A_ID);
        entityManager.persist(original);
        entityManager.persist(userCopy);
        entityManager.flush();

        TimelineStayEntity stay = createTimelineStay(USER_A_ID, coords, original, "Original");
        entityManager.persist(stay);
        entityManager.flush();

        // When
        int updatedCount = syncService.switchToNewGeocodingReference(
                USER_A_ID, original.getId(), userCopy.getId(), "Custom Name");

        // Then
        assertEquals(1, updatedCount);

        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity updatedStay = entityManager.find(TimelineStayEntity.class, stay.getId());
        assertEquals(userCopy.getId(), updatedStay.getGeocodingLocation().getId());
        assertEquals("Custom Name", updatedStay.getLocationName());
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("switchToNewGeocodingReference: Should switch multiple stays from original to user copy")
    void testSwitchMultipleStays() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON + 0.2, TEST_LAT + 0.2);
        ReverseGeocodingLocationEntity original = createGeocodingEntity(coords, "Gym");
        ReverseGeocodingLocationEntity userCopy = createGeocodingEntity(coords, "My Gym", USER_A_ID);
        entityManager.persist(original);
        entityManager.persist(userCopy);
        entityManager.flush();

        TimelineStayEntity stay1 = createTimelineStay(USER_A_ID, coords, original, "Gym");
        TimelineStayEntity stay2 = createTimelineStay(USER_A_ID, coords, original, "Gym");
        TimelineStayEntity stay3 = createTimelineStay(USER_A_ID, coords, original, "Gym");
        entityManager.persist(stay1);
        entityManager.persist(stay2);
        entityManager.persist(stay3);
        entityManager.flush();

        // When
        int updatedCount = syncService.switchToNewGeocodingReference(
                USER_A_ID, original.getId(), userCopy.getId(), "My Gym");

        // Then
        assertEquals(3, updatedCount);

        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity updated1 = entityManager.find(TimelineStayEntity.class, stay1.getId());
        TimelineStayEntity updated2 = entityManager.find(TimelineStayEntity.class, stay2.getId());
        TimelineStayEntity updated3 = entityManager.find(TimelineStayEntity.class, stay3.getId());

        assertEquals(userCopy.getId(), updated1.getGeocodingLocation().getId());
        assertEquals(userCopy.getId(), updated2.getGeocodingLocation().getId());
        assertEquals(userCopy.getId(), updated3.getGeocodingLocation().getId());
        assertEquals("My Gym", updated1.getLocationName());
        assertEquals("My Gym", updated2.getLocationName());
        assertEquals("My Gym", updated3.getLocationName());
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("switchToNewGeocodingReference: Should only switch current user's stays (user isolation)")
    void testUserIsolationOnSwitch() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON + 0.3, TEST_LAT + 0.3);
        ReverseGeocodingLocationEntity original = createGeocodingEntity(coords, "Office");
        ReverseGeocodingLocationEntity userACopy = createGeocodingEntity(coords, "User A Office", USER_A_ID);
        entityManager.persist(original);
        entityManager.persist(userACopy);
        entityManager.flush();

        TimelineStayEntity userAStay = createTimelineStay(USER_A_ID, coords, original, "Office");
        TimelineStayEntity userBStay = createTimelineStay(USER_B_ID, coords, original, "Office");
        entityManager.persist(userAStay);
        entityManager.persist(userBStay);
        entityManager.flush();

        // When
        int updatedCount = syncService.switchToNewGeocodingReference(
                USER_A_ID, original.getId(), userACopy.getId(), "My Office");

        // Then
        assertEquals(1, updatedCount, "Should only switch User A's stay");

        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity updatedUserAStay = entityManager.find(TimelineStayEntity.class, userAStay.getId());
        TimelineStayEntity unchangedUserBStay = entityManager.find(TimelineStayEntity.class, userBStay.getId());

        assertEquals(userACopy.getId(), updatedUserAStay.getGeocodingLocation().getId());
        assertEquals("My Office", updatedUserAStay.getLocationName());

        assertEquals(original.getId(), unchangedUserBStay.getGeocodingLocation().getId(),
                "User B should still reference original");
        assertEquals("Office", unchangedUserBStay.getLocationName(), "User B's name should be unchanged");
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("switchToNewGeocodingReference: Should update location name simultaneously")
    void testLocationNameUpdatedSimultaneously() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON + 0.4, TEST_LAT + 0.4);
        ReverseGeocodingLocationEntity original = createGeocodingEntity(coords, "Park");
        ReverseGeocodingLocationEntity userCopy = createGeocodingEntity(coords, "Central Park", USER_A_ID);
        entityManager.persist(original);
        entityManager.persist(userCopy);
        entityManager.flush();

        TimelineStayEntity stay = createTimelineStay(USER_A_ID, coords, original, "Park");
        entityManager.persist(stay);
        entityManager.flush();

        Long originalStayId = stay.getId();
        String originalName = stay.getLocationName();
        Long originalGeocodingId = stay.getGeocodingLocation().getId();

        // When
        syncService.switchToNewGeocodingReference(
                USER_A_ID, original.getId(), userCopy.getId(), "My Favorite Park");

        // Then
        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity updatedStay = entityManager.find(TimelineStayEntity.class, originalStayId);
        assertNotEquals(originalGeocodingId, updatedStay.getGeocodingLocation().getId(),
                "Geocoding reference should change");
        assertNotEquals(originalName, updatedStay.getLocationName(), "Location name should change");
        assertEquals(userCopy.getId(), updatedStay.getGeocodingLocation().getId());
        assertEquals("My Favorite Park", updatedStay.getLocationName());
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("switchToNewGeocodingReference: Should return 0 for user with no stays at old geocoding ID")
    void testNoStaysAtOldGeocodingId() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON + 0.5, TEST_LAT + 0.5);
        ReverseGeocodingLocationEntity original = createGeocodingEntity(coords, "Location 1");
        ReverseGeocodingLocationEntity userCopy = createGeocodingEntity(coords, "Location 2", USER_A_ID);
        entityManager.persist(original);
        entityManager.persist(userCopy);
        entityManager.flush();

        // No stays created

        // When
        int updatedCount = syncService.switchToNewGeocodingReference(
                USER_A_ID, original.getId(), userCopy.getId(), "New Name");

        // Then
        assertEquals(0, updatedCount);
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("switchToNewGeocodingReference: Should verify both geocoding_id AND location_name updated atomically")
    void testAtomicUpdate() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON + 0.6, TEST_LAT + 0.6);
        ReverseGeocodingLocationEntity original = createGeocodingEntity(coords, "Restaurant");
        ReverseGeocodingLocationEntity userCopy = createGeocodingEntity(coords, "My Restaurant", USER_A_ID);
        entityManager.persist(original);
        entityManager.persist(userCopy);
        entityManager.flush();

        TimelineStayEntity stay1 = createTimelineStay(USER_A_ID, coords, original, "Restaurant");
        TimelineStayEntity stay2 = createTimelineStay(USER_A_ID, coords, original, "Restaurant");
        entityManager.persist(stay1);
        entityManager.persist(stay2);
        entityManager.flush();

        Long stay1Id = stay1.getId();
        Long stay2Id = stay2.getId();

        // When
        syncService.switchToNewGeocodingReference(
                USER_A_ID, original.getId(), userCopy.getId(), "Favorite Restaurant");

        // Then
        entityManager.flush();
        entityManager.clear();

        TimelineStayEntity updated1 = entityManager.find(TimelineStayEntity.class, stay1Id);
        TimelineStayEntity updated2 = entityManager.find(TimelineStayEntity.class, stay2Id);

        // Both fields should be updated for both stays
        assertEquals(userCopy.getId(), updated1.getGeocodingLocation().getId());
        assertEquals("Favorite Restaurant", updated1.getLocationName());
        assertEquals(userCopy.getId(), updated2.getGeocodingLocation().getId());
        assertEquals("Favorite Restaurant", updated2.getLocationName());
    }

    // ==================== Helper Methods ====================

    private ReverseGeocodingLocationEntity createGeocodingEntity(Point coords, String displayName) {
        return createGeocodingEntity(coords, displayName, null);
    }

    private ReverseGeocodingLocationEntity createGeocodingEntity(Point coords, String displayName, UUID userId) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setRequestCoordinates(coords);
        entity.setResultCoordinates(coords);
        entity.setDisplayName(displayName);
        entity.setProviderName("test_provider");
        entity.setCity("Test City");
        entity.setCountry("Test Country");
        entity.setCreatedAt(Instant.now());
        entity.setLastAccessedAt(Instant.now());

        if (userId != null) {
            UserEntity user = entityManager.getReference(UserEntity.class, userId);
            entity.setUser(user);
        }

        return entity;
    }

    private TimelineStayEntity createTimelineStay(UUID userId, Point location,
                                                   ReverseGeocodingLocationEntity geocoding,
                                                   String locationName) {
        UserEntity user = entityManager.getReference(UserEntity.class, userId);

        return TimelineStayEntity.builder()
                .user(user)
                .timestamp(Instant.now())
                .stayDuration(3600L)
                .location(location)
                .locationName(locationName)
                .geocodingLocation(geocoding)
                .locationSource(LocationSource.GEOCODING)
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .build();
    }
}
