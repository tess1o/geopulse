package org.github.tess1o.geopulse.geocoding.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Comprehensive integration tests for ReverseGeocodingLocationRepository.
 * Tests user filtering, spatial queries, copy-on-write scenarios, and edge cases.
 * <p>
 * NO MOCKS - Uses real PostgreSQL/PostGIS database.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ReverseGeocodingLocationRepositoryTest {

    @Inject
    ReverseGeocodingLocationRepository repository;

    @Inject
    EntityManager entityManager;

    private UUID USER_A_ID;
    private UUID USER_B_ID;
    private UserEntity userA;
    private UserEntity userB;
    private TestCoordinates.Scope coordinateScope;
    // Test coordinates

    private static final double STARBUCKS_LAT = 40.7589;
    private static final double STARBUCKS_LON = -73.9851;
    private static final double CENTRAL_PARK_LAT = 40.7829;
    private static final double CENTRAL_PARK_LON = -73.9654;

    @BeforeEach
    @Transactional
    void setupUsers() {
        coordinateScope = TestCoordinates.newScope();

        // Create test users
        userA = UserEntity.builder()
                .email(TestIds.uniqueEmail("it-user"))
                .fullName("User A")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(userA);
        userB = UserEntity.builder()
                .email(TestIds.uniqueEmail("it-user"))
                .fullName("User B")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(userB);
        entityManager.flush();
        USER_A_ID = userA.getId();
        USER_B_ID = userB.getId();
    }

    // ==================== User Filtering Tests ====================
    @Test
    @Transactional
    @DisplayName("Test 1: User-specific copy is prioritized over original")
    void testUserSpecificCopyPrioritized() {
        // Given: Original at Starbucks location
        Point coords = coord(STARBUCKS_LON, STARBUCKS_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        // And: User A's custom copy at same location
        ReverseGeocodingLocationEntity userACopy = createUserCopy(USER_A_ID, coords, "My Coffee Shop");
        repository.persist(userACopy);
        entityManager.flush();
        // When: User A searches
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(USER_A_ID, coords, 25.0);
        // Then: User A sees their copy
        assertNotNull(result, "Should find a result");
        assertEquals("My Coffee Shop", result.getDisplayName(), "Should return user-specific copy");
        assertEquals(userACopy.getId(), result.getId(), "Should return user A's copy ID");
        assertTrue(result.isOwnedBy(USER_A_ID), "Should be owned by user A");
    }

    @Test
    @Transactional
    @DisplayName("Test 2: User B sees original when user A has custom copy")
    void testUserBSeesOriginalWhenUserAHasCopy() {
        // Given: Original at Starbucks
        Point coords = coord(STARBUCKS_LON, STARBUCKS_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        // And: User A's custom copy
        ReverseGeocodingLocationEntity userACopy = createUserCopy(USER_A_ID, coords, "My Coffee Shop");
        repository.persist(userACopy);
        entityManager.flush();
        // When: User B searches
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(USER_B_ID, coords, 25.0);
        // Then: User B sees original (not user A's copy)
        assertNotNull(result, "Should find a result");
        assertEquals("Starbucks", result.getDisplayName(), "Should return original");
        assertEquals(original.getId(), result.getId(), "Should return original ID");
        assertTrue(result.isOriginal(), "Should be original");
    }

    @Test
    @Transactional
    @DisplayName("Test 3: User sees original when no custom copy exists")
    void testUserSeesOriginalWhenNoCopyExists() {
        // Given: Only original exists
        Point coords = coord(STARBUCKS_LON, STARBUCKS_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        entityManager.flush();
        // When: User A searches
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(USER_A_ID, coords, 25.0);
        // Then: User A sees original
        assertNotNull(result, "Should find a result");
        assertEquals("Starbucks", result.getDisplayName(), "Should return original");
        assertTrue(result.isOriginal(), "Should be original");
    }

    @Test
    @Transactional
    @DisplayName("Test 4: User cannot see another user's custom copy")
    void testUserCannotSeeAnotherUsersCopy() {
        // Given: Only User A's custom copy exists (no original)
        Point coords = coord(STARBUCKS_LON, STARBUCKS_LAT);
        ReverseGeocodingLocationEntity userACopy = createUserCopy(USER_A_ID, coords, "My Coffee Shop");
        repository.persist(userACopy);
        entityManager.flush();
        // When: User B searches
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(USER_B_ID, coords, 25.0);
        // Then: User B does not see user A's copy
        if (result != null) {
            assertNotEquals(userACopy.getId(), result.getId(), "User B must not receive user A's copy");
            assertTrue(result.isOriginal(), "Fallback result may only be an original");
        }
    }

    // ==================== Batch Query Tests ====================
    @Test
    @Transactional
    @DisplayName("Test 5: Batch query prioritizes user-specific copies")
    void testBatchQueryPrioritizesUserCopies() {
        // Given: Two locations
        Point starbucks = coord(STARBUCKS_LON, STARBUCKS_LAT);
        Point centralPark = coord(CENTRAL_PARK_LON, CENTRAL_PARK_LAT);
        // Starbucks: Original + User A's copy
        ReverseGeocodingLocationEntity starbucksOriginal = createOriginal(starbucks, "Starbucks");
        repository.persist(starbucksOriginal);
        ReverseGeocodingLocationEntity starbucksUserCopy = createUserCopy(USER_A_ID, starbucks, "My Coffee Shop");
        repository.persist(starbucksUserCopy);
        // Central Park: Only original
        ReverseGeocodingLocationEntity parkOriginal = createOriginal(centralPark, "Central Park");
        repository.persist(parkOriginal);
        entityManager.flush();
        // When: User A batch searches
        Map<String, ReverseGeocodingLocationEntity> results = repository.findByCoordinatesBatchReal(
                USER_A_ID, List.of(starbucks, centralPark), 25.0);
        // Then: Returns user copy for Starbucks, original for Central Park
        assertEquals(2, results.size(), "Should find both locations");
        String starbucksKey = starbucks.getX() + "," + starbucks.getY();
        String parkKey = centralPark.getX() + "," + centralPark.getY();
        assertEquals("My Coffee Shop", results.get(starbucksKey).getDisplayName(),
                "Should return user copy for Starbucks");
        assertEquals("Central Park", results.get(parkKey).getDisplayName(),
                "Should return original for Central Park");
    }

    @Test
    @Transactional
    @DisplayName("Test 6: Batch query filters out other users' copies")
    void testBatchQueryFiltersOtherUsersCopies() {
        // Given: User A has custom copy, User B searches
        Point coords = coord(STARBUCKS_LON, STARBUCKS_LAT);
        ReverseGeocodingLocationEntity userACopy = createUserCopy(USER_A_ID, coords, "My Coffee Shop");
        repository.persist(userACopy);
        entityManager.flush();
        // When: User B batch searches
        Map<String, ReverseGeocodingLocationEntity> results = repository.findByCoordinatesBatchReal(
                USER_B_ID, List.of(coords), 25.0);
        // Then: User B does not see user A's copy
        String coordsKey = coords.getX() + "," + coords.getY();
        ReverseGeocodingLocationEntity result = results.get(coordsKey);
        if (result != null) {
            assertNotEquals(userACopy.getId(), result.getId(), "User B must not receive user A's copy in batch mode");
            assertTrue(result.isOriginal(), "Fallback result may only be an original");
        }
    }

    // ==================== Management Page Tests ====================
    @Test
    @Transactional
    @DisplayName("Test 7: Management page shows user's copies and referenced originals")
    void testManagementPageShowsRelevantEntities() {
        // Given: Three geocoding entities
        Point loc1 = coord(-73.9851, 40.7589);
        Point loc2 = coord(-73.9654, 40.7829);
        Point loc3 = coord(-74.0060, 40.7128);
        // Location 1: Original referenced in User A's timeline
        ReverseGeocodingLocationEntity loc1Original = createOriginal(loc1, "Location 1 Original");
        repository.persist(loc1Original);
        // Location 2: User A's custom copy
        ReverseGeocodingLocationEntity loc2UserCopy = createUserCopy(USER_A_ID, loc2, "Location 2 Custom");
        repository.persist(loc2UserCopy);
        // Location 3: Original NOT referenced by anyone (should not appear)
        ReverseGeocodingLocationEntity loc3Original = createOriginal(loc3, "Location 3 Unreferenced");
        repository.persist(loc3Original);
        entityManager.flush();
        // Create timeline stay for User A referencing loc1Original
        createTimelineStay(USER_A_ID, loc1Original.getId(), loc1, "Location 1 Original");
        // When: User A fetches management page
        List<ReverseGeocodingLocationEntity> results = repository.findForUserManagementPage(
                USER_A_ID, null, null, null, null, 1, 50, null, null);
        // Then: Should see loc1 (referenced) and loc2 (owned), but NOT loc3
        assertEquals(2, results.size(), "Should show 2 entities");
        List<Long> resultIds = results.stream().map(ReverseGeocodingLocationEntity::getId).toList();
        assertTrue(resultIds.contains(loc1Original.getId()), "Should include referenced original");
        assertTrue(resultIds.contains(loc2UserCopy.getId()), "Should include owned copy");
        assertFalse(resultIds.contains(loc3Original.getId()), "Should NOT include unreferenced original");
    }

    @Test
    @Transactional
    @DisplayName("Test 8: Management page filters by provider")
    void testManagementPageFiltersByProvider() {
        // Given: Two entities with different providers
        Point coords1 = coord(-73.9851, 40.7589);
        Point coords2 = coord(-73.9654, 40.7829);
        ReverseGeocodingLocationEntity googleEntity = createUserCopy(USER_A_ID, coords1, "Google Location");
        googleEntity.setProviderName("google");
        repository.persist(googleEntity);
        ReverseGeocodingLocationEntity nominatimEntity = createUserCopy(USER_A_ID, coords2, "Nominatim Location");
        nominatimEntity.setProviderName("nominatim");
        repository.persist(nominatimEntity);
        entityManager.flush();
        // When: Filter by google provider
        List<ReverseGeocodingLocationEntity> results = repository.findForUserManagementPage(
                USER_A_ID, "google", null, null, null, 1, 50, null, null);
        // Then: Only google entity returned
        assertEquals(1, results.size(), "Should find only google entity");
        assertEquals("google", results.get(0).getProviderName(), "Should be google provider");
    }

    @Test
    @Transactional
    @DisplayName("Test 9: Management page search text filters display names")
    void testManagementPageSearchText() {
        // Given: Two entities with different names
        Point coords1 = coord(-73.9851, 40.7589);
        Point coords2 = coord(-73.9654, 40.7829);
        ReverseGeocodingLocationEntity coffee = createUserCopy(USER_A_ID, coords1, "My Coffee Shop");
        repository.persist(coffee);
        ReverseGeocodingLocationEntity park = createUserCopy(USER_A_ID, coords2, "Central Park");
        repository.persist(park);
        entityManager.flush();
        // When: Search for "coffee"
        List<ReverseGeocodingLocationEntity> results = repository.findForUserManagementPage(
                USER_A_ID, null, null, null, "coffee", 1, 50, null, null);
        // Then: Only coffee shop returned
        assertEquals(1, results.size(), "Should find only coffee shop");
        assertTrue(results.get(0).getDisplayName().toLowerCase().contains("coffee"),
                "Should contain 'coffee' in name");
    }
    // ==================== Spatial Query Tests ====================
    @Test
    @Transactional
    @DisplayName("Test 10: Spatial query finds entity within tolerance")
    void testSpatialQueryWithinTolerance() {
        // Given: Entity at exact coordinates
        Point exactCoords = coord(STARBUCKS_LON, STARBUCKS_LAT);
        ReverseGeocodingLocationEntity entity = createOriginal(exactCoords, "Starbucks");
        repository.persist(entity);
        entityManager.flush();
        // When: Search with slightly different coordinates (within 25m tolerance)
        Point nearbyCoords = coord(STARBUCKS_LON + 0.0001, STARBUCKS_LAT + 0.0001);
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(USER_A_ID, nearbyCoords, 25.0);
        // Then: Should find entity
        assertNotNull(result, "Should find entity within tolerance");
        assertEquals("Starbucks", result.getDisplayName());
    }

    @Test
    @Transactional
    @DisplayName("Test 11: Spatial query returns null outside tolerance")
    void testSpatialQueryOutsideTolerance() {
        // Given: Entity at Starbucks
        Point starbucksCoords = coord(STARBUCKS_LON, STARBUCKS_LAT);
        ReverseGeocodingLocationEntity entity = createOriginal(starbucksCoords, "Starbucks");
        repository.persist(entity);
        entityManager.flush();
        // When: Search at Central Park (far away)
        Point parkCoords = coord(CENTRAL_PARK_LON, CENTRAL_PARK_LAT);
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(USER_A_ID, parkCoords, 25.0);
        // Then: Must not return the Starbucks entity created by this test
        if (result != null) {
            assertNotEquals(entity.getId(), result.getId(),
                    "Query outside tolerance must not return the created Starbucks entity");
        }
    }

    @Test
    @Transactional
    @DisplayName("Test 11b: Spatial query ignores oversized bounding boxes")
    void testSpatialQueryIgnoresOversizedBoundingBox() {
        Point requestCoords = coord(-73.9851, 40.7589);
        Point queryCoords = coord(-73.9654, 40.7829);

        // ~9,300km² around NYC, intentionally above the 5,000km² guard.
        Polygon oversizedBbox = GeoUtils.buildBoundingBoxPolygon(40.60, 41.50, -74.30, -73.00);
        ReverseGeocodingLocationEntity entity = createOriginalWithBoundingBox(requestCoords, "Oversized BBox", oversizedBbox);
        repository.persist(entity);
        entityManager.flush();

        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(
                USER_A_ID, queryCoords, 25.0, 5_000_000_000d);

        if (result != null) {
            assertNotEquals(entity.getId(), result.getId(),
                    "Oversized bbox containment should be ignored");
        }
    }

    @Test
    @Transactional
    @DisplayName("Test 11c: Spatial query uses safe bounding boxes")
    void testSpatialQueryUsesSafeBoundingBox() {
        // Use coordinates in a distinct area and force bbox-only matching:
        // query point sits inside bbox, but outside distance tolerance from request/result coordinates.
        Point requestCoords = coord(12.3000, 45.6000);
        Point queryCoords = coord(12.3456, 45.6786);

        Polygon safeBbox = GeoUtils.buildBoundingBoxPolygon(
                queryCoords.getY() - 0.0001,
                queryCoords.getY() + 0.0001,
                queryCoords.getX() - 0.0001,
                queryCoords.getX() + 0.0001);
        ReverseGeocodingLocationEntity entity = createOriginalWithBoundingBox(requestCoords, "Safe BBox", safeBbox);
        repository.persist(entity);
        entityManager.flush();

        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(
                USER_A_ID, queryCoords, 1.0, 5_000_000_000d);

        assertNotNull(result, "Safe bbox should still be usable for containment");
        assertEquals(entity.getId(), result.getId());
    }

    @Test
    @Transactional
    @DisplayName("Test 11d: Distance match is prioritized over bbox-only match")
    void testDistanceMatchPrioritizedOverBboxOnlyMatch() {
        Point queryCoords = coord(-73.9851, 40.7589);
        Point distanceMatchCoords = coord(-73.9851005, 40.7589005);
        Point bboxOnlyCoords = coord(-73.9700, 40.7700);

        ReverseGeocodingLocationEntity distanceMatch = createOriginal(distanceMatchCoords, "Distance Winner");
        repository.persist(distanceMatch);

        Polygon safeBbox = GeoUtils.buildBoundingBoxPolygon(40.7580, 40.7600, -73.9860, -73.9840);
        ReverseGeocodingLocationEntity bboxOnly = createOriginalWithBoundingBox(bboxOnlyCoords, "BBox Candidate", safeBbox);
        repository.persist(bboxOnly);
        entityManager.flush();

        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(
                USER_A_ID, queryCoords, 25.0, 5_000_000_000d);

        assertNotNull(result);
        assertEquals(distanceMatch.getId(), result.getId(), "Distance match must be ranked before bbox-only match");
    }

    // ==================== Edge Case Tests ====================
    @Test
    @Transactional
    @DisplayName("Test 12: Empty batch query returns empty map")
    void testEmptyBatchQuery() {
        // When: Search with empty coordinates list
        Map<String, ReverseGeocodingLocationEntity> results = repository.findByCoordinatesBatchReal(
                USER_A_ID, List.of(), 25.0);
        // Then: Returns empty map
        assertNotNull(results, "Should return non-null map");
        assertTrue(results.isEmpty(), "Should be empty");
    }

    @Test
    @Transactional
    @DisplayName("Test 13: Null coordinates returns null")
    void testNullCoordinates() {
        // When: Search with null coordinates
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(USER_A_ID, null, 25.0);
        // Then: Returns null (handled gracefully)
        assertNull(result, "Should handle null coordinates gracefully");
    }

    @Test
    @Transactional
    @DisplayName("Test 14: Multiple user copies - each user sees their own")
    void testMultipleUserCopiesAtSameLocation() {
        // Given: Original + two user copies at same location
        Point coords = coord(STARBUCKS_LON, STARBUCKS_LAT);
        ReverseGeocodingLocationEntity original = createOriginal(coords, "Starbucks");
        repository.persist(original);
        ReverseGeocodingLocationEntity userACopy = createUserCopy(USER_A_ID, coords, "My Coffee Shop");
        repository.persist(userACopy);
        ReverseGeocodingLocationEntity userBCopy = createUserCopy(USER_B_ID, coords, "Mon Café");
        repository.persist(userBCopy);
        entityManager.flush();
        // When: Each user searches
        ReverseGeocodingLocationEntity resultA = repository.findByRequestCoordinates(USER_A_ID, coords, 25.0);
        ReverseGeocodingLocationEntity resultB = repository.findByRequestCoordinates(USER_B_ID, coords, 25.0);
        // Then: Each sees their own copy
        assertEquals("My Coffee Shop", resultA.getDisplayName(), "User A should see their copy");
        assertEquals("Mon Café", resultB.getDisplayName(), "User B should see their copy");
        assertTrue(resultA.isOwnedBy(USER_A_ID), "Should be owned by user A");
        assertTrue(resultB.isOwnedBy(USER_B_ID), "Should be owned by user B");
    }

    @Test
    @Transactional
    @DisplayName("Test 15: Count query matches find query")
    void testCountMatchesFindQuery() {
        // Given: 5 entities for user A
        for (int i = 0; i < 5; i++) {
            Point coords = coord(-73.9 - i * 0.01, 40.7 + i * 0.01);
            ReverseGeocodingLocationEntity entity = createUserCopy(USER_A_ID, coords, "Location " + i);
            repository.persist(entity);
        }
        entityManager.flush();
        // When: Get count and entities
        long count = repository.countForUserManagementPage(USER_A_ID, null, null, null, null);
        List<ReverseGeocodingLocationEntity> results = repository.findForUserManagementPage(
                USER_A_ID, null, null, null, null, 1, 50, null, null);
        // Then: Count matches list size
        assertEquals(5, count, "Count should be 5");
        assertEquals(5, results.size(), "Should find 5 entities");
        assertEquals(count, results.size(), "Count should match results size");
    }

    // ==================== Helper Methods ====================
    private ReverseGeocodingLocationEntity createOriginal(Point coords, String displayName) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setUser(null); // NULL = original
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

    private ReverseGeocodingLocationEntity createOriginalWithBoundingBox(Point coords, String displayName, Polygon boundingBox) {
        ReverseGeocodingLocationEntity entity = createOriginal(coords, displayName);
        entity.setBoundingBox(boundingBox);
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

    private Point coord(double lon, double lat) {
        return coordinateScope.point(lon, lat);
    }
}
