package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingUpdateDTO;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for ReverseGeocodingManagementService.
 * Tests the sophisticated copy-on-write logic, authorization, and timeline synchronization.
 * <p>
 * NO MOCKS - Uses real database with all services integrated.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReverseGeocodingManagementServiceTest {

    @Inject
    ReverseGeocodingManagementService managementService;

    @Inject
    ReverseGeocodingLocationRepository repository;

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
                .email("user-a-mgmt-test@example.com")
                .fullName("User A")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(userA);

        UserEntity userB = UserEntity.builder()
                .email("user-b-mgmt-test@example.com")
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

    // ==================== Copy-on-Write: User Modifying Original ====================

    @Test
    @Order(1)
    @Transactional
    @DisplayName("COW Test 1: Modifying original creates user copy")
    void testModifyingOriginalCreatesUserCopy() {
        // Given: Original entity
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        entityManager.flush();

        Long originalId = original.getId();

        // When: User A modifies it
        ReverseGeocodingUpdateDTO updateDTO = new ReverseGeocodingUpdateDTO();
        updateDTO.setDisplayName("My Coffee Shop");
        updateDTO.setCity("New York");
        updateDTO.setCountry("USA");

        ReverseGeocodingDTO result = managementService.updateGeocodingResult(USER_A_ID, originalId, updateDTO);

        // Then: New user-specific copy created
        assertNotNull(result);
        assertNotEquals(originalId, result.getId(), "Should create new entity");
        assertEquals("My Coffee Shop", result.getDisplayName());
        assertTrue(result.isUserSpecific(), "Should be user-specific");

        // And: Original still exists unchanged
        ReverseGeocodingLocationEntity originalCheck = repository.findById(originalId);
        assertNotNull(originalCheck, "Original should still exist");
        assertEquals("Starbucks", originalCheck.getDisplayName(), "Original unchanged");
        assertTrue(originalCheck.isOriginal(), "Should still be original");
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("COW Test 2: Timeline stays remapped to user copy")
    void testTimelineStaysRemappedToUserCopy() {
        // Given: Original with timeline stay
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        entityManager.flush();

        createTimelineStay(USER_A_ID, original.getId(), coords, "Starbucks");

        // When: User A modifies original
        ReverseGeocodingUpdateDTO updateDTO = new ReverseGeocodingUpdateDTO();
        updateDTO.setDisplayName("My Coffee Shop");
        updateDTO.setCity("New York");
        updateDTO.setCountry("USA");

        ReverseGeocodingDTO result = managementService.updateGeocodingResult(USER_A_ID, original.getId(), updateDTO);

        // Then: Timeline stay now references new copy
        List<Object[]> stays = getTimelineStaysForUser(USER_A_ID);
        assertEquals(1, stays.size(), "Should have 1 timeline stay");

        Long geocodingId = ((Number) stays.get(0)[0]).longValue();
        String locationName = (String) stays.get(0)[1];

        assertEquals(result.getId(), geocodingId, "Should reference new copy");
        assertEquals("My Coffee Shop", locationName, "Should have updated name");
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("COW Test 3: User B's timeline stays unchanged after User A modifies")
    void testUserBTimelineStaysUnchangedWhenUserAModifies() {
        // Given: Original with timeline stays for both users
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        entityManager.flush();

        createTimelineStay(USER_A_ID, original.getId(), coords, "Starbucks");
        createTimelineStay(USER_B_ID, original.getId(), coords, "Starbucks");

        // When: User A modifies
        ReverseGeocodingUpdateDTO updateDTO = new ReverseGeocodingUpdateDTO();
        updateDTO.setDisplayName("My Coffee Shop");
        updateDTO.setCity("New York");
        updateDTO.setCountry("USA");

        managementService.updateGeocodingResult(USER_A_ID, original.getId(), updateDTO);

        // Then: User B's timeline stay still references original
        List<Object[]> userBStays = getTimelineStaysForUser(USER_B_ID);
        assertEquals(1, userBStays.size(), "User B should have 1 stay");

        Long userBGeocodingId = ((Number) userBStays.get(0)[0]).longValue();
        String userBLocationName = (String) userBStays.get(0)[1];

        assertEquals(original.getId(), userBGeocodingId, "User B should still reference original");
        assertEquals("Starbucks", userBLocationName, "User B's name unchanged");
    }

    // ==================== Copy-on-Write: User Modifying Own Copy ====================

    @Test
    @Order(4)
    @Transactional
    @DisplayName("COW Test 4: Modifying own copy updates in-place")
    void testModifyingOwnCopyUpdatesInPlace() {
        // Given: User A's custom copy
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity userCopy = createUserCopy(USER_A_ID, coords, "My Coffee Shop");
        repository.persist(userCopy);
        entityManager.flush();

        Long copyId = userCopy.getId();

        // When: User A modifies their own copy
        ReverseGeocodingUpdateDTO updateDTO = new ReverseGeocodingUpdateDTO();
        updateDTO.setDisplayName("My Updated Coffee Shop");
        updateDTO.setCity("Brooklyn");
        updateDTO.setCountry("USA");

        ReverseGeocodingDTO result = managementService.updateGeocodingResult(USER_A_ID, copyId, updateDTO);

        // Then: Same entity updated in-place
        assertEquals(copyId, result.getId(), "Should be same entity ID");
        assertEquals("My Updated Coffee Shop", result.getDisplayName());
        assertEquals("Brooklyn", result.getCity());

        // And: No new entity created
        long countAfter = repository.count();
        assertEquals(1, countAfter, "Should still have only 1 entity");
    }

    // ==================== Authorization Tests ====================

    @Test
    @Order(5)
    @Transactional
    @DisplayName("Auth Test 1: User cannot modify another user's copy")
    void testUserCannotModifyAnotherUsersCopy() {
        // Given: User A's custom copy
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity userACopy = createUserCopy(USER_A_ID, coords, "My Coffee Shop");
        repository.persist(userACopy);
        entityManager.flush();

        // When: User B tries to modify it
        ReverseGeocodingUpdateDTO updateDTO = new ReverseGeocodingUpdateDTO();
        updateDTO.setDisplayName("Hacked");
        updateDTO.setCity("New York");
        updateDTO.setCountry("USA");

        // Then: 403 Forbidden
        assertThrows(ForbiddenException.class, () -> {
            managementService.updateGeocodingResult(USER_B_ID, userACopy.getId(), updateDTO);
        }, "Should throw ForbiddenException");

        // And: Entity unchanged
        ReverseGeocodingLocationEntity checkEntity = repository.findById(userACopy.getId());
        assertEquals("My Coffee Shop", checkEntity.getDisplayName(), "Should be unchanged");
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("Auth Test 2: User cannot access another user's copy")
    void testUserCannotAccessAnotherUsersCopy() {
        // Given: User A's custom copy
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity userACopy = createUserCopy(USER_A_ID, coords, "My Coffee Shop");
        repository.persist(userACopy);
        entityManager.flush();

        // When: User B tries to access it
        // Then: 403 Forbidden
        assertThrows(ForbiddenException.class, () -> {
            managementService.getGeocodingResult(USER_B_ID, userACopy.getId());
        }, "Should throw ForbiddenException");
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("Auth Test 3: User can access originals")
    void testUserCanAccessOriginals() {
        // Given: Original entity
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        entityManager.flush();

        // When: User A accesses it
        ReverseGeocodingDTO result = managementService.getGeocodingResult(USER_A_ID, original.getId());

        // Then: Success
        assertNotNull(result);
        assertEquals("Starbucks", result.getDisplayName());
        assertFalse(result.isUserSpecific(), "Should not be user-specific");
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("Auth Test 4: Non-existent entity throws NotFoundException")
    void testNonExistentEntityThrowsNotFound() {
        // When: Access non-existent entity
        // Then: 404 Not Found
        assertThrows(NotFoundException.class, () -> {
            managementService.getGeocodingResult(USER_A_ID, 999999L);
        }, "Should throw NotFoundException");
    }

    // ==================== Management Page Tests ====================

    @Test
    @Order(9)
    @Transactional
    @DisplayName("Management Test 1: User sees only relevant entities")
    void testManagementPageShowsRelevantEntitiesOnly() {
        // Given: Multiple entities
        Point loc1 = GeoUtils.createPoint(-73.9851, 40.7589);
        Point loc2 = GeoUtils.createPoint(-73.9654, 40.7829);
        Point loc3 = GeoUtils.createPoint(-74.0060, 40.7128);

        // Original referenced by User A
        ReverseGeocodingLocationEntity original1 = createOriginal(loc1, "Location 1");
        repository.persist(original1);

        // User A's copy
        ReverseGeocodingLocationEntity userACopy = createUserCopy(USER_A_ID, loc2, "My Location 2");
        repository.persist(userACopy);

        // Unreferenced original (should not appear)
        ReverseGeocodingLocationEntity original3 = createOriginal(loc3, "Location 3 Unreferenced");
        repository.persist(original3);

        // User B's copy (should not appear for User A)
        ReverseGeocodingLocationEntity userBCopy = createUserCopy(USER_B_ID, loc3, "User B Location");
        repository.persist(userBCopy);

        entityManager.flush();

        createTimelineStay(USER_A_ID, original1.getId(), loc1, "Location 1");

        // When: User A fetches management page
        List<ReverseGeocodingDTO> results = managementService.getGeocodingResults(
                USER_A_ID, null, null, 1, 50, "lastAccessedAt", "desc");

        // Then: Only 2 entities (referenced original + own copy)
        assertEquals(2, results.size(), "Should show 2 entities");

        List<Long> resultIds = results.stream().map(ReverseGeocodingDTO::getId).toList();
        assertTrue(resultIds.contains(original1.getId()), "Should include referenced original");
        assertTrue(resultIds.contains(userACopy.getId()), "Should include own copy");
        assertFalse(resultIds.contains(original3.getId()), "Should NOT include unreferenced original");
        assertFalse(resultIds.contains(userBCopy.getId()), "Should NOT include user B's copy");
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("Management Test 2: Count matches results")
    void testCountMatchesResults() {
        // Given: 3 entities for user A
        for (int i = 0; i < 3; i++) {
            Point coords = GeoUtils.createPoint(-73.9 - i * 0.01, 40.7 + i * 0.01);
            ReverseGeocodingLocationEntity entity = createUserCopy(USER_A_ID, coords, "Location " + i);
            repository.persist(entity);
        }

        entityManager.flush();

        // When: Get count and results
        long count = managementService.countGeocodingResults(USER_A_ID, null, null);
        List<ReverseGeocodingDTO> results = managementService.getGeocodingResults(
                USER_A_ID, null, null, 1, 50, "lastAccessedAt", "desc");

        // Then: Count matches
        assertEquals(3, count);
        assertEquals(3, results.size());
        assertEquals(count, results.size());
    }

    // ==================== Concurrent Modification Tests ====================

    @Test
    @Order(11)
    @Transactional
    @DisplayName("Concurrency Test: Two users modifying same original creates separate copies")
    void testTwoUsersModifyingSameOriginalCreatesSeparateCopies() {
        // Given: Original
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        entityManager.flush();

        Long originalId = original.getId();

        // When: User A modifies
        ReverseGeocodingUpdateDTO updateA = new ReverseGeocodingUpdateDTO();
        updateA.setDisplayName("My Coffee Shop");
        updateA.setCity("New York");
        updateA.setCountry("USA");

        ReverseGeocodingDTO resultA = managementService.updateGeocodingResult(USER_A_ID, originalId, updateA);

        // And: User B modifies
        ReverseGeocodingUpdateDTO updateB = new ReverseGeocodingUpdateDTO();
        updateB.setDisplayName("Mon Café");
        updateB.setCity("New York");
        updateB.setCountry("USA");

        ReverseGeocodingDTO resultB = managementService.updateGeocodingResult(USER_B_ID, originalId, updateB);

        // Then: Three entities exist (original + 2 copies)
        assertEquals(3, repository.count(), "Should have 3 entities");

        // And: Each user has their own copy
        assertNotEquals(resultA.getId(), resultB.getId(), "Should be different copies");
        assertEquals("My Coffee Shop", resultA.getDisplayName());
        assertEquals("Mon Café", resultB.getDisplayName());

        // And: Original unchanged
        ReverseGeocodingLocationEntity originalCheck = repository.findById(originalId);
        assertEquals("Starbucks", originalCheck.getDisplayName(), "Original unchanged");
    }

    // ==================== Helper Methods ====================

    private ReverseGeocodingLocationEntity createOriginal(Point coords, String displayName) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setUser(null);
        entity.setRequestCoordinates(coords);
        entity.setResultCoordinates(coords);
        entity.setDisplayName(displayName);
        entity.setProviderName("google");
        entity.setCity("New York");
        entity.setCountry("USA");
        entity.setCreatedAt(Instant.now());
        entity.setLastAccessedAt(Instant.now());
        return entity;
    }

    private ReverseGeocodingLocationEntity createUserCopy(UUID userId, Point coords, String displayName) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setUser(entityManager.getReference(UserEntity.class, userId));
        entity.setRequestCoordinates(coords);
        entity.setResultCoordinates(coords);
        entity.setDisplayName(displayName);
        entity.setProviderName("google");
        entity.setCity("New York");
        entity.setCountry("USA");
        entity.setCreatedAt(Instant.now());
        entity.setLastAccessedAt(Instant.now());
        return entity;
    }

    private void createTimelineStay(UUID userId, Long geocodingId, Point coords, String locationName) {
        String sql = """
                INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name,
                                           geocoding_id, created_at, last_updated, location_source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'GEOCODING')
                """;

        entityManager.createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, Instant.now())
                .setParameter(3, 300L)
                .setParameter(4, coords)
                .setParameter(5, locationName)
                .setParameter(6, geocodingId)
                .setParameter(7, Instant.now())
                .setParameter(8, Instant.now())
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> getTimelineStaysForUser(UUID userId) {
        String sql = "SELECT geocoding_id, location_name FROM timeline_stays WHERE user_id = ? ORDER BY id";
        return entityManager.createNativeQuery(sql)
                .setParameter(1, userId)
                .getResultList();
    }
}
