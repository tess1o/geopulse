package org.github.tess1o.geopulse.geocoding.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ReverseGeocodingLocationRepository using real PostGIS database.
 * Tests the comprehensive spatial query with multiple proximity checks and bounding box containment.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class ReverseGeocodingLocationRepositoryTest {

    @Inject
    ReverseGeocodingLocationRepository repository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @Transactional
    void testFindByRequestCoordinates_ExactMatch() {
        // Given: a cached location
        Point coordinates = GeoUtils.createPoint(-73.9857, 40.7484); // Times Square
        ReverseGeocodingLocationEntity entity = createTestEntity(coordinates, "Times Square, NYC");
        repository.persist(entity);

        // When: searching for exact coordinates
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(coordinates, 100.0);

        // Then: should find the exact match
        assertNotNull(result);
        assertEquals("Times Square, NYC", result.getDisplayName());
        assertEquals("Nominatim", result.getProviderName());
        assertTrue(coordinates.equals(result.getRequestCoordinates()));
    }

    @Test
    @Transactional
    void testFindByRequestCoordinates_RequestCoordinatesProximity() {
        // Given: a cached location
        Point originalCoordinates = GeoUtils.createPoint(-73.9857, 40.7484); // Times Square
        ReverseGeocodingLocationEntity entity = createTestEntity(originalCoordinates, "Times Square, NYC");
        repository.persist(entity);

        // When: searching for nearby coordinates (within tolerance)
        Point nearbyCoordinates = GeoUtils.createPoint(-73.9856, 40.7485); // ~50-80m away
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(nearbyCoordinates, 100.0);

        // Then: should find the cached result via request coordinates proximity
        assertNotNull(result);
        assertEquals("Times Square, NYC", result.getDisplayName());
        assertEquals(originalCoordinates, result.getRequestCoordinates());
    }

    @Test
    @Transactional
    void testFindByRequestCoordinates_ResultCoordinatesProximity() {
        // Given: a cached location where result coordinates differ from request coordinates
        Point requestCoordinates = GeoUtils.createPoint(-73.9857, 40.7484); // Times Square request
        Point resultCoordinates = GeoUtils.createPoint(-73.9855, 40.7486); // Slightly different result from provider
        
        ReverseGeocodingLocationEntity entity = createTestEntity(requestCoordinates, "Times Square, NYC");
        entity.setResultCoordinates(resultCoordinates);
        repository.persist(entity);

        // When: searching for coordinates near the result coordinates
        Point searchCoordinates = GeoUtils.createPoint(-73.9854, 40.7487); // Near result coordinates
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(searchCoordinates, 100.0);

        // Then: should find the cached result via result coordinates proximity
        assertNotNull(result);
        assertEquals("Times Square, NYC", result.getDisplayName());
        assertEquals(requestCoordinates, result.getRequestCoordinates());
        assertEquals(resultCoordinates, result.getResultCoordinates());
    }

    @Test
    @Transactional
    void testFindByRequestCoordinates_BoundingBoxContainment() {
        // Given: a cached location with a bounding box
        Point requestCoordinates = GeoUtils.createPoint(-73.9857, 40.7484); // Times Square
        
        // Create bounding box around Times Square area (about 200m x 200m)
        double minLat = 40.7474;
        double maxLat = 40.7494;
        double minLon = -73.9867;
        double maxLon = -73.9847;
        Polygon boundingBox = GeoUtils.buildBoundingBoxPolygon(minLat, maxLat, minLon, maxLon);
        
        ReverseGeocodingLocationEntity entity = createTestEntity(requestCoordinates, "Times Square Area");
        entity.setBoundingBox(boundingBox);
        repository.persist(entity);

        // When: searching for coordinates inside the bounding box
        Point insideBounds = GeoUtils.createPoint(-73.9860, 40.7480); // Inside the bounding box
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(insideBounds, 50.0); // Small tolerance

        // Then: should find the cached result via bounding box containment
        assertNotNull(result);
        assertEquals("Times Square Area", result.getDisplayName());
        assertEquals(requestCoordinates, result.getRequestCoordinates());
        assertNotNull(result.getBoundingBox());
    }

    @Test
    @Transactional
    void testFindByRequestCoordinates_OutsideTolerance() {
        // Given: a cached location
        Point coordinates = GeoUtils.createPoint(-73.9857, 40.7484); // Times Square
        ReverseGeocodingLocationEntity entity = createTestEntity(coordinates, "Times Square, NYC");
        repository.persist(entity);

        // When: searching for coordinates far away (outside tolerance)
        Point farAwayCoordinates = GeoUtils.createPoint(-74.0059, 40.7128); // Wall Street area (~2km away)
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(farAwayCoordinates, 100.0);

        // Then: should not find any result
        assertNull(result);
    }

    @Test
    @Transactional
    void testFindByRequestCoordinates_MultipleMatches_ReturnsRecent() {
        // Given: multiple cached locations that could match
        Point coordinates1 = GeoUtils.createPoint(-73.9857, 40.7484); // Times Square
        Point coordinates2 = GeoUtils.createPoint(-73.9856, 40.7485); // Very close to Times Square

        ReverseGeocodingLocationEntity entity1 = createTestEntity(coordinates1, "Times Square Old");
        entity1.setLastAccessedAt(Instant.now().minusSeconds(3600)); // 1 hour ago
        repository.persist(entity1);

        ReverseGeocodingLocationEntity entity2 = createTestEntity(coordinates2, "Times Square Recent");
        entity2.setLastAccessedAt(Instant.now().minusSeconds(60)); // 1 minute ago
        repository.persist(entity2);

        // When: searching for coordinates that match both
        Point searchCoordinates = GeoUtils.createPoint(-73.9857, 40.7484);
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(searchCoordinates, 100.0);

        // Then: should return the most recently accessed one
        assertNotNull(result);
        assertEquals("Times Square Recent", result.getDisplayName());
    }

    @Test
    @Transactional
    void testFindByRequestCoordinates_UpdatesLastAccessedTime() {
        // Given: a cached location
        Point coordinates = GeoUtils.createPoint(-73.9857, 40.7484);
        ReverseGeocodingLocationEntity entity = createTestEntity(coordinates, "Times Square, NYC");
        Instant originalTime = Instant.now().minusSeconds(3600); // 1 hour ago
        entity.setLastAccessedAt(originalTime);
        repository.persist(entity);

        // When: finding the location
        ReverseGeocodingLocationEntity result = repository.findByRequestCoordinates(coordinates, 100.0);

        // Then: last accessed time should be updated
        assertNotNull(result);
        assertTrue(result.getLastAccessedAt().isAfter(originalTime));
    }

    @Test
    @Transactional
    void testFindByExactCoordinates() {
        // Given: a cached location
        Point coordinates = GeoUtils.createPoint(-73.9857, 40.7484);
        ReverseGeocodingLocationEntity entity = createTestEntity(coordinates, "Times Square, NYC");
        repository.persist(entity);

        // When: searching for exact coordinates
        ReverseGeocodingLocationEntity result = repository.findByExactCoordinates(coordinates);

        // Then: should find the exact match
        assertNotNull(result);
        assertEquals("Times Square, NYC", result.getDisplayName());
        assertTrue(coordinates.equals(result.getRequestCoordinates()));
    }

    @Test
    @Transactional
    void testFindByExactCoordinates_NoMatch() {
        // Given: a cached location
        Point coordinates = GeoUtils.createPoint(-73.9857, 40.7484);
        ReverseGeocodingLocationEntity entity = createTestEntity(coordinates, "Times Square, NYC");
        repository.persist(entity);

        // When: searching for different exact coordinates
        Point differentCoordinates = GeoUtils.createPoint(-74.0059, 40.7128);
        ReverseGeocodingLocationEntity result = repository.findByExactCoordinates(differentCoordinates);

        // Then: should not find any result
        assertNull(result);
    }

    @Test
    @Transactional
    void testFindByCoordinatesBatchReal_WithLargeNumberOfCoordinates() {
        // Given: a large number of coordinates, exceeding the parameter limit
        int totalCoordinates = 40000;
        List<Point> coordinates = new ArrayList<>();

        // Add a point that will match an entity
        Point matchingPoint1 = GeoUtils.createPoint(-73.9857, 40.7484);
        coordinates.add(matchingPoint1);

        // Add a bunch of other points that won't match anything
        for (int i = 1; i < totalCoordinates - 1; i++) {
            coordinates.add(GeoUtils.createPoint(i * 0.001, 45.0)); // Use smaller increment to avoid invalid longitude
        }

        // Add another point that will match another entity
        Point matchingPoint2 = GeoUtils.createPoint(-74.0445, 40.6892); // Statue of Liberty
        coordinates.add(matchingPoint2);


        // And: a few cached locations that will match some of the coordinates
        ReverseGeocodingLocationEntity entity1 = createTestEntity(matchingPoint1, "Times Square, NYC");
        repository.persist(entity1);

        ReverseGeocodingLocationEntity entity2 = createTestEntity(matchingPoint2, "Statue of Liberty");
        repository.persist(entity2);

        // When: searching for all coordinates in a single batch call
        Map<String, ReverseGeocodingLocationEntity> results = repository.findByCoordinatesBatchReal(coordinates, 100.0);

        // Then: should find the matching locations
        assertEquals(2, results.size());
        assertTrue(results.containsKey(matchingPoint1.getX() + "," + matchingPoint1.getY()));
        assertEquals("Times Square, NYC", results.get(matchingPoint1.getX() + "," + matchingPoint1.getY()).getDisplayName());
        assertTrue(results.containsKey(matchingPoint2.getX() + "," + matchingPoint2.getY()));
        assertEquals("Statue of Liberty", results.get(matchingPoint2.getX() + "," + matchingPoint2.getY()).getDisplayName());
    }

    /**
     * Helper method to create a test entity.
     */
    private ReverseGeocodingLocationEntity createTestEntity(Point coordinates, String displayName) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setRequestCoordinates(coordinates);
        entity.setResultCoordinates(coordinates); // Default to same as request
        entity.setDisplayName(displayName);
        entity.setProviderName("Nominatim");
        entity.setCreatedAt(Instant.now());
        entity.setLastAccessedAt(Instant.now());
        return entity;
    }
}