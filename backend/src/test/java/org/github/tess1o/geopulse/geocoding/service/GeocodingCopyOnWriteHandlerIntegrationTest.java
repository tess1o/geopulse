package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingUpdateDTO;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GeocodingCopyOnWriteHandler using real database.
 * Tests complete workflows with actual persistence and database constraints.
 *
 * Coverage: End-to-end copy-on-write operations with real database interactions
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@DisplayName("GeocodingCopyOnWriteHandler Integration Tests")
class GeocodingCopyOnWriteHandlerIntegrationTest {

    @Inject
    GeocodingCopyOnWriteHandler handler;

    @Inject
    ReverseGeocodingLocationRepository repository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    CleanupHelper cleanupHelper;

    private static final double TEST_LAT = 40.7589;
    private static final double TEST_LON = -73.9851;

    private UserEntity testUser;
    private UserEntity otherUser;
    private UUID testUserId;
    private UUID otherUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test users
        testUser = new UserEntity();
        testUser.setEmail("test@geopulse.app");
        testUser.setFullName("Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);
        testUserId = testUser.getId();

        otherUser = new UserEntity();
        otherUser.setEmail("other@geopulse.app");
        otherUser.setFullName("Other User");
        otherUser.setPasswordHash("other-hash");
        otherUser.setCreatedAt(Instant.now());
        userRepository.persist(otherUser);
        otherUserId = otherUser.getId();

        entityManager.flush();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupHelper.cleanupAll();
    }

    // ==================== Test Suite 1: handleUserUpdate() ====================

    @Test
    @DisplayName("Should update own entity in-place")
    @Transactional
    void testUserUpdatingOwnEntity() {
        // Given
        ReverseGeocodingLocationEntity entity = createUserOwnedEntity(testUser);
        repository.persist(entity);
        entityManager.flush();
        Long entityId = entity.getId();

        ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                .displayName("Updated Name")
                .city("Updated City")
                .country("Updated Country")
                .build();

        // When
        GeocodingCopyOnWriteHandler.UpdateResult result = handler.handleUserUpdate(testUserId, entity, updateDTO);

        // Then
        assertNotNull(result);
        assertFalse(result.wasCopied(), "Should be in-place update");
        assertNull(result.originalId());
        assertEquals(entity, result.entity());

        // Verify entity was updated in database
        entityManager.flush();
        entityManager.clear();
        ReverseGeocodingLocationEntity reloaded = repository.findById(entityId);
        assertNotNull(reloaded);
        assertEquals("Updated Name", reloaded.getDisplayName());
        assertEquals("Updated City", reloaded.getCity());
        assertEquals("Updated Country", reloaded.getCountry());
        assertTrue(reloaded.isOwnedBy(testUserId));
    }

