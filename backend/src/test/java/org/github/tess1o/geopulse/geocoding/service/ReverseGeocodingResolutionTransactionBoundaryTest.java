package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.dto.CreateNormalizationRuleRequest;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.model.NormalizationRuleType;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ReverseGeocodingResolutionTransactionBoundaryTest {

    private static final String PROVIDER_NAME = "resolution-boundary";
    private static final String ORIGINAL_CITY = "Old City";
    private static final String ORIGINAL_COUNTRY = "Old Country";
    private static final String NORMALIZED_CITY = "New City";
    private static final String NORMALIZED_COUNTRY = "New Country";
    private static final String ORIGINAL_DISPLAY_NAME = "Original Display Name";

    @Inject
    ReverseGeocodingManagementService managementService;

    @Inject
    UserLocationNormalizationService normalizationService;

    @Inject
    EntityManager entityManager;

    private UUID userId;
    private TestCoordinates.Scope coordinateScope;

    @BeforeEach
    @Transactional
    void setUp() {
        coordinateScope = TestCoordinates.newScope();

        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail("resolution-boundary"))
                .passwordHash("test-hash")
                .timezone("UTC")
                .isActive(true)
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        entityManager.persist(user);
        entityManager.flush();
        userId = user.getId();

        normalizationService.createRule(userId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.CITY)
                .sourceCity(ORIGINAL_CITY)
                .targetCity(NORMALIZED_CITY)
                .build());
        normalizationService.createRule(userId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.COUNTRY)
                .sourceCountry(ORIGINAL_COUNTRY)
                .targetCountry(NORMALIZED_COUNTRY)
                .build());
    }

    @Test
    void normalizeForResolutionCommitsUserCopyWithoutTimelineSyncWhenCallerRollsBack() {
        Point point = coordinateScope.point(30.5234, 50.4501);
        SeededTimelineStay seeded = createCommittedOriginalAndStay(point);

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().call(() -> {
                    ReverseGeocodingDTO normalized = managementService
                            .normalizeGeocodingForResolution(userId, seeded.originalGeocodingId());

                    assertNotEquals(seeded.originalGeocodingId(), normalized.getId());
                    assertTrue(normalized.isUserSpecific());
                    assertEquals(NORMALIZED_CITY, normalized.getCity());
                    assertEquals(NORMALIZED_COUNTRY, normalized.getCountry());

                    throw new RuntimeException("rollback timeline resolution caller");
                }));

        assertEquals("rollback timeline resolution caller", failure.getMessage());

        ReverseGeocodingLocationEntity userCopy = findUserCopy();
        assertEquals(NORMALIZED_CITY, userCopy.getCity());
        assertEquals(NORMALIZED_COUNTRY, userCopy.getCountry());

        TimelineStaySnapshot stay = findStay(seeded.stayId());
        assertEquals(seeded.originalGeocodingId(), stay.geocodingId(),
                "Resolution normalization must not remap existing timeline rows outside the caller transaction");
        assertEquals(ORIGINAL_DISPLAY_NAME, stay.locationName());
    }

    private SeededTimelineStay createCommittedOriginalAndStay(Point point) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UserEntity user = entityManager.find(UserEntity.class, userId);

            ReverseGeocodingLocationEntity original = new ReverseGeocodingLocationEntity();
            original.setRequestCoordinates(point);
            original.setResultCoordinates(point);
            original.setDisplayName(ORIGINAL_DISPLAY_NAME);
            original.setProviderName(PROVIDER_NAME);
            original.setCity(ORIGINAL_CITY);
            original.setCountry(ORIGINAL_COUNTRY);
            entityManager.persist(original);
            entityManager.flush();

            TimelineStayEntity stay = TimelineStayEntity.builder()
                    .user(user)
                    .timestamp(Instant.parse("2026-01-01T10:00:00Z"))
                    .stayDuration(900)
                    .location(point)
                    .locationName(ORIGINAL_DISPLAY_NAME)
                    .geocodingLocation(original)
                    .locationSource(LocationSource.GEOCODING)
                    .build();
            entityManager.persist(stay);
            entityManager.flush();

            return new SeededTimelineStay(original.getId(), stay.getId());
        });
    }

    private ReverseGeocodingLocationEntity findUserCopy() {
        return QuarkusTransaction.requiringNew().call(() -> {
            List<ReverseGeocodingLocationEntity> copies = entityManager.createQuery("""
                            SELECT r
                            FROM ReverseGeocodingLocationEntity r
                            WHERE r.user.id = :userId
                              AND r.providerName = :providerName
                            """, ReverseGeocodingLocationEntity.class)
                    .setParameter("userId", userId)
                    .setParameter("providerName", PROVIDER_NAME)
                    .getResultList();

            assertEquals(1, copies.size(), "A single user-specific normalized copy should be committed");
            return copies.get(0);
        });
    }

    private TimelineStaySnapshot findStay(Long stayId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TimelineStayEntity stay = entityManager.find(TimelineStayEntity.class, stayId);
            return new TimelineStaySnapshot(stay.getGeocodingLocation().getId(), stay.getLocationName());
        });
    }

    private record SeededTimelineStay(Long originalGeocodingId, Long stayId) {
    }

    private record TimelineStaySnapshot(Long geocodingId, String locationName) {
    }
}
