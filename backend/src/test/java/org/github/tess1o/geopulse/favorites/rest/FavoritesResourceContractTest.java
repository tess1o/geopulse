package org.github.tess1o.geopulse.favorites.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReconciliationJobProgress;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.geocoding.service.ReconciliationJobProgressService;
import org.github.tess1o.geopulse.testsupport.AsyncTimelineTestMocks;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;
import static org.github.tess1o.geopulse.testsupport.GeocodingTestMocks.failWith;
import static org.github.tess1o.geopulse.testsupport.JobPolling.awaitTerminal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class FavoritesResourceContractTest {

    private static final String FAVORITES = "/api/v1/favorites";
    private static final String PASSWORD = "password123";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject ReconciliationJobProgressService reconciliationProgressService;

    private GeocodingProviderFactory geocodingProvider;
    private UUID ownerId;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    @Transactional
    void setUp() {
        geocodingProvider = GeocodingTestMocks.install();
        AsyncTimelineTestMocks.install();

        String ownerEmail = TestIds.uniqueEmail("favorites-owner");
        UserEntity owner = userService.registerUser(ownerEmail, PASSWORD, "Favorites Owner", "UTC");
        ownerId = owner.getId();
        ownerToken = authenticationService.authenticate(ownerEmail, PASSWORD).getAccessToken();

        String otherEmail = TestIds.uniqueEmail("favorites-other");
        userService.registerUser(otherEmail, PASSWORD, "Favorites Other", "UTC");
        otherToken = authenticationService.authenticate(otherEmail, PASSWORD).getAccessToken();
    }

    @Test
    void createsAndListsPointAndAreaWithoutCrossTenantLeak() {
        String ownerPoint = TestIds.uniqueValue("owner-point");
        String ownerArea = TestIds.uniqueValue("owner-area");
        String otherPoint = TestIds.uniqueValue("other-point");

        createPoint(otherToken, otherPoint);
        createPoint(ownerToken, ownerPoint);
        createArea(ownerToken, ownerArea);

        Response response = authenticated(ownerToken).when().get(FAVORITES);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(names(response, "points")).contains(ownerPoint).doesNotContain(otherPoint);
        assertThat(names(response, "areas")).contains(ownerArea);
    }

    @Test
    void updatesDeletesAndListsDistinctValues() {
        String originalName = TestIds.uniqueValue("editable-point");
        long favoriteId = createPoint(ownerToken, originalName);
        String updatedName = TestIds.uniqueValue("updated-point");

        Response update = authenticated(ownerToken)
                .body(Map.of(
                        "name", updatedName,
                        "city", "Kyiv",
                        "country", "Ukraine"))
                .when().put(FAVORITES + "/" + favoriteId);
        assertThat(update.statusCode()).isEqualTo(200);

        Response afterUpdate = authenticated(ownerToken).when().get(FAVORITES);
        assertThat(favorite(afterUpdate, "points", updatedName))
                .containsEntry("city", "Kyiv")
                .containsEntry("country", "Ukraine");

        Response distinct = authenticated(ownerToken).when().get(FAVORITES + "/distinct-values");
        assertThat(distinct.statusCode()).isEqualTo(200);
        assertThat(distinct.jsonPath().getList("cities", String.class)).contains("Kyiv");
        assertThat(distinct.jsonPath().getList("countries", String.class)).contains("Ukraine");

        assertJobResponse(authenticated(ownerToken).when().delete(FAVORITES + "/" + favoriteId), 200);
        assertThat(names(authenticated(ownerToken).when().get(FAVORITES), "points")).doesNotContain(updatedName);
    }

    @Test
    void bulkCreatesAndUpdatesFavorites() {
        String pointName = TestIds.uniqueValue("bulk-point");
        String areaName = TestIds.uniqueValue("bulk-area");

        Response create = authenticated(ownerToken)
                .body(Map.of(
                        "points", List.of(pointBody(pointName)),
                        "areas", List.of(areaBody(areaName))))
                .when().post(FAVORITES + "/bulk");
        assertThat(create.statusCode()).isEqualTo(201);
        assertThat(create.jsonPath().getInt("totalRequested")).isEqualTo(2);
        assertThat(create.jsonPath().getInt("successCount")).isEqualTo(2);
        assertThat(create.jsonPath().getInt("failedCount")).isZero();
        List<Number> createdIds = create.jsonPath().getList("createdFavoriteIds", Number.class);
        assertThat(createdIds).hasSize(2);
        assertUuid(create.jsonPath().getString("jobId"));

        List<Long> favoriteIds = createdIds.stream().map(Number::longValue).toList();
        Response update = authenticated(ownerToken)
                .body(Map.of(
                        "favoriteIds", favoriteIds,
                        "updateCity", true,
                        "city", "Lviv",
                        "updateCountry", true,
                        "country", "Ukraine"))
                .when().patch(FAVORITES + "/bulk-update");
        assertThat(update.statusCode()).isEqualTo(200);
        assertThat(update.jsonPath().getInt("totalRequested")).isEqualTo(2);
        assertThat(update.jsonPath().getInt("successCount")).isEqualTo(2);
        assertThat(update.jsonPath().getInt("failedCount")).isZero();

        Response favorites = authenticated(ownerToken).when().get(FAVORITES);
        assertThat(favorite(favorites, "points", pointName)).containsEntry("city", "Lviv");
        assertThat(favorite(favorites, "areas", areaName)).containsEntry("city", "Lviv");
    }

    @Test
    void startsAndReadsReconciliationJob() {
        long favoriteId = createPoint(ownerToken, TestIds.uniqueValue("reconcile-point"));

        UUID jobId = assertJobResponse(reconcile(ownerToken, favoriteId), 200);
        Response progress = authenticated(ownerToken).when().get(FAVORITES + "/reconcile/jobs/" + jobId);

        assertThat(progress.statusCode()).isEqualTo(200);
        assertThat(progress.jsonPath().getString("jobId")).isEqualTo(jobId.toString());
        assertThat(progress.jsonPath().getString("userId")).isEqualTo(ownerId.toString());
        assertThat(progress.jsonPath().getString("providerName")).isEqualTo("test-provider");
        assertThat(progress.jsonPath().getString("status")).isNotBlank();
        awaitTerminal(reconciliationProgressService, jobId);
    }

    @Test
    void validationFailureUsesProblemEnvelopeAndOneViolation() {
        Response response = authenticated(ownerToken)
                .body(Map.of("lat", 50.45, "lon", 30.52))
                .when().post(FAVORITES + "/points");

        assertProblemEnvelope(response, 400, "VALIDATION_FAILED");
        List<Map<String, Object>> violations = response.jsonPath().getList("violations");
        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.get("field")).isEqualTo("name");
            assertThat(violation.get("code")).isEqualTo("NOT_BLANK");
        });
    }

    @Test
    void domainValidationFailuresUseExactCodes() {
        Response emptyBulk = authenticated(ownerToken)
                .body(Map.of("points", List.of(), "areas", List.of()))
                .when().post(FAVORITES + "/bulk");
        assertProblemEnvelope(emptyBulk, 400, "NO_FAVORITES_PROVIDED");

        long pointId = createPoint(ownerToken, TestIds.uniqueValue("point-with-invalid-bounds"));
        Response invalidBounds = authenticated(ownerToken)
                .body(Map.of(
                        "name", "Still a point",
                        "northEastLat", 51.0,
                        "northEastLon", 31.0,
                        "southWestLat", 50.0,
                        "southWestLon", 30.0))
                .when().put(FAVORITES + "/" + pointId);
        assertProblemEnvelope(invalidBounds, 400, "INVALID_FAVORITE");

        Response invalidJobId = authenticated(ownerToken)
                .when().get(FAVORITES + "/reconcile/jobs/not-a-uuid");
        assertProblemEnvelope(invalidJobId, 400, "INVALID_RECONCILIATION_JOB_ID");
    }

    @Test
    void crossTenantFavoriteMutationsAreForbidden() {
        long otherFavoriteId = createPoint(otherToken, TestIds.uniqueValue("other-owned-point"));

        Response update = authenticated(ownerToken)
                .body(Map.of("name", "Cannot rename"))
                .when().put(FAVORITES + "/" + otherFavoriteId);
        assertProblemEnvelope(update, 403, "FAVORITE_ACCESS_DENIED");

        Response delete = authenticated(ownerToken).when().delete(FAVORITES + "/" + otherFavoriteId);
        assertProblemEnvelope(delete, 403, "FAVORITE_ACCESS_DENIED");
    }

    @Test
    void crossTenantReconciliationJobReadIsForbidden() {
        long otherFavoriteId = createPoint(otherToken, TestIds.uniqueValue("other-reconcile-point"));
        UUID otherJobId = assertJobResponse(reconcile(otherToken, otherFavoriteId), 200);

        Response response = authenticated(ownerToken)
                .when().get(FAVORITES + "/reconcile/jobs/" + otherJobId);
        assertProblemEnvelope(response, 403, "RECONCILIATION_JOB_ACCESS_DENIED");
        awaitTerminal(reconciliationProgressService, otherJobId);
    }

    @Test
    void missingFavoriteAndJobUseExactNotFoundCodes() {
        Response missingFavorite = authenticated(ownerToken).when().delete(FAVORITES + "/999999");
        assertProblemEnvelope(missingFavorite, 404, "FAVORITE_NOT_FOUND");

        Response missingJob = authenticated(ownerToken)
                .when().get(FAVORITES + "/reconcile/jobs/" + UUID.randomUUID());
        assertProblemEnvelope(missingJob, 404, "RECONCILIATION_JOB_NOT_FOUND");
    }

    @Test
    void secondReconciliationWhileFirstIsActiveReturnsConflict() throws InterruptedException {
        long favoriteId = createPoint(ownerToken, TestIds.uniqueValue("active-reconcile-point"));
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        when(geocodingProvider.reverseGeocode(any(Point.class))).thenAnswer(invocation -> {
            Point point = invocation.getArgument(0);
            return Uni.createFrom().item(() -> {
                providerEntered.countDown();
                try {
                    if (!releaseProvider.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release test geocoder");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return GeocodingTestMocks.result(point);
            });
        });

        UUID firstJobId = assertJobResponse(reconcile(ownerToken, favoriteId), 200);
        try {
            assertThat(providerEntered.await(10, TimeUnit.SECONDS)).isTrue();
            assertProblemEnvelope(reconcile(ownerToken, favoriteId), 409, "RECONCILIATION_ALREADY_ACTIVE");
        } finally {
            releaseProvider.countDown();
        }
        awaitTerminal(reconciliationProgressService, firstJobId);
    }

    @Test
    void providerFailureUsesFallbackAndRemainsAnAsyncSuccess() {
        long favoriteId = createPoint(ownerToken, TestIds.uniqueValue("failed-reconcile-point"));
        failWith(geocodingProvider, new RuntimeException("provider down"));

        UUID jobId = assertJobResponse(reconcile(ownerToken, favoriteId), 200);
        ReconciliationJobProgress job = awaitTerminal(reconciliationProgressService, jobId);

        assertThat(job.getStatus()).isEqualTo(ReconciliationJobProgress.JobStatus.COMPLETED);
        assertThat(job.getSuccessCount()).isEqualTo(1);
        assertThat(job.getFailedCount()).isZero();

        Response progress = authenticated(ownerToken).when().get(FAVORITES + "/reconcile/jobs/" + jobId);
        assertThat(progress.statusCode()).isEqualTo(200);
        assertThat(progress.jsonPath().getInt("successCount")).isEqualTo(1);
        assertThat(progress.jsonPath().getInt("failedCount")).isZero();
    }

    private long createPoint(String token, String name) {
        Response response = authenticated(token).body(pointBody(name)).when().post(FAVORITES + "/points");
        assertJobResponse(response, 201);
        return favoriteId(token, "points", name);
    }

    private long createArea(String token, String name) {
        Response response = authenticated(token).body(areaBody(name)).when().post(FAVORITES + "/areas");
        assertJobResponse(response, 201);
        return favoriteId(token, "areas", name);
    }

    private Response reconcile(String token, long favoriteId) {
        return authenticated(token)
                .body(Map.of(
                        "providerName", "test-provider",
                        "favoriteIds", List.of(favoriteId),
                        "reconcileAll", false))
                .when().post(FAVORITES + "/reconcile/bulk");
    }

    private RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }

    private static Map<String, Object> pointBody(String name) {
        return Map.of("name", name, "lat", 50.45, "lon", 30.52);
    }

    private static Map<String, Object> areaBody(String name) {
        return Map.of(
                "name", name,
                "northEastLat", 50.50,
                "northEastLon", 30.60,
                "southWestLat", 50.40,
                "southWestLon", 30.40);
    }

    private static List<String> names(Response response, String collection) {
        assertThat(response.statusCode()).isEqualTo(200);
        return response.jsonPath().getList(collection + ".name", String.class);
    }

    private static Map<String, Object> favorite(Response response, String collection, String name) {
        assertThat(response.statusCode()).isEqualTo(200);
        return response.jsonPath().getList(collection, Map.class).stream()
                .map(entry -> (Map<String, Object>) entry)
                .filter(entry -> name.equals(entry.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Favorite not found: " + name));
    }

    private long favoriteId(String token, String collection, String name) {
        Object id = favorite(authenticated(token).when().get(FAVORITES), collection, name).get("id");
        return ((Number) id).longValue();
    }

    private static UUID assertJobResponse(Response response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        return assertUuid(response.jsonPath().getString("jobId"));
    }

    private static UUID assertUuid(String value) {
        assertThat(value).isNotBlank();
        return UUID.fromString(value);
    }

}
