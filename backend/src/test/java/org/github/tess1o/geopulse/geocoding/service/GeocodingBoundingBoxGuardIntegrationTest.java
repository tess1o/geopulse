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
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("Migration update nulls oversized bbox and keeps safe bbox")
    void testMigrationSqlBehavior() {
        Point oversizedPoint = coord(18.060245, 59.330209);
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
                  AND ST_Area(bounding_box::geography) > 5000000000
                """).executeUpdate();

        assertTrue(updatedRows >= 1, "At least the seeded oversized row should be nulled");

        entityManager.flush();
        entityManager.clear();

        ReverseGeocodingLocationEntity oversizedReloaded = repository.findById(oversized.getId());
        ReverseGeocodingLocationEntity safeReloaded = repository.findById(safe.getId());
        assertNotNull(oversizedReloaded);
        assertNotNull(safeReloaded);
        assertNull(oversizedReloaded.getBoundingBox(), "Oversized bbox should be nulled");
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
