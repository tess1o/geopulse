package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.dto.ApplyNormalizationRulesRequest;
import org.github.tess1o.geopulse.geocoding.dto.CreateNormalizationRuleRequest;
import org.github.tess1o.geopulse.geocoding.dto.NormalizationRuleDto;
import org.github.tess1o.geopulse.geocoding.model.NormalizationRuleType;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.repository.UserLocationNormalizationRuleRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class UserLocationNormalizationServiceTest {

    @Inject
    UserLocationNormalizationService normalizationService;

    @Inject
    ReverseGeocodingManagementService managementService;

    @Inject
    ReconciliationJobProgressService jobProgressService;

    @Inject
    ReverseGeocodingLocationRepository geocodingRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    UserLocationNormalizationRuleRepository ruleRepository;

    @Inject
    EntityManager entityManager;

    private UUID userId;
    private UUID otherUserId;
    private TestCoordinates.Scope coordinateScope;

    @BeforeEach
    @Transactional
    void setupUsers() {
        coordinateScope = TestCoordinates.newScope();

        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail("norm-user"))
                .fullName("Normalization User")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(user);

        UserEntity otherUser = UserEntity.builder()
                .email(TestIds.uniqueEmail("norm-other"))
                .fullName("Other User")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(otherUser);
        entityManager.flush();

        userId = user.getId();
        otherUserId = otherUser.getId();
    }

    @Test
    @Transactional
    @DisplayName("CRUD: duplicate country source for same user is rejected")
    void duplicateCountryRuleRejected() {
        CreateNormalizationRuleRequest first = CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.COUNTRY)
                .sourceCountry("Болгарія")
                .targetCountry("Bulgaria")
                .build();
        normalizationService.createRule(userId, first);

        CreateNormalizationRuleRequest duplicate = CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.COUNTRY)
                .sourceCountry(" болгарія ")
                .targetCountry("Bulgaria")
                .build();

        assertThrows(IllegalArgumentException.class, () -> normalizationService.createRule(userId, duplicate));
        assertEquals(1, ruleRepository.findByUserId(userId).size());
    }

    @Test
    @Transactional
    @DisplayName("CRUD: duplicate city source for same user is rejected")
    void duplicateCityRuleRejected() {
        normalizationService.createRule(userId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.CITY)
                .sourceCity("София")
                .targetCity("Sofia")
                .build());

        CreateNormalizationRuleRequest duplicate = CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.CITY)
                .sourceCity(" софия ")
                .targetCity("Софія")
                .build();

        assertThrows(IllegalArgumentException.class, () -> normalizationService.createRule(userId, duplicate));
        assertEquals(1, ruleRepository.findByUserId(userId).stream()
                .filter(rule -> rule.getRuleType() == NormalizationRuleType.CITY)
                .count());
    }

    @Test
    @Transactional
    @DisplayName("Matching: city mapping uses trim/case/unicode normalization and keeps country")
    void cityMatchingNormalization() {
        normalizationService.createRule(userId, CreateNormalizationRuleRequest.builder()
                .ruleType(NormalizationRuleType.CITY)
                .sourceCity("София")
                .targetCity("Sofia")
                .build());

        UserLocationNormalizationService.NormalizedLocation result =
                normalizationService.normalizeForUser(userId, "  СОФИЯ  ", "болгарія");

        assertTrue(result.changed());
        assertEquals("Sofia", result.city());
        assertEquals("болгарія", result.country());
    }

    @Test
    @Transactional
    @DisplayName("Apply: updates geocoding via copy-on-write and favorites directly")
    void applyRulesUpdatesGeocodingAndFavorites() {
        Point coords = coordinateScope.point(-73.9851, 40.7589);

        ReverseGeocodingLocationEntity original = new ReverseGeocodingLocationEntity();
        original.setRequestCoordinates(coords);
        original.setResultCoordinates(coords);
        original.setDisplayName("Central Spot");
        original.setProviderName("nominatim");
        original.setCity("София");
        original.setCountry("Болгарія");
        original.setCreatedAt(Instant.now());
        original.setLastAccessedAt(Instant.now());
        geocodingRepository.persist(original);
        entityManager.flush();

        createTimelineStay(userId, original.getId(), coords, "Central Spot");

        FavoritesEntity favorite = FavoritesEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .geometry(coords)
                .name("Fav Spot")
                .type(FavoriteLocationType.POINT)
                .city("София")
                .country("Болгарія")
                .build();
        favoritesRepository.persist(favorite);
        entityManager.flush();

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

        UUID jobId = managementService.applyNormalizationRulesAsync(userId,
                ApplyNormalizationRulesRequest.builder()
                        .applyToGeocoding(true)
                        .applyToFavorites(true)
                        .build());

        waitForJob(jobId);

        List<ReverseGeocodingLocationEntity> userVisible = geocodingRepository.findForUserManagementPage(
                userId, null, null, null, null, 1, 100, null, null);
        assertTrue(userVisible.stream().anyMatch(g -> "Sofia".equals(g.getCity()) && "Bulgaria".equals(g.getCountry())));

        FavoritesEntity updatedFavorite = favoritesRepository.findById(favorite.getId());
        assertEquals("Sofia", updatedFavorite.getCity());
        assertEquals("Bulgaria", updatedFavorite.getCountry());

        List<Object[]> stays = getTimelineStaysForUser(userId);
        assertEquals(1, stays.size());
        Long stayGeocodingId = ((Number) stays.getFirst()[0]).longValue();
        assertNotEquals(original.getId(), stayGeocodingId, "timeline should be remapped to user copy");

        Map<String, Object> metadata = jobProgressService.getJobProgress(jobId).orElseThrow().getMetadata();
        assertNotNull(metadata);
        assertEquals("normalization-rules", metadata.get("mode"));
    }

    @Test
    @Transactional
    @DisplayName("Auth: user cannot delete another user's rule")
    void userCannotDeleteAnotherUsersRule() {
        NormalizationRuleDto otherRule = normalizationService.createRule(otherUserId,
                CreateNormalizationRuleRequest.builder()
                        .ruleType(NormalizationRuleType.COUNTRY)
                        .sourceCountry("Deutschland")
                        .targetCountry("Germany")
                        .build());

        assertThrows(ForbiddenException.class, () -> normalizationService.deleteRule(userId, otherRule.getId()));
    }

    private void waitForJob(UUID jobId) {
        long timeoutAt = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < timeoutAt) {
            var progress = jobProgressService.getJobProgress(jobId);
            if (progress.isPresent() && progress.get().isTerminal()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for job");
            }
        }
        fail("Timed out waiting for normalization job");
    }

    private void createTimelineStay(UUID owner, Long geocodingId, Point coords, String locationName) {
        String sql = """
                INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name,
                                           geocoding_id, created_at, last_updated, location_source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'GEOCODING')
                """;
        entityManager.createNativeQuery(sql)
                .setParameter(1, owner)
                .setParameter(2, Instant.now())
                .setParameter(3, 300L)
                .setParameter(4, coords)
                .setParameter(5, locationName)
                .setParameter(6, geocodingId)
                .setParameter(7, Instant.now())
                .setParameter(8, Instant.now())
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> getTimelineStaysForUser(UUID owner) {
        String sql = "SELECT geocoding_id, location_name FROM timeline_stays WHERE user_id = ? ORDER BY id";
        return entityManager.createNativeQuery(sql)
                .setParameter(1, owner)
                .getResultList();
    }
}
