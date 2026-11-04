package org.github.tess1o.geopulse.geocoding.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.ReconciliationJobProgressService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.JobPolling;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/geocoding}.
 *
 * <p>Ownership rules worth stating explicitly, because they are the point of this class: a row with
 * {@code user_id = NULL} is an "original" and readable by anyone; a row owned by another user is
 * 403. The management listing is narrower than the single read — it shows only rows the user owns
 * or that back their timeline stays.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ReverseGeocodingContractTest {

    private static final String GEOCODING = "/api/v1/geocoding";
    private static final String PROVIDER = "test-provider";
    private static final String PASSWORD = "password123";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject ReverseGeocodingLocationRepository geocodingRepository;
    @Inject ReconciliationJobProgressService reconciliationProgressService;

    @Inject jakarta.persistence.EntityManager entityManager;
    private org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory provider;
    private TestCoordinates.Scope coords;
    private UserEntity owner;
    private UserEntity other;
    private String ownerToken;
    private String otherToken;

    private long ownerRowId;
    private long sharedRowId;
    private long otherRowId;

    @BeforeEach
    @Transactional
    void setUp() {
        // Keeps every provider call off the network.
        provider = GeocodingTestMocks.install();
        org.mockito.Mockito.when(provider.getEnabledProviders()).thenReturn(List.of(PROVIDER));
        org.mockito.Mockito.when(provider.reconciliationCandidates(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    var first = GeocodingTestMocks.result(invocation.getArgument(1));
                    var second = GeocodingTestMocks.result(invocation.getArgument(1));
                    second.setFormattedDisplayName("Selected second candidate");
                    second.setResultCoordinates(org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(30.521, 50.451));
                    return io.smallrye.mutiny.Uni.createFrom().item(List.of(
                            new org.github.tess1o.geopulse.geocoding.model.common.GeocodingCandidate(first, null, null),
                            new org.github.tess1o.geopulse.geocoding.model.common.GeocodingCandidate(second, "cafe", "Street")));
                });
        coords = TestCoordinates.newScope();

        owner = register("geocoding-owner");
        other = register("geocoding-other");
        ownerToken = token(owner);
        otherToken = token(other);

        ownerRowId = seedRow(owner, "Owner geocoding row").getId();
        sharedRowId = seedRow(null, "Shared geocoding row").getId();
        otherRowId = seedRow(other, "Other geocoding row").getId();
    }

    @Test
    void listsAndReadsOwnGeocodingResults() {
        Response page = authenticated(ownerToken)
                .queryParam("providerName", PROVIDER)
                .queryParam("page", 1)
                .queryParam("limit", 50)
                .when().get(GEOCODING);

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.jsonPath().getInt("page")).isEqualTo(1);
        assertThat(page.jsonPath().getList("items.displayName", String.class))
                .contains("Owner geocoding row");

        Response single = authenticated(ownerToken).when().get(GEOCODING + "/" + ownerRowId);
        assertThat(single.statusCode()).isEqualTo(200);
        assertThat(single.jsonPath().getString("displayName")).isEqualTo("Owner geocoding row");
        assertThat(single.jsonPath().getString("providerName")).isEqualTo(PROVIDER);
    }

    @Test
    void originalsAreReadableByAnyoneButAnotherUsersCopyIsForbidden() {
        // user_id = NULL is shared provider data, readable by every user.
        Response original = authenticated(ownerToken).when().get(GEOCODING + "/" + sharedRowId);
        assertThat(original.statusCode()).isEqualTo(200);
        assertThat(original.jsonPath().getString("displayName")).isEqualTo("Shared geocoding row");

        // user_id = someone else is not.
        Response foreign = authenticated(ownerToken).when().get(GEOCODING + "/" + otherRowId);
        assertProblemEnvelope(foreign, 403, "GEOCODING_ACCESS_DENIED");
    }

    @Test
    void updatesOwnResultInPlaceAndCopiesOnWriteForOriginals() {
        Response updated = authenticated(ownerToken)
                .body(Map.of("displayName", "Renamed owner row", "city", "Lviv", "country", "Ukraine"))
                .when().put(GEOCODING + "/" + ownerRowId);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.jsonPath().getString("displayName")).isEqualTo("Renamed owner row");

        // Editing an original must not mutate the shared row — it creates a user-owned copy.
        Response copy = authenticated(ownerToken)
                .body(Map.of("displayName", "My copy of shared row"))
                .when().put(GEOCODING + "/" + sharedRowId);
        assertThat(copy.statusCode()).isEqualTo(200);
        assertThat(copy.jsonPath().getString("displayName")).isEqualTo("My copy of shared row");

        Response originalStillIntact = authenticated(otherToken).when().get(GEOCODING + "/" + sharedRowId);
        assertThat(originalStillIntact.statusCode()).isEqualTo(200);
        assertThat(originalStillIntact.jsonPath().getString("displayName"))
                .as("copy-on-write must leave the shared original untouched")
                .isEqualTo("Shared geocoding row");
    }

    @Test
    void updatingAnotherUsersCopyIsForbidden() {
        Response response = authenticated(ownerToken)
                .body(Map.of("displayName", "Hijacked"))
                .when().put(GEOCODING + "/" + otherRowId);
        assertProblemEnvelope(response, 403, "GEOCODING_ACCESS_DENIED");
    }

    @Test
    void bulkUpdateReportsPerItemOutcomes() {
        Response response = authenticated(ownerToken)
                .body(Map.of(
                        "geocodingIds", List.of(ownerRowId),
                        "updateCity", true,
                        "city", "Kyiv"))
                .when().patch(GEOCODING + "/bulk-update");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getInt("totalRequested")).isEqualTo(1);
        assertThat(response.jsonPath().getInt("successCount")).isEqualTo(1);
        assertThat(response.jsonPath().getInt("failedCount")).isZero();
    }

    @Test
    void normalizationRulesSupportFullCrud() {
        Response created = authenticated(ownerToken)
                .body(Map.of("ruleType", "COUNTRY", "sourceCountry", "USA", "targetCountry", "United States"))
                .when().post(GEOCODING + "/normalization-rules");
        assertThat(created.statusCode()).isEqualTo(201);
        long ruleId = created.jsonPath().getLong("id");

        Response listed = authenticated(ownerToken).when().get(GEOCODING + "/normalization-rules");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.jsonPath().getList("id", Long.class)).contains(ruleId);

        Response updated = authenticated(ownerToken)
                .body(Map.of("ruleType", "CITY", "sourceCity", "Kiev", "targetCity", "Kyiv"))
                .when().put(GEOCODING + "/normalization-rules/" + ruleId);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.jsonPath().getString("ruleType")).isEqualTo("CITY");

        Response deleted = authenticated(ownerToken).when().delete(GEOCODING + "/normalization-rules/" + ruleId);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(authenticated(ownerToken).when().get(GEOCODING + "/normalization-rules")
                .jsonPath().getList("id", Long.class)).doesNotContain(ruleId);
    }

    @Test
    void normalizationRuleFailuresUseExactCodes() {
        // COUNTRY rule without a target country trips @AssertTrue -> bean validation, not a domain code.
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("ruleType", "COUNTRY", "sourceCountry", "USA"))
                        .when().post(GEOCODING + "/normalization-rules"),
                400, "VALIDATION_FAILED");

        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("ruleType", "CITY", "sourceCity", "A", "targetCity", "B"))
                        .when().put(GEOCODING + "/normalization-rules/999999999"),
                404, "NORMALIZATION_RULE_NOT_FOUND");

        assertProblemEnvelope(authenticated(ownerToken)
                        .when().delete(GEOCODING + "/normalization-rules/999999999"),
                404, "NORMALIZATION_RULE_NOT_FOUND");
    }

    @Test
    void validationAndNotFoundContracts() {
        assertProblemEnvelope(authenticated(ownerToken).queryParam("page", 0).when().get(GEOCODING),
                400, "INVALID_GEOCODING_REQUEST");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("limit", 0).when().get(GEOCODING),
                400, "INVALID_GEOCODING_REQUEST");

        assertProblemEnvelope(authenticated(ownerToken).when().get(GEOCODING + "/999999999"),
                404, "GEOCODING_RESULT_NOT_FOUND");

        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("displayName", "   "))
                        .when().put(GEOCODING + "/" + ownerRowId),
                400, "VALIDATION_FAILED");
    }

    @Test
    void distinctValuesAndProvidersAreReadable() {
        Response distinct = authenticated(ownerToken).when().get(GEOCODING + "/distinct-values");
        assertThat(distinct.statusCode()).isEqualTo(200);

        Response available = authenticated(ownerToken).when().get(GEOCODING + "/providers/available");
        assertThat(available.statusCode()).isEqualTo(200);
        assertThat(available.jsonPath().getList("$", String.class)).contains(PROVIDER);
    }

    @Test
    void reconcileSingleWithNoIdsPerformsNoWork() {
        Response response = authenticated(ownerToken)
                .body(Map.of("providerName", PROVIDER, "geocodingIds", List.of(), "reconcileAll", false))
                .when().post(GEOCODING + "/reconcile/single");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getInt("totalProcessed")).isZero();
        assertThat(response.jsonPath().getInt("successfulUpdates")).isZero();
        assertThat(response.jsonPath().getInt("failedUpdates")).isZero();
    }

    @Test
    void reconcileBulkStartsAJobThatReportsProgress() {
        Response started = authenticated(ownerToken).body(reconcileBody(List.of(ownerRowId)))
                .when().post(GEOCODING + "/reconcile/bulk");
        assertThat(started.statusCode()).isEqualTo(200);
        UUID jobId = UUID.fromString(started.jsonPath().getString("jobId"));

        Response progress = authenticated(ownerToken).when().get(GEOCODING + "/reconcile/jobs/" + jobId);
        assertThat(progress.statusCode()).isEqualTo(200);
        assertThat(progress.jsonPath().getString("jobId")).isEqualTo(jobId.toString());
        assertThat(progress.jsonPath().getString("userId")).isEqualTo(owner.getId().toString());

        // Drain it: job state is an in-process map shared across tests in this JVM.
        JobPolling.awaitTerminal(reconciliationProgressService, jobId);
    }

    @Test
    void reconciliationJobFailuresUseExactCodes() {
        assertProblemEnvelope(authenticated(ownerToken).when().get(GEOCODING + "/reconcile/jobs/not-a-uuid"),
                400, "INVALID_RECONCILIATION_JOB_ID");

        assertProblemEnvelope(
                authenticated(ownerToken).when().get(GEOCODING + "/reconcile/jobs/" + UUID.randomUUID()),
                404, "RECONCILIATION_JOB_NOT_FOUND");
    }

    @Test
    void anotherUsersJobIsForbiddenAndAnActiveJobConflicts() {
        UUID otherJobId = reconciliationProgressService.createJob(other.getId(), PROVIDER, 1);
        try {
            assertProblemEnvelope(
                    authenticated(ownerToken).when().get(GEOCODING + "/reconcile/jobs/" + otherJobId),
                    403, "RECONCILIATION_JOB_ACCESS_DENIED");
        } finally {
            reconciliationProgressService.completeJob(otherJobId);
        }

        // A second reconcile while one is active is rejected before any work starts.
        UUID activeJobId = reconciliationProgressService.createJob(owner.getId(), PROVIDER, 1);
        try {
            assertProblemEnvelope(authenticated(ownerToken).body(reconcileBody(List.of(ownerRowId)))
                            .when().post(GEOCODING + "/reconcile/bulk"),
                    409, "RECONCILIATION_ALREADY_ACTIVE");
        } finally {
            reconciliationProgressService.completeJob(activeJobId);
        }
    }

    @Test
    void previewsAndSelectsSecondCandidateWithCopyOnWriteAndLinkedStayIsolation() {
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            var shared = geocodingRepository.findById(sharedRowId);
            for (var user : List.of(owner, owner, other)) {
                var stay = new org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity();
                stay.setUser(entityManager.getReference(UserEntity.class, user.getId()));
                stay.setGeocodingLocation(shared);
                stay.setLocation(shared.getRequestCoordinates());
                stay.setLocationName(shared.getDisplayName());
                stay.setLocationSource(org.github.tess1o.geopulse.streaming.model.domain.LocationSource.GEOCODING);
                stay.setTimestamp(Instant.now().minusSeconds(3600));
                stay.setStayDuration(600L);
                stay.setCreatedAt(Instant.now());
                stay.setLastUpdated(Instant.now());
                entityManager.persist(stay);
            }
        });
        var preview = authenticated(ownerToken).body(Map.of("providerName", PROVIDER))
                .post(GEOCODING + "/" + sharedRowId + "/reconciliation-candidates");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.jsonPath().getList("candidates")).hasSize(2);
        var selection = Map.of("previewId", preview.jsonPath().getString("previewId"), "candidateId", 1);
        assertProblemEnvelope(authenticated(otherToken).body(selection)
                .post(GEOCODING + "/" + sharedRowId + "/reconciliation-selection"), 400, "INVALID_GEOCODING_REQUEST");
        var saved = authenticated(ownerToken).body(selection)
                .post(GEOCODING + "/" + sharedRowId + "/reconciliation-selection");
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(saved.jsonPath().getString("displayName")).isEqualTo("Selected second candidate");
        long copyId = saved.jsonPath().getLong("id");
        assertThat(copyId).isNotEqualTo(sharedRowId);
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            assertThat(geocodingRepository.findById(sharedRowId).getDisplayName()).isEqualTo("Shared geocoding row");
            var copies = entityManager.createQuery("select t from TimelineStayEntity t where t.user.id = :user and t.geocodingLocation.id = :id",
                    org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity.class)
                    .setParameter("user", owner.getId()).setParameter("id", copyId).getResultList();
            assertThat(copies).hasSize(2).allSatisfy(stay -> assertThat(stay.getLocationName()).isEqualTo("Selected second candidate"));
            var foreign = entityManager.createQuery("select t from TimelineStayEntity t where t.user.id = :user and t.geocodingLocation.id = :id",
                    org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity.class)
                    .setParameter("user", other.getId()).setParameter("id", sharedRowId).getResultList();
            assertThat(foreign).hasSize(1);
        });
    }

    @Test
    void candidateEndpointsRejectForeignEntriesAndInvalidSelections() {
        assertProblemEnvelope(authenticated(ownerToken).body(Map.of("providerName", PROVIDER))
                .post(GEOCODING + "/" + otherRowId + "/reconciliation-candidates"), 403, "GEOCODING_ACCESS_DENIED");
        assertThat(authenticated(ownerToken).body(Map.of("providerName", ""))
                .post(GEOCODING + "/" + ownerRowId + "/reconciliation-candidates").statusCode()).isEqualTo(400);
        assertThat(authenticated(ownerToken).body(Map.of("previewId", UUID.randomUUID(), "candidateId", 10))
                .post(GEOCODING + "/" + ownerRowId + "/reconciliation-selection").statusCode()).isEqualTo(400);
    }

    private static Map<String, Object> reconcileBody(List<Long> ids) {
        return Map.of("providerName", PROVIDER, "geocodingIds", ids, "reconcileAll", false);
    }

    private UserEntity register(String prefix) {
        String email = TestIds.uniqueEmail(prefix);
        return userService.registerUser(email, PASSWORD, prefix + " User", "UTC");
    }

    private String token(UserEntity user) {
        return authenticationService.authenticate(user.getEmail(), PASSWORD).getAccessToken();
    }

    private ReverseGeocodingLocationEntity seedRow(UserEntity user, String displayName) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setUser(user);
        entity.setRequestCoordinates(coords.point(30.52, 50.45));
        entity.setResultCoordinates(coords.point(30.52, 50.45));
        entity.setDisplayName(displayName);
        entity.setProviderName(PROVIDER);
        entity.setCreatedAt(Instant.now());
        geoCodingPersist(entity);
        return entity;
    }

    private void geoCodingPersist(ReverseGeocodingLocationEntity entity) {
        geocodingRepository.persist(entity);
        geocodingRepository.flush();
    }

    private static RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
}
