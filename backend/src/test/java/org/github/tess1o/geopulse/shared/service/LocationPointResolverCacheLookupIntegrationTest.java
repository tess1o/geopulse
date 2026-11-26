package org.github.tess1o.geopulse.shared.service;

import io.quarkus.arc.ClientProxy;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingBatchService;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * How a timeline rebuild finds cached place names: one batch lookup per chunk, and nothing per coordinate unless that
 * batch lookup failed. The individual lookup matches exactly like the batch, so after a batch that ran it can only
 * find nothing; it used to run twice for every coordinate the batch missed.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class LocationPointResolverCacheLookupIntegrationTest {

    @Inject
    LocationPointResolver resolver;

    @Inject
    CacheGeocodingService cacheService;

    @Inject
    CacheGeocodingBatchService batchService;

    @Inject
    ReverseGeocodingLocationRepository repository;

    @Inject
    TestUserFactory users;

    @Inject
    EntityManager entityManager;

    private UUID userId;
    private TestCoordinates.Scope scope;
    private GeocodingProviderFactory provider;

    @BeforeEach
    void setUp() {
        provider = GeocodingTestMocks.install();
        userId = users.createUser("cache-lookup").userId();
        scope = TestCoordinates.newScope();
    }

    @Test
    void batchAndIndividualLookupsMatchTheSamePlaces() {
        Point request = scope.point(24.0310, 49.8420);
        Point result = scope.point(24.0324, 49.8420);
        cacheService.cacheGeocodingResult(SimpleFormattableResult.builder()
                .requestCoordinates(request).resultCoordinates(result)
                .formattedDisplayName("Cached place").providerName("test-provider").city("Lviv").country("Ukraine")
                .build());
        List<Point> probes = List.of(request, result,
                // 20 m from the request and from the result coordinates, within the 25 m tolerance
                scope.point(24.0310, 49.84218), scope.point(24.0324, 49.84218),
                // 40 m away: outside it
                scope.point(24.0310, 49.84236), scope.point(24.0317, 49.84236));

        var batch = batchService.getCachedPlacesBatch(userId, probes);
        for (Point probe : probes) {
            var single = cacheService.getCachedPlace(userId, probe);
            var batched = batch.places().get(LocationPointResolver.coordinateKey(probe));
            assertThat(batched).as("probe %s", probe).isEqualTo(single.orElse(null));
        }
        assertThat(batch.failed()).isEmpty();
        assertThat(probes.subList(0, 4)).allSatisfy(probe -> assertThat(
                batch.places().get(LocationPointResolver.coordinateKey(probe)).displayName()).isEqualTo("Cached place"));
    }

    @Test
    void aBatchMissGoesStraightToTheProvider() {
        Point cached = scope.point(24.0310, 49.8420);
        Point uncached = scope.point(24.0750, 49.8350);
        resolver.resolveLocationsWithReferencesBatch(userId, List.of(cached));
        clearInvocations(provider);
        CacheGeocodingService counting = countingCacheService();

        var results = resolver.resolveLocationsWithReferencesBatch(userId, List.of(cached, uncached));

        assertThat(results.get(LocationPointResolver.coordinateKey(cached)).getGeocodingId()).isNotNull();
        assertThat(results.get(LocationPointResolver.coordinateKey(uncached)).getGeocodingId()).isNotNull();
        verify(counting, never()).getCachedGeocodingResult(any(), any());
        verify(counting, never()).getCachedPlace(any(), any());
        verify(provider, times(1)).reverseGeocode(any(Point.class));
    }

    @Test
    void aTimedOutBatchIsSplitInsteadOfLookingUpEveryCoordinateAlone() {
        List<Point> points = cachedPoints(16);
        AtomicInteger statements = new AtomicInteger();
        // Statements for more than four coordinates time out in PostgreSQL; smaller ones run the real lookup
        slowRepository(statements, coordinates -> coordinates.size() > 4);
        CacheGeocodingService counting = countingCacheService();

        var results = resolver.resolveLocationsWithReferencesBatch(userId, points);

        assertThat(results).hasSize(points.size())
                .allSatisfy((key, result) -> assertThat(result.getLocationName()).startsWith("Cached "));
        verify(counting, never()).getCachedGeocodingResult(any(), any());
        verify(counting, never()).getCachedPlace(any(), any());
        verify(provider, never()).reverseGeocode(any(Point.class));
        // 16 -> 2 x 8 -> 4 x 4
        assertThat(statements.get()).isEqualTo(7);
    }

    @Test
    void onlyTheCoordinatesWhoseLookupKeepsTimingOutAreLookedUpAlone() {
        List<Point> points = cachedPoints(16);
        Point slow = points.get(5);
        AtomicInteger statements = new AtomicInteger();
        slowRepository(statements, coordinates -> coordinates.contains(slow));
        CacheGeocodingService counting = countingCacheService();

        var results = resolver.resolveLocationsWithReferencesBatch(userId, points);

        assertThat(results).hasSize(points.size())
                .allSatisfy((key, result) -> assertThat(result.getLocationName()).startsWith("Cached "));
        // Three splits leave the two coordinates sharing the slow one's last statement
        verify(counting).getCachedPlace(userId, points.get(4));
        verify(counting).getCachedPlace(userId, slow);
        verify(counting, times(2)).getCachedPlace(any(), any());
        verify(provider, never()).reverseGeocode(any(Point.class));
        assertThat(statements.get()).isEqualTo(7);
    }

    /** Points in the geocoding cache, named "Cached 0", "Cached 1", ... */
    private List<Point> cachedPoints(int count) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Point point = scope.point(24.0 + i * 0.01, 49.8);
            cacheService.cacheGeocodingResult(SimpleFormattableResult.builder()
                    .requestCoordinates(point).resultCoordinates(point)
                    .formattedDisplayName("Cached " + i).providerName("test-provider").build());
            points.add(point);
        }
        return points;
    }

    /**
     * The real repository, except that place lookups the predicate picks time out in PostgreSQL, as a slow statement
     * does: through the same JPA query timeout, in the transaction of the lookup, which the timeout aborts.
     */
    private void slowRepository(AtomicInteger statements, Predicate<List<Point>> timesOut) {
        ReverseGeocodingLocationRepository real = ClientProxy.unwrap(repository);
        ReverseGeocodingLocationRepository slow = mock(ReverseGeocodingLocationRepository.class, delegatesTo(real));
        doAnswer(invocation -> {
            statements.incrementAndGet();
            List<Point> coordinates = invocation.getArgument(1);
            if (timesOut.test(coordinates)) {
                // JDBC query timeouts are whole seconds
                entityManager.createNativeQuery("SELECT pg_sleep(3)")
                        .setHint("jakarta.persistence.query.timeout", 1000)
                        .getSingleResult();
                throw new AssertionError("the statement should have timed out");
            }
            return real.findPlacesByCoordinatesBatch(invocation.getArgument(0), coordinates,
                    invocation.getArgument(2), invocation.getArgument(3));
        }).when(slow).findPlacesByCoordinatesBatch(any(), anyList(), anyDouble(), anyDouble());
        QuarkusMock.installMockForType(slow, ReverseGeocodingLocationRepository.class);
    }

    /** The real cache service, with every call it gets recorded. */
    private CacheGeocodingService countingCacheService() {
        CacheGeocodingService counting = mock(CacheGeocodingService.class, delegatesTo(ClientProxy.unwrap(cacheService)));
        QuarkusMock.installMockForType(counting, CacheGeocodingService.class);
        return counting;
    }
}
