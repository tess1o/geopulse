package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeocodingBoundingBoxGuardIntegrationTest {

    @Inject
    CacheGeocodingService cacheGeocodingService;

    @Inject
    ReverseGeocodingLocationRepository repository;

    @Inject
    EntityManager entityManager;

    private TestCoordinates.Scope coordinateScope;

    @BeforeEach
    void setup() {
        coordinateScope = TestCoordinates.newScope();
    }

    @Test
    @Transactional
    @DisplayName("Oversized provider bbox is dropped during cache persistence")
    void testOversizedBboxIsDroppedOnCacheWrite() {
        Point point = coord(18.060045, 59.330009);
        Polygon oversizedBbox = GeoUtils.buildBoundingBoxPolygon(0.0, 1.0, 0.0, 1.0);

        cacheGeocodingService.cacheGeocodingResult(buildResult(point, oversizedBbox, "Nominatim Oversized"));

        ReverseGeocodingLocationEntity persisted = repository.findOriginalByExactCoordinates(point);
        assertNotNull(persisted);
        assertNull(persisted.getBoundingBox(), "Oversized bbox should be stored as null");
    }

    @Test
    @Transactional
    @DisplayName("World-spanning provider bbox is dropped during cache persistence")
    void testWorldSpanningBboxIsDroppedOnCacheWrite() {
        // Issue #648: ST_Area(::geography) reports 0 for this box because the
        // -180/+180 edges collapse onto the antimeridian.
        Point point = coord(18.060445, 59.330409);
        Polygon worldBbox = GeoUtils.buildBoundingBoxPolygon(-14.7608358, 71.5889534, -180, 180);

        cacheGeocodingService.cacheGeocodingResult(buildResult(point, worldBbox, "Nominatim World"));

        ReverseGeocodingLocationEntity persisted = repository.findOriginalByExactCoordinates(point);
        assertNotNull(persisted);
        assertNull(persisted.getBoundingBox(), "World-spanning bbox should be stored as null");
    }

    @Test
    @Transactional
    @DisplayName("Near-world-spanning provider bbox is dropped during cache persistence")
    void testNearWorldSpanningBboxIsDroppedOnCacheWrite() {
        // Geography area sees only the tiny antimeridian-side complement here,
        // while planar containment covers almost the whole planet.
        Point point = coord(18.060545, 59.330509);
        Polygon nearWorldBbox = GeoUtils.buildBoundingBoxPolygon(-14.7608358, 71.5889534, -179.9995, 179.9995);

        cacheGeocodingService.cacheGeocodingResult(buildResult(point, nearWorldBbox, "Nominatim Near World"));

        ReverseGeocodingLocationEntity persisted = repository.findOriginalByExactCoordinates(point);
        assertNotNull(persisted);
        assertNull(persisted.getBoundingBox(), "Near-world-spanning bbox should be stored as null");
    }

    @Test
    @Transactional
    @DisplayName("Safe provider bbox is preserved during cache persistence")
    void testSafeBboxIsPreservedOnCacheWrite() {
        Point point = coord(18.060145, 59.330109);
        Polygon safeBbox = GeoUtils.buildBoundingBoxPolygon(59.3299, 59.3302, 18.0598, 18.0603);

        cacheGeocodingService.cacheGeocodingResult(buildResult(point, safeBbox, "Nominatim Safe"));

        ReverseGeocodingLocationEntity persisted = repository.findOriginalByExactCoordinates(point);
        assertNotNull(persisted);
        assertNotNull(persisted.getBoundingBox(), "Safe bbox should be preserved");
    }

    @Test
    @Transactional
    @DisplayName("Migration update nulls oversized and world-spanning bboxes and keeps safe bbox")
    void testMigrationSqlBehavior() {
        Point oversizedPoint = coord(18.060245, 59.330209);
        Point worldPoint = coord(18.060645, 59.330609);
        Point safePoint = coord(18.060345, 59.330309);

        ReverseGeocodingLocationEntity oversized = new ReverseGeocodingLocationEntity();
        oversized.setRequestCoordinates(oversizedPoint);
        oversized.setResultCoordinates(oversizedPoint);
        oversized.setBoundingBox(GeoUtils.buildBoundingBoxPolygon(0.0, 1.0, 0.0, 1.0));
        oversized.setDisplayName("Oversized For Migration");
        oversized.setProviderName("Nominatim");
        oversized.setCity("Stockholm");
        oversized.setCountry("Sweden");
        repository.persist(oversized);

        ReverseGeocodingLocationEntity worldSpanning = new ReverseGeocodingLocationEntity();
        worldSpanning.setRequestCoordinates(worldPoint);
        worldSpanning.setResultCoordinates(worldPoint);
        worldSpanning.setBoundingBox(GeoUtils.buildBoundingBoxPolygon(-14.7608358, 71.5889534, -180, 180));
        worldSpanning.setDisplayName("World For Migration");
        worldSpanning.setProviderName("Nominatim");
        worldSpanning.setCity("Stockholm");
        worldSpanning.setCountry("Sweden");
        repository.persist(worldSpanning);

        ReverseGeocodingLocationEntity safe = new ReverseGeocodingLocationEntity();
        safe.setRequestCoordinates(safePoint);
        safe.setResultCoordinates(safePoint);
        safe.setBoundingBox(GeoUtils.buildBoundingBoxPolygon(59.3302, 59.3304, 18.0602, 18.0604));
        safe.setDisplayName("Safe For Migration");
        safe.setProviderName("Nominatim");
        safe.setCity("Stockholm");
        safe.setCountry("Sweden");
        repository.persist(safe);

        entityManager.flush();

        int updatedRows = entityManager.createNativeQuery("""
                UPDATE reverse_geocoding_location
                SET bounding_box = NULL
                WHERE bounding_box IS NOT NULL
                  AND geopulse_geocoding_bbox_area_km2(bounding_box) > 5000
                """).executeUpdate();

        assertTrue(updatedRows >= 2, "The seeded oversized and world-spanning rows should be nulled");

        entityManager.flush();
        entityManager.clear();

        ReverseGeocodingLocationEntity oversizedReloaded = repository.findById(oversized.getId());
        ReverseGeocodingLocationEntity worldSpanningReloaded = repository.findById(worldSpanning.getId());
        ReverseGeocodingLocationEntity safeReloaded = repository.findById(safe.getId());
        assertNotNull(oversizedReloaded);
        assertNotNull(worldSpanningReloaded);
        assertNotNull(safeReloaded);
        assertNull(oversizedReloaded.getBoundingBox(), "Oversized bbox should be nulled");
        assertNull(worldSpanningReloaded.getBoundingBox(), "World-spanning bbox should be nulled");
        assertNotNull(safeReloaded.getBoundingBox(), "Safe bbox should remain");
    }

    private FormattableGeocodingResult buildResult(Point point, Polygon boundingBox, String displayName) {
        return SimpleFormattableResult.builder()
                .requestCoordinates(point)
                .resultCoordinates(point)
                .boundingBox(boundingBox)
                .formattedDisplayName(displayName)
                .providerName("Nominatim")
                .city("Stockholm")
                .country("Sweden")
                .build();
    }

    private Point coord(double lon, double lat) {
        return coordinateScope.point(lon, lat);
    }
}
