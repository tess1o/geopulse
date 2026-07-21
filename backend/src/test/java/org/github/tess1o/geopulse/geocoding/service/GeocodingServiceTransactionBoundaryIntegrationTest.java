package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeocodingServiceTransactionBoundaryIntegrationTest {

    @Inject
    GeocodingService geocodingService;

    @Inject
    CacheGeocodingService cacheGeocodingService;

    @Inject
    EntityManager entityManager;

    @Inject
    TransactionManager transactionManager;

    private TestCoordinates.Scope coordinateScope;

    @BeforeEach
    void setUp() {
        coordinateScope = TestCoordinates.newScope();
    }

    @Test
    void getLocationNameSuspendsCallerTransactionAndCachesResultOutsideRollback() {
        Point point = coordinateScope.point(-73.9857, 40.7484);
        String providerName = shortUniqueValue("suspend");
        String displayName = TestIds.uniqueValue("Suspended Transaction Location");

        AtomicBoolean providerCalled = new AtomicBoolean(false);
        AtomicBoolean providerSawActiveTransaction = new AtomicBoolean(true);
        AtomicReference<SystemException> transactionInspectionFailure = new AtomicReference<>();

        GeocodingProviderFactory providerFactory = Mockito.mock(GeocodingProviderFactory.class);
        when(providerFactory.reverseGeocode(any(Point.class)))
                .thenAnswer(invocation -> {
                    providerCalled.set(true);
                    try {
                        providerSawActiveTransaction.set(transactionManager.getTransaction() != null);
                    } catch (SystemException e) {
                        transactionInspectionFailure.set(e);
                    }

                    Point requestPoint = invocation.getArgument(0, Point.class);
                    return Uni.createFrom().item(SimpleFormattableResult.builder()
                            .requestCoordinates(requestPoint)
                            .resultCoordinates(requestPoint)
                            .formattedDisplayName(displayName)
                            .providerName(providerName)
                            .city("New York")
                            .country("USA")
                            .build());
                });
        QuarkusMock.installMockForType(providerFactory, GeocodingProviderFactory.class);

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().call(() -> {
                    geocodingService.getLocationName(point);
                    throw new RuntimeException("rollback caller transaction");
                }));

        assertEquals("rollback caller transaction", failure.getMessage());
        assertTrue(providerCalled.get(), "The provider should be called through GeocodingService");
        assertNull(transactionInspectionFailure.get(), "The provider transaction state should be inspectable");
        assertFalse(providerSawActiveTransaction.get(),
                "GeocodingService should suspend the caller transaction before external provider work");

        assertTrue(cacheGeocodingService.getCachedGeocodingResult(null, point).isPresent(),
                "Geocoding cache writes should survive rollback of the caller transaction");
        assertEquals(1, countGeocodingRows(providerName, displayName));
    }

    private long countGeocodingRows(String providerName, String displayName) {
        return QuarkusTransaction.requiringNew().call(() ->
                ((Number) entityManager.createNativeQuery("""
                                SELECT COUNT(*)
                                FROM reverse_geocoding_location
                                WHERE provider_name = :providerName
                                  AND display_name = :displayName
                                  AND user_id IS NULL
                                """)
                        .setParameter("providerName", providerName)
                        .setParameter("displayName", displayName)
                        .getSingleResult()).longValue());
    }

    private String shortUniqueValue(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