    @Test
    @DisplayName("Should create copy when modifying original entity")
    @Transactional
    void testUserModifyingOriginal() {
        // Given
        ReverseGeocodingLocationEntity original = createOriginalEntity();
        repository.persist(original);
        entityManager.flush();
        Long originalId = original.getId();

        ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                .displayName("Custom Name")
                .city("Custom City")
                .country("Custom Country")
                .build();

        // When
        GeocodingCopyOnWriteHandler.UpdateResult result = handler.handleUserUpdate(testUserId, original, updateDTO);

        // Then
        assertNotNull(result);
        assertTrue(result.wasCopied(), "Should create copy");
        assertEquals(originalId, result.originalId());
        assertNotEquals(originalId, result.entity().getId());

        // Verify original is unchanged in database
        entityManager.flush();
        entityManager.clear();
        ReverseGeocodingLocationEntity reloadedOriginal = repository.findById(originalId);
        assertNotNull(reloadedOriginal);
        assertEquals("Test Location", reloadedOriginal.getDisplayName());
        assertTrue(reloadedOriginal.isOriginal());

        // Verify user copy was created in database
        ReverseGeocodingLocationEntity userCopy = repository.findById(result.entity().getId());
        assertNotNull(userCopy);
        assertEquals("Custom Name", userCopy.getDisplayName());
        assertEquals("Custom City", userCopy.getCity());
        assertEquals("Custom Country", userCopy.getCountry());
        assertTrue(userCopy.isOwnedBy(testUserId));

        // Verify we now have 2 entities in database
        long count = repository.count();
        assertEquals(2, count);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when modifying another user's entity")
    @Transactional
    void testUserTryingToModifyAnotherUsersEntity() {
        // Given
        ReverseGeocodingLocationEntity otherUserEntity = createUserOwnedEntity(otherUser);
        repository.persist(otherUserEntity);
        entityManager.flush();

        ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                .displayName("Hacker Name")
                .build();

        // When / Then
        assertThrows(ForbiddenException.class, () -> {
            handler.handleUserUpdate(testUserId, otherUserEntity, updateDTO);
        });

        // Verify entity was not modified in database
        entityManager.flush();
        entityManager.clear();
        ReverseGeocodingLocationEntity reloaded = repository.findById(otherUserEntity.getId());
        assertNotNull(reloaded);
        assertEquals("Test Location", reloaded.getDisplayName());
        assertTrue(reloaded.isOwnedBy(otherUserId));
    }

    @Test
    @DisplayName("Should sync timeline when updating own entity")
    @Transactional
    void testTimelineSyncWhenUpdatingOwnEntity() {
        // Given
        ReverseGeocodingLocationEntity entity = createUserOwnedEntity(testUser);
        repository.persist(entity);
        entityManager.flush();

        // Create a timeline stay that references this geocoding
        TimelineStayEntity stay = createTimelineStay(testUser, entity);
        stayRepository.persist(stay);
        entityManager.flush();
        Long entityId = entity.getId();
        Long stayId = stay.getId();
        entityManager.clear();

        // Reload entity after clear
        ReverseGeocodingLocationEntity reloadedEntity = repository.findById(entityId);

        ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                .displayName("Updated Location Name")
                .city("Updated City")
                .country("Updated Country")
                .build();

        // When
        GeocodingCopyOnWriteHandler.UpdateResult result = handler.handleUserUpdate(testUserId, reloadedEntity, updateDTO);

        // Then
        entityManager.flush();
        entityManager.clear();

        // Verify timeline was updated with new location name
        TimelineStayEntity reloadedStay = stayRepository.findById(stayId);
        assertNotNull(reloadedStay);
        assertEquals("Updated Location Name", reloadedStay.getLocationName());
        assertEquals(entityId, reloadedStay.getGeocodingLocation().getId());
    }

    @Test
    @DisplayName("Should sync timeline when creating copy of original")
    @Transactional
    void testTimelineSyncWhenCreatingCopy() {
        // Given
        ReverseGeocodingLocationEntity original = createOriginalEntity();
        repository.persist(original);
        entityManager.flush();

        // Create a timeline stay that references the original
        TimelineStayEntity stay = createTimelineStay(testUser, original);
        stayRepository.persist(stay);
        entityManager.flush();
        Long originalId = original.getId();
        entityManager.clear();

        ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                .displayName("Custom Location Name")
                .city("Custom City")
                .country("Custom Country")
                .build();

        // When
        GeocodingCopyOnWriteHandler.UpdateResult result = handler.handleUserUpdate(testUserId, original, updateDTO);

        // Then
        entityManager.flush();
        entityManager.clear();

        // Verify timeline was switched to reference the new copy
        TimelineStayEntity reloadedStay = stayRepository.findById(stay.getId());
        assertNotNull(reloadedStay);
        assertEquals("Custom Location Name", reloadedStay.getLocationName());
        assertNotEquals(originalId, reloadedStay.getGeocodingLocation().getId());
        assertEquals(result.entity().getId(), reloadedStay.getGeocodingLocation().getId());
    }

    // ==================== Test Suite 2: handleReconciliation() ====================

    @Test
    @DisplayName("Should return noChange when data unchanged")
    @Transactional
    void testReconciliationWithNoDataChanges() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        repository.persist(entity);
        entityManager.flush();
        entityManager.clear();

        FormattableGeocodingResult freshResult = createTestResult(
                "Test Location",  // Same
                "Test City",      // Same
                "Test Country"    // Same
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, entity, freshResult);

        // Then
        assertNotNull(result);
        assertFalse(result.changed());
        assertFalse(result.wasCopied());
        assertNull(result.originalId());
        assertEquals(entity, result.entity());

        // Verify no new entities created
        long count = repository.count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Should create copy when original data changed")
    @Transactional
    void testReconciliationCreatesUserCopy() {
        // Given
        ReverseGeocodingLocationEntity original = createOriginalEntity();
        repository.persist(original);
        entityManager.flush();
        Long originalId = original.getId();
        entityManager.clear();

        FormattableGeocodingResult freshResult = createTestResult(
                "New Name",
                "New City",
                "New Country"
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, original, freshResult);

        // Then
        assertNotNull(result);
        assertTrue(result.changed());
        assertTrue(result.wasCopied());
        assertEquals(originalId, result.originalId());

        // Verify original is unchanged
        entityManager.flush();
        entityManager.clear();
        ReverseGeocodingLocationEntity reloadedOriginal = repository.findById(originalId);
        assertNotNull(reloadedOriginal);
        assertEquals("Test Location", reloadedOriginal.getDisplayName());
        assertTrue(reloadedOriginal.isOriginal());

        // Verify user copy was created
        ReverseGeocodingLocationEntity userCopy = repository.findById(result.entity().getId());
        assertNotNull(userCopy);
        assertEquals("New Name", userCopy.getDisplayName());
        assertEquals("New City", userCopy.getCity());
        assertEquals("New Country", userCopy.getCountry());
        assertTrue(userCopy.isOwnedBy(testUserId));

        // Verify we now have 2 entities
        long count = repository.count();
        assertEquals(2, count);
    }

    @Test
    @DisplayName("Should update user copy in-place when data changed")
    @Transactional
    void testReconciliationUpdatesUserCopy() {
        // Given
        ReverseGeocodingLocationEntity userCopy = createUserOwnedEntity(testUser);
        repository.persist(userCopy);
        entityManager.flush();
        Long copyId = userCopy.getId();
        entityManager.clear();

        // Reload entity after clear
        ReverseGeocodingLocationEntity reloadedCopy = repository.findById(copyId);

        FormattableGeocodingResult freshResult = createTestResult(
                "Updated Name",
                "Updated City",
                "Updated Country"
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, reloadedCopy, freshResult);

        // Then
        assertNotNull(result);
        assertTrue(result.changed());
        assertFalse(result.wasCopied());
        assertNull(result.originalId());
        assertEquals(reloadedCopy, result.entity());

        // Verify entity was updated in database
        entityManager.flush();
        entityManager.clear();
        ReverseGeocodingLocationEntity reloaded = repository.findById(copyId);
        assertNotNull(reloaded);
        assertEquals("Updated Name", reloaded.getDisplayName());
        assertEquals("Updated City", reloaded.getCity());
        assertEquals("Updated Country", reloaded.getCountry());
        assertTrue(reloaded.isOwnedBy(testUserId));

        // Verify no new entities created
        long count = repository.count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Should sync timeline when reconciling creates copy")
    @Transactional
    void testTimelineSyncWhenReconciliationCreatesCopy() {
        // Given
        ReverseGeocodingLocationEntity original = createOriginalEntity();
        repository.persist(original);
        entityManager.flush();

        TimelineStayEntity stay = createTimelineStay(testUser, original);
        stayRepository.persist(stay);
        entityManager.flush();
        Long originalId = original.getId();
        entityManager.clear();

        FormattableGeocodingResult freshResult = createTestResult(
                "Reconciled Name",
                "Reconciled City",
                "Reconciled Country"
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, original, freshResult);

        // Then
        entityManager.flush();
        entityManager.clear();

        // Verify timeline was switched to reference the new copy
        TimelineStayEntity reloadedStay = stayRepository.findById(stay.getId());
        assertNotNull(reloadedStay);
        assertEquals("Reconciled Name", reloadedStay.getLocationName());
        assertNotEquals(originalId, reloadedStay.getGeocodingLocation().getId());
        assertEquals(result.entity().getId(), reloadedStay.getGeocodingLocation().getId());
    }

    @Test
    @DisplayName("Should sync timeline when reconciling updates user copy")
    @Transactional
    void testTimelineSyncWhenReconciliationUpdatesUserCopy() {
        // Given
        ReverseGeocodingLocationEntity userCopy = createUserOwnedEntity(testUser);
        repository.persist(userCopy);
        entityManager.flush();

        TimelineStayEntity stay = createTimelineStay(testUser, userCopy);
        stayRepository.persist(stay);
        entityManager.flush();
        Long copyId = userCopy.getId();
        Long stayId = stay.getId();
        entityManager.clear();

        // Reload entity after clear
        ReverseGeocodingLocationEntity reloadedCopy = repository.findById(copyId);

        FormattableGeocodingResult freshResult = createTestResult(
                "Reconciled Name",
                "Test City",
                "Test Country"
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, reloadedCopy, freshResult);

        // Then
        entityManager.flush();
        entityManager.clear();

        // Verify timeline was updated with new location name
        TimelineStayEntity reloadedStay = stayRepository.findById(stayId);
        assertNotNull(reloadedStay);
        assertEquals("Reconciled Name", reloadedStay.getLocationName());
        assertEquals(copyId, reloadedStay.getGeocodingLocation().getId());
    }

    // ==================== Test Suite 3: Data Change Detection ====================

    @Test
    @DisplayName("Data Change Detection: Should detect display name change")
    @Transactional
    void testDetectDisplayNameChange() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        repository.persist(entity);
        entityManager.flush();

        FormattableGeocodingResult freshResult = createTestResult(
                "New Display Name",  // Changed
                "Test City",
                "Test Country"
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, entity, freshResult);

        // Then
        assertTrue(result.changed(), "Should detect display name change");
        assertTrue(result.wasCopied());
    }

    @Test
    @DisplayName("Data Change Detection: Should detect city change")
    @Transactional
    void testDetectCityChange() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        repository.persist(entity);
        entityManager.flush();

        FormattableGeocodingResult freshResult = createTestResult(
                "Test Location",
                "New City",  // Changed
                "Test Country"
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, entity, freshResult);

        // Then
        assertTrue(result.changed(), "Should detect city change");
        assertTrue(result.wasCopied());
    }

