package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CacheGeocodingBatchService.
 * Tests batch operations, timeout handling, and user filtering.
 *
 * Coverage: 10 test cases across batch lookup methods
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("CacheGeocodingBatchService Integration Tests")
class CacheGeocodingBatchServiceTest {

    @Inject
    CacheGeocodingBatchService batchService;

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
                .email("user-a-batch-test@example.com")
                .fullName("User A")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(userA);

        UserEntity userB = UserEntity.builder()
                .email("user-b-batch-test@example.com")
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


    @Test
    @Order(1)
    @DisplayName("Should find all coordinates when fully cached")
    void testBatchLookupAllCached() {
        // Given - setup in separate transaction
        Point coords1 = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        Point coords2 = GeoUtils.createPoint(TEST_LON + 0.01, TEST_LAT + 0.01);
        Point coords3 = GeoUtils.createPoint(TEST_LON + 0.02, TEST_LAT + 0.02);

        Long id1 = setupGeocodingEntity(coords1, "Location 1", null);
        Long id2 = setupGeocodingEntity(coords2, "Location 2", null);
        Long id3 = setupGeocodingEntity(coords3, "Location 3", null);

        List<Point> lookupCoords = List.of(coords1, coords2, coords3);

        // When
        Map<String, Long> result = batchService.getCachedGeocodingResultIdsBatch(USER_A_ID, lookupCoords);

        // Then
        assertEquals(3, result.size());
        assertTrue(result.containsValue(id1));
        assertTrue(result.containsValue(id2));
        assertTrue(result.containsValue(id3));
    }

    @Test
    @Order(2)
    @DisplayName("Should handle partial cache hits")
    void testBatchLookupPartialCacheHits() {
        // Given
        Point cachedCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        Point uncachedCoords = GeoUtils.createPoint(TEST_LON + 10, TEST_LAT + 10); // Far away

        Long cachedId = setupGeocodingEntity(cachedCoords, "Cached", null);

        List<Point> lookupCoords = List.of(cachedCoords, uncachedCoords);

        // When
        Map<String, Long> result = batchService.getCachedGeocodingResultIdsBatch(USER_A_ID, lookupCoords);

        // Then
        assertEquals(1, result.size(), "Should only find the cached coordinate");
        assertTrue(result.containsValue(cachedId));
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("Should return empty map when no cache hits")
    void testBatchLookupNoCacheHits() {
        // Given
        Point uncachedCoords1 = GeoUtils.createPoint(100.0, 50.0);
        Point uncachedCoords2 = GeoUtils.createPoint(110.0, 60.0);

        List<Point> lookupCoords = List.of(uncachedCoords1, uncachedCoords2);

        // When
        Map<String, Long> result = batchService.getCachedGeocodingResultIdsBatch(USER_A_ID, lookupCoords);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("Should prioritize user-specific copies over originals")
    void testUserFilteringPrioritizesUserCopies() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);

        Long originalId = setupGeocodingEntity(coords, "Original", null);
        Long userCopyId = setupGeocodingEntity(coords, "User Copy", USER_A_ID);

        List<Point> lookupCoords = List.of(coords);

        // When
        Map<String, Long> result = batchService.getCachedGeocodingResultIdsBatch(USER_A_ID, lookupCoords);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsValue(userCopyId), "Should return user copy ID, not original");
        assertFalse(result.containsValue(originalId), "Should not return original when user copy exists");
    }

    @Test
    @Order(5)
    @Transactional
    @DisplayName("Should return empty map for empty coordinates list")
    void testEmptyCoordinatesList() {
        // Given
        List<Point> emptyList = new ArrayList<>();

        // When
        Map<String, Long> result = batchService.getCachedGeocodingResultIdsBatch(USER_A_ID, emptyList);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("Should return empty map for null coordinates list")
    void testNullCoordinatesList() {
        // Given
        Point uncachedCoords1 = GeoUtils.createPoint(100.0, 50.0);
        Point uncachedCoords2 = GeoUtils.createPoint(110.0, 60.0);

        List<Point> lookupCoords = List.of(uncachedCoords1, uncachedCoords2);

        // When
        Map<String, Long> result = batchService.getCachedGeocodingResultIdsBatch(USER_A_ID, null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }



    @Test
    @Order(7)
    @DisplayName("Should return FormattableGeocodingResults for cached coordinates")
    void testBatchResultLookup() {
        // Given
        Point coords1 = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        Point coords2 = GeoUtils.createPoint(TEST_LON + 0.01, TEST_LAT + 0.01);

        setupGeocodingEntity(coords1, "Times Square", null);
        setupGeocodingEntity(coords2, "Central Park", null);

        List<Point> lookupCoords = List.of(coords1, coords2);

        // When
        Map<String, FormattableGeocodingResult> results =
                batchService.getCachedGeocodingResultsBatch(USER_A_ID, lookupCoords);

        // Then
        assertEquals(2, results.size());
        assertNotNull(results.values().stream().findFirst().orElse(null));

        // Verify results contain correct data
        boolean foundTimesSquare = results.values().stream()
                .anyMatch(r -> "Times Square".equals(r.getFormattedDisplayName()));
        boolean foundCentralPark = results.values().stream()
                .anyMatch(r -> "Central Park".equals(r.getFormattedDisplayName()));

        assertTrue(foundTimesSquare);
        assertTrue(foundCentralPark);
    }

    @Test
    @Order(8)
    @DisplayName("Should prioritize user-specific copies")
    void testUserCopyPrioritization() {
        // Given
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);

        setupGeocodingEntity(coords, "Original Name", null);
        setupGeocodingEntity(coords, "Custom Name", USER_A_ID);

        List<Point> lookupCoords = List.of(coords);

        // When
        Map<String, FormattableGeocodingResult> results =
                batchService.getCachedGeocodingResultsBatch(USER_A_ID, lookupCoords);

        // Then
        assertEquals(1, results.size());
        FormattableGeocodingResult result = results.values().iterator().next();
        assertEquals("Custom Name", result.getFormattedDisplayName(), "Should return user copy");
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("Should handle empty coordinates list")
    void testEmptyListReturnsEmptyMap() {
        // Given
        List<Point> emptyList = new ArrayList<>();

        // When
        Map<String, FormattableGeocodingResult> results =
                batchService.getCachedGeocodingResultsBatch(USER_A_ID, emptyList);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("Should handle null coordinates list")
    void testNullListReturnsEmptyMap() {
        // When
        Map<String, FormattableGeocodingResult> results =
                batchService.getCachedGeocodingResultsBatch(USER_A_ID, null);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
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

    @Transactional
    Long setupGeocodingEntity(Point coords, String displayName, UUID userId) {
        ReverseGeocodingLocationEntity entity = createGeocodingEntity(coords, displayName, userId);
        entityManager.persist(entity);
        entityManager.flush();
        return entity.getId();
    }
}
