package org.github.tess1o.geopulse.importdata;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.dto.CreateNormalizationRuleRequest;
import org.github.tess1o.geopulse.geocoding.model.NormalizationRuleType;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.geocoding.service.UserLocationNormalizationService;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.service.BatchProcessor;
import org.github.tess1o.geopulse.importdata.service.ImportBatchPersistenceException;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
@Slf4j
class ImportTransactionBoundaryTest {

    @Inject
    BatchProcessor batchProcessor;

    @Inject
    CacheGeocodingService cacheGeocodingService;

    @Inject
    ReverseGeocodingManagementService reverseGeocodingManagementService;

    @Inject
    UserLocationNormalizationService normalizationService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UUID testUserId;
    private UserEntity testUser;
    private TestCoordinates.Scope coordinateScope;
    private long pointCounter;

    @BeforeEach
    @Transactional
    void setUp() {
        coordinateScope = TestCoordinates.newScope();

        testUser = new UserEntity();
        testUser.setEmail(TestIds.uniqueEmail("import-boundary-user"));
        testUser.setPasswordHash("test-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole(Role.USER);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();

        testUserId = testUser.getId();
        pointCounter = 0;

        normalizationService.createRule(testUserId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.CITY)
                .sourceCity("San Francisco")
                .targetCity("San Francisco City")
                .build());
    }

    @Test
    void failedImportRollsBackGpsRowsButPreservesReverseGeocodingRows() {
        Point geocodedPoint = coordinateScope.point(-122.4194, 37.7749);

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().timeout(60).call(() -> {
                    batchProcessor.processBatch(createTestGpsPoints(10), true);

                    cacheGeocodingService.cacheGeocodingResult(SimpleFormattableResult.builder()
                            .requestCoordinates(geocodedPoint)
                            .resultCoordinates(geocodedPoint)
                            .formattedDisplayName("San Francisco Area")
                            .providerName("test-provider")
                            .city("San Francisco")
                            .country("USA")
                            .build());

                    Long originalGeocodingId = cacheGeocodingService
                            .getCachedGeocodingResultId(testUserId, geocodedPoint)
                            .orElseThrow();
                    Long normalizedGeocodingId = reverseGeocodingManagementService
                            .normalizeGeocodingForResolution(testUserId, originalGeocodingId)
                            .getId();
                    assertNotNull(normalizedGeocodingId);

                    throw new RuntimeException("simulated import failure");
                }));

        assertEquals("simulated import failure", failure.getMessage());
        assertEquals(0, countPersistedGpsPoints());

        List<Object> geocodingUserIds = findGeocodingUserIds(geocodedPoint);
        assertEquals(2, geocodingUserIds.size());
        assertTrue(geocodingUserIds.stream().anyMatch(value -> value == null));
        assertTrue(geocodingUserIds.stream().anyMatch(testUserId::equals));
    }

    @Test
    void failedBatchRollsBackEarlierGpsBatchesInSameImportTransaction() {
        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().timeout(60).call(() -> {
                    batchProcessor.processBatch(createTestGpsPoints(10), true);
                    batchProcessor.processBatch(List.of(createInvalidGpsPoint()), true);
                    return null;
                }));

        assertInstanceOf(ImportBatchPersistenceException.class, failure);
        assertEquals(0, countPersistedGpsPoints());
    }

    @Test
    void failedProcessInBatchesRollsBackEarlierInternalBatches() {
        List<GpsPointEntity> points = new ArrayList<>();
        points.addAll(createTestGpsPoints(4));
        points.add(createInvalidGpsPoint());

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                batchProcessor.processInBatches(points, 2, true));

        assertInstanceOf(ImportBatchPersistenceException.class, failure);
        assertEquals(0, countPersistedGpsPoints(),
                "A failed processInBatches call must roll back GPS rows flushed by earlier internal batches");
    }

    private List<GpsPointEntity> createTestGpsPoints(int count) {
        List<GpsPointEntity> points = new ArrayList<>();
        Instant baseTime = Instant.parse("2024-01-01T12:00:00Z");
        for (int i = 0; i < count; i++) {
            GpsPointEntity point = new GpsPointEntity();
            point.setUser(testUser);
            point.setTimestamp(baseTime.plusSeconds(pointCounter * 60));
            point.setCoordinates(GeoUtils.createPoint(
                    -73.9851 + (pointCounter * 0.0001),
                    40.7589 + (pointCounter * 0.0001)
            ));
            point.setAccuracy(10.0);
            point.setCreatedAt(Instant.now());
            points.add(point);
            pointCounter++;
        }
        return points;
    }

    private GpsPointEntity createInvalidGpsPoint() {
        GpsPointEntity point = new GpsPointEntity();
        point.setTimestamp(Instant.parse("2024-01-02T12:00:00Z"));
        point.setCoordinates(GeoUtils.createPoint(-73.9851, 40.7589));
        point.setAccuracy(10.0);
        point.setCreatedAt(Instant.now());
        return point;
    }

    private long countPersistedGpsPoints() {
        return QuarkusTransaction.requiringNew().call(() ->
                gpsPointRepository.count("user.id = ?1", testUserId));
    }

    private List<Object> findGeocodingUserIds(Point point) {
        return QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object> userIds = entityManager.createNativeQuery("""
                            SELECT user_id
                            FROM reverse_geocoding_location
                            WHERE ST_Equals(request_coordinates, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
                              AND provider_name = :providerName
                            ORDER BY user_id NULLS FIRST
                            """)
                    .setParameter("lon", point.getX())
                    .setParameter("lat", point.getY())
                    .setParameter("providerName", "test-provider")
                    .getResultList();
            return userIds;
        });
    }
}