    @Test
    @DisplayName("Data Change Detection: Should detect country change")
    @Transactional
    void testDetectCountryChange() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        repository.persist(entity);
        entityManager.flush();

        FormattableGeocodingResult freshResult = createTestResult(
                "Test Location",
                "Test City",
                "New Country"  // Changed
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, entity, freshResult);

        // Then
        assertTrue(result.changed(), "Should detect country change");
        assertTrue(result.wasCopied());
    }

    @Test
    @DisplayName("Data Change Detection: Should not detect change when all fields match")
    @Transactional
    void testNoChangeWhenAllFieldsMatch() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        repository.persist(entity);
        entityManager.flush();

        FormattableGeocodingResult freshResult = createTestResult(
                "Test Location",
                "Test City",
                "Test Country"
        );

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(testUserId, entity, freshResult);

        // Then
        assertFalse(result.changed(), "Should not detect change when all fields match");
        assertFalse(result.wasCopied());
    }

    // ==================== Test Suite 4: Complex Scenarios ====================

    @Test
    @DisplayName("Multiple users creating independent copies of same original")
    @Transactional
    void testMultipleUsersCreatingCopies() {
        // Given
        ReverseGeocodingLocationEntity original = createOriginalEntity();
        repository.persist(original);
        entityManager.flush();
        Long originalId = original.getId();

        ReverseGeocodingUpdateDTO user1Update = ReverseGeocodingUpdateDTO.builder()
                .displayName("User 1 Custom Name")
                .city("User 1 City")
                .country("User 1 Country")
                .build();

        ReverseGeocodingUpdateDTO user2Update = ReverseGeocodingUpdateDTO.builder()
                .displayName("User 2 Custom Name")
                .city("User 2 City")
                .country("User 2 Country")
                .build();

        // When
        GeocodingCopyOnWriteHandler.UpdateResult result1 = handler.handleUserUpdate(testUserId, original, user1Update);
        entityManager.flush();
        entityManager.clear();

        // Reload original for second user
        ReverseGeocodingLocationEntity reloadedOriginal = repository.findById(originalId);
        GeocodingCopyOnWriteHandler.UpdateResult result2 = handler.handleUserUpdate(otherUserId, reloadedOriginal, user2Update);

        // Then
        entityManager.flush();
        entityManager.clear();

        // Verify original still exists and unchanged
        ReverseGeocodingLocationEntity finalOriginal = repository.findById(originalId);
        assertNotNull(finalOriginal);
        assertEquals("Test Location", finalOriginal.getDisplayName());
        assertTrue(finalOriginal.isOriginal());

        // Verify both user copies exist and are different
        ReverseGeocodingLocationEntity copy1 = repository.findById(result1.entity().getId());
        ReverseGeocodingLocationEntity copy2 = repository.findById(result2.entity().getId());

        assertNotNull(copy1);
        assertNotNull(copy2);
        assertNotEquals(copy1.getId(), copy2.getId());

        assertEquals("User 1 Custom Name", copy1.getDisplayName());
        assertTrue(copy1.isOwnedBy(testUserId));

        assertEquals("User 2 Custom Name", copy2.getDisplayName());
        assertTrue(copy2.isOwnedBy(otherUserId));

        // Verify we have 3 total entities
        long count = repository.count();
        assertEquals(3, count);
    }

    @Test
    @DisplayName("User repeatedly updating their own copy")
    @Transactional
    void testRepeatedUpdatesToUserCopy() {
        // Given
        ReverseGeocodingLocationEntity userCopy = createUserOwnedEntity(testUser);
        repository.persist(userCopy);
        entityManager.flush();
        Long copyId = userCopy.getId();

        // When - Make multiple updates
        ReverseGeocodingUpdateDTO update1 = ReverseGeocodingUpdateDTO.builder()
                .displayName("Version 1")
                .build();
        handler.handleUserUpdate(testUserId, userCopy, update1);
        entityManager.flush();

        ReverseGeocodingUpdateDTO update2 = ReverseGeocodingUpdateDTO.builder()
                .displayName("Version 2")
                .build();
        handler.handleUserUpdate(testUserId, userCopy, update2);
        entityManager.flush();

        ReverseGeocodingUpdateDTO update3 = ReverseGeocodingUpdateDTO.builder()
                .displayName("Version 3")
                .build();
        handler.handleUserUpdate(testUserId, userCopy, update3);
        entityManager.flush();
        entityManager.clear();

        // Then
        ReverseGeocodingLocationEntity reloaded = repository.findById(copyId);
        assertNotNull(reloaded);
        assertEquals("Version 3", reloaded.getDisplayName());

        // Verify no additional copies were created
        long count = repository.count();
        assertEquals(1, count);
    }

    // ==================== Helper Methods ====================

    private ReverseGeocodingLocationEntity createOriginalEntity() {
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setRequestCoordinates(coords);
        entity.setResultCoordinates(coords);
        entity.setDisplayName("Test Location");
        entity.setCity("Test City");
        entity.setCountry("Test Country");
        entity.setProviderName("test_provider");
        entity.setCreatedAt(Instant.now());
        entity.setLastAccessedAt(Instant.now());
        entity.setUser(null); // Original entity
        return entity;
    }

    private ReverseGeocodingLocationEntity createUserOwnedEntity(UserEntity user) {
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        entity.setUser(user);
        return entity;
    }

    private FormattableGeocodingResult createTestResult(String displayName, String city, String country) {
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        return SimpleFormattableResult.builder()
                .requestCoordinates(coords)
                .resultCoordinates(coords)
                .formattedDisplayName(displayName)
                .city(city)
                .country(country)
                .providerName("test_provider")
                .build();
    }

    private TimelineStayEntity createTimelineStay(UserEntity user, ReverseGeocodingLocationEntity geocoding) {
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setUser(user);
        stay.setLocation(coords);
        stay.setTimestamp(Instant.now().minusSeconds(3600));
        stay.setStayDuration(3600L);
        stay.setGeocodingLocation(geocoding);
        stay.setLocationName(geocoding.getDisplayName());
        stay.setLocationSource(org.github.tess1o.geopulse.streaming.model.domain.LocationSource.GEOCODING);
        stay.setCreatedAt(Instant.now());
        stay.setLastUpdated(Instant.now());
        return stay;
    }
}
