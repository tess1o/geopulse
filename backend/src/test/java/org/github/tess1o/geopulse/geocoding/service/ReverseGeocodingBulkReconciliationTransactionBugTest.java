package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingReconcileRequest;
import org.github.tess1o.geopulse.geocoding.model.ReconciliationJobProgress;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression test for issue #411:
 * If one item fails in async bulk reconciliation, successful items are counted in job progress
 * but may not be persisted.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ReverseGeocodingBulkReconciliationTransactionBugTest {

    @Inject
    ReverseGeocodingManagementService managementService;

    @Inject
    ReconciliationJobProgressService reconciliationJobProgressService;

    @Inject
    ReverseGeocodingLocationRepository repository;

    @Inject
    EntityManager entityManager;

    @Inject
    UserTransaction userTransaction;

    private UUID userId;
    private TestCoordinates.Scope coordinateScope;

    @BeforeEach
    @Transactional
    void setupUserAndMocks() {
        coordinateScope = TestCoordinates.newScope();

        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail("it-user"))
                .fullName("Bulk Reconcile Bug User")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        userId = user.getId();

        GeocodingProviderFactory providerFactoryMock = Mockito.mock(GeocodingProviderFactory.class);
        QuarkusMock.installMockForType(providerFactoryMock, GeocodingProviderFactory.class);

        when(providerFactoryMock.reconcileWithProvider(eq("photon"), any(Point.class)))
                .thenAnswer(invocation -> {
                    Point requestPoint = invocation.getArgument(1, Point.class);
                    double lon = requestPoint.getX();

                    if (lon < -100.0) {
                        return Uni.createFrom().item(SimpleFormattableResult.builder()
                                .requestCoordinates(requestPoint)
                                .resultCoordinates(requestPoint)
                                .formattedDisplayName("Photon Success Location")
                                .providerName("photon")
                                .city("Kyiv")
                                .country("Ukraine")
                                .build());
                    }

                    return Uni.createFrom().failure(new RuntimeException("Simulated provider failure"));
                });
    }

    @Test
    @DisplayName("BUG: mixed success/failure bulk reconciliation should persist successful item")
    void shouldPersistSuccessfulItemsEvenWhenOneItemFails() throws Exception {
        Long successId;
        Long failingId;

        userTransaction.begin();
        try {
            ReverseGeocodingLocationEntity successEntity = createUserEntity(
                    userId,
                    coord(-120.1234, 35.6789),
                    "Original Success Name"
            );
            repository.persist(successEntity);

            ReverseGeocodingLocationEntity failingEntity = createUserEntity(
                    userId,
                    coord(-80.5678, 40.1234),
                    "Original Failing Name"
            );
            repository.persist(failingEntity);

            entityManager.flush();
            successId = successEntity.getId();
            failingId = failingEntity.getId();
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        ReverseGeocodingReconcileRequest request = ReverseGeocodingReconcileRequest.builder()
                .providerName("photon")
                .geocodingIds(List.of(successId, failingId))
                .reconcileAll(false)
                .build();

        UUID jobId = managementService.reconcileWithProviderAsync(userId, request);
        ReconciliationJobProgress jobProgress = waitForJobToFinish(jobId, Duration.ofSeconds(20));

        assertEquals(ReconciliationJobProgress.JobStatus.COMPLETED, jobProgress.getStatus());
        assertEquals(2, jobProgress.getProcessedItems());
        assertEquals(1, jobProgress.getSuccessCount());
        assertEquals(1, jobProgress.getFailedCount());

        userTransaction.begin();
        try {
            entityManager.clear();
            ReverseGeocodingLocationEntity successAfter = repository.findById(successId);
            assertNotNull(successAfter);

            // Successful item should be persisted as Photon even when another item fails.
            assertEquals("photon", successAfter.getProviderName(),
                    "Successful reconciliations must be committed even when another item fails");
            assertEquals("Photon Success Location", successAfter.getDisplayName());

            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    private ReconciliationJobProgress waitForJobToFinish(UUID jobId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        ReconciliationJobProgress latest = null;

        while (Instant.now().isBefore(deadline)) {
            latest = reconciliationJobProgressService.getJobProgress(jobId).orElse(null);
            if (latest != null && latest.isTerminal()) {
                return latest;
            }
            Thread.sleep(100);
        }

        fail("Timed out waiting for reconciliation job to finish. Last progress: " + latest);
        return null;
    }

    private ReverseGeocodingLocationEntity createUserEntity(UUID ownerUserId, Point requestCoordinates, String displayName) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setUser(entityManager.getReference(UserEntity.class, ownerUserId));
        entity.setRequestCoordinates(requestCoordinates);
        entity.setResultCoordinates(requestCoordinates);
        entity.setDisplayName(displayName);
        entity.setProviderName("nominatim");
        entity.setCity("Old City");
        entity.setCountry("Old Country");
        entity.setCreatedAt(Instant.now());
        entity.setLastAccessedAt(Instant.now());
        return entity;
    }

    private Point coord(double lon, double lat) {
        return coordinateScope.point(lon, lat);
    }
}
