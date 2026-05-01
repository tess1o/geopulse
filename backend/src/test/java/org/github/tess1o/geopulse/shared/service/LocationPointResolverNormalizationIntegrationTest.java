package org.github.tess1o.geopulse.shared.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.dto.CreateNormalizationRuleRequest;
import org.github.tess1o.geopulse.geocoding.model.NormalizationRuleType;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.geocoding.service.UserLocationNormalizationService;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class LocationPointResolverNormalizationIntegrationTest {

    @Inject
    LocationPointResolver locationPointResolver;

    @Inject
    UserLocationNormalizationService normalizationService;

    @Inject
    ReverseGeocodingLocationRepository geocodingRepository;

    @Inject
    EntityManager entityManager;

    private UUID userId;
    private TestCoordinates.Scope coordinateScope;

    @BeforeEach
    @Transactional
    void setupUser() {
        coordinateScope = TestCoordinates.newScope();

        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail("resolver-norm"))
                .fullName("Resolver Normalization User")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(user);
        entityManager.flush();

        userId = user.getId();
    }

    @Test
    @DisplayName("Full flow: Nominatim external geocoding is normalized for user-specific reference")
    void nominatimExternalFlowAppliesNormalization() {
        GeocodingProviderFactory providerFactoryMock = Mockito.mock(GeocodingProviderFactory.class);
        QuarkusMock.installMockForType(providerFactoryMock, GeocodingProviderFactory.class);

        Point point = coordinateScope.point(23.3219, 42.6977);
        when(providerFactoryMock.reverseGeocode(any(Point.class)))
                .thenReturn(Uni.createFrom().item(SimpleFormattableResult.builder()
                        .requestCoordinates(point)
                        .resultCoordinates(point)
                        .formattedDisplayName("Sofia Center")
                        .providerName("nominatim")
                        .city("София")
                        .country("Болгарія")
                        .build()));

        normalizationService.createRule(userId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.CITY)
                .sourceCity("София")
                .targetCity("Sofia")
                .build());
        normalizationService.createRule(userId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.COUNTRY)
                .sourceCountry("Болгарія")
                .targetCountry("Bulgaria")
                .build());

        LocationResolutionResult result = locationPointResolver.resolveLocationWithReferences(userId, point);

        assertNotNull(result);
        assertNotNull(result.getGeocodingId(), "Geocoding reference should be persisted and returned");

        ReverseGeocodingLocationEntity normalizedEntity = geocodingRepository.findById(result.getGeocodingId());
        assertNotNull(normalizedEntity);
        assertNotNull(normalizedEntity.getUser(), "Normalized entity should be user-specific copy");
        assertEquals(userId, normalizedEntity.getUser().getId());
        assertEquals("Sofia", normalizedEntity.getCity());
        assertEquals("Bulgaria", normalizedEntity.getCountry());

        assertHasOriginalAndUserCopyForPoint(userId, point, "nominatim");
    }

    @Test
    @DisplayName("Full flow: Photon batch geocoding is normalized for all returned references")
    void photonBatchExternalFlowAppliesNormalization() {
        GeocodingProviderFactory providerFactoryMock = Mockito.mock(GeocodingProviderFactory.class);
        QuarkusMock.installMockForType(providerFactoryMock, GeocodingProviderFactory.class);

        Point point1 = coordinateScope.point(30.5234, 50.4501);
        Point point2 = coordinateScope.point(30.5240, 50.4505);

        when(providerFactoryMock.reverseGeocode(any(Point.class)))
                .thenAnswer(invocation -> {
                    Point requestPoint = invocation.getArgument(0, Point.class);
                    return Uni.createFrom().item(SimpleFormattableResult.builder()
                            .requestCoordinates(requestPoint)
                            .resultCoordinates(requestPoint)
                            .formattedDisplayName("Kyiv Area")
                            .providerName("photon")
                            .city("Київ")
                            .country("Україна")
                            .build());
                });

        normalizationService.createRule(userId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.CITY)
                .sourceCity("Київ")
                .targetCity("Kyiv")
                .build());
        normalizationService.createRule(userId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.COUNTRY)
                .sourceCountry("Україна")
                .targetCountry("Ukraine")
                .build());

        Map<String, LocationResolutionResult> results = locationPointResolver
                .resolveLocationsWithReferencesBatch(userId, List.of(point1, point2));

        assertEquals(2, results.size());

        for (Point point : List.of(point1, point2)) {
            String key = point.getX() + "," + point.getY();
            LocationResolutionResult locationResult = results.get(key);
            assertNotNull(locationResult, "Expected resolution for coordinate " + key);
            assertNotNull(locationResult.getGeocodingId(), "Expected geocoding ID for coordinate " + key);

            ReverseGeocodingLocationEntity normalizedEntity = geocodingRepository.findById(locationResult.getGeocodingId());
            assertNotNull(normalizedEntity);
            assertNotNull(normalizedEntity.getUser(), "Batch normalized entity should be user-specific copy");
            assertEquals(userId, normalizedEntity.getUser().getId());
            assertEquals("Kyiv", normalizedEntity.getCity());
            assertEquals("Ukraine", normalizedEntity.getCountry());

            assertHasOriginalAndUserCopyForPoint(userId, point, "photon");
        }
    }

    @SuppressWarnings("unchecked")
    private void assertHasOriginalAndUserCopyForPoint(UUID currentUserId, Point point, String providerName) {
        String sql = """
                SELECT user_id
                FROM reverse_geocoding_location
                WHERE ST_Equals(request_coordinates, ST_SetSRID(ST_MakePoint(?1, ?2), 4326))
                  AND provider_name = ?3
                """;
        List<Object> userIds = entityManager.createNativeQuery(sql)
                .setParameter(1, point.getX())
                .setParameter(2, point.getY())
                .setParameter(3, providerName)
                .getResultList();

        assertTrue(userIds.stream().anyMatch(value -> value == null),
                "Expected original cached row (user_id IS NULL)");
        assertTrue(userIds.stream().anyMatch(value -> currentUserId.equals(value)),
                "Expected user-specific normalized copy row");
    }
}
