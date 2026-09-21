package org.github.tess1o.geopulse.export.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/exports}.
 *
 * <p>Every class here is {@code @Authenticated} rather than {@code @RolesAllowed}, so there is no
 * wrong-role 403 to assert — only 401. The class-wide {@code @Consumes(APPLICATION_JSON)} means even
 * bodyless POSTs need a JSON content type.
 *
 * <p>The scheduler is disabled in tests, so a freshly created export job never advances past
 * {@code processing}. {@code EXPORT_NOT_READY} is therefore the honest contract for the download
 * endpoint, not a 200.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ExportContractTest {

    private static final String EXPORTS = "/api/v1/exports";
    private static final String PASSWORD = "password123";
    private static final Instant RANGE_START = Instant.parse("2024-08-01T00:00:00Z");
    private static final Instant RANGE_END = Instant.parse("2024-08-31T00:00:00Z");

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    @Transactional
    void setUp() {
        ownerToken = token(register("export-owner"));
        otherToken = token(register("export-other"));
    }

    @Test
    void csvTemplateAndJobLifecycleAreServed() {
        Response template = authenticated(ownerToken).when().get(EXPORTS + "/csv-template");
        assertThat(template.statusCode()).isEqualTo(200);
        assertThat(template.contentType()).startsWith("text/csv");
        assertThat(template.body().asString()).isNotBlank();

        Response created = authenticated(ownerToken).body(exportBody(List.of("gps"))).when().post(EXPORTS);
        assertThat(created.statusCode()).isEqualTo(200);
        UUID jobId = UUID.fromString(created.jsonPath().getString("exportJobId"));
        assertThat(created.jsonPath().getString("status")).isEqualTo("processing");

        Response polled = authenticated(ownerToken).when().get(EXPORTS + "/" + jobId);
        assertThat(polled.statusCode()).isEqualTo(200);
        assertThat(polled.jsonPath().getString("status")).isEqualTo("processing");

        Response listed = authenticated(ownerToken)
                .queryParam("page", 0).queryParam("size", 10)
                .when().get(EXPORTS);
        assertThat(listed.statusCode()).isEqualTo(200);

        // Not downloadable yet — the job never advances with the scheduler off.
        assertProblemEnvelope(authenticated(ownerToken).when().get(EXPORTS + "/" + jobId + "/content"),
                409, "EXPORT_NOT_READY");

        assertThat(authenticated(ownerToken).when().delete(EXPORTS + "/" + jobId).statusCode()).isEqualTo(204);
    }

    @Test
    void exactErrorCodesForInvalidRequests() {
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(exportBody(List.of()))
                        .when().post(EXPORTS),
                400, "INVALID_EXPORT_REQUEST");

        Map<String, Object> noRange = new HashMap<>();
        noRange.put("dataTypes", List.of("gps"));
        assertProblemEnvelope(authenticated(ownerToken).body(noRange).when().post(EXPORTS),
                400, "INVALID_DATE_RANGE");

        assertProblemEnvelope(authenticated(ownerToken)
                        .body(exportBody(List.of("gps"), Instant.now().plusSeconds(86_400), RANGE_END))
                        .when().post(EXPORTS),
                400, "INVALID_DATE_RANGE");

        assertProblemEnvelope(authenticated(ownerToken)
                        .body(exportBody(List.of("gps"), RANGE_END, RANGE_START))
                        .when().post(EXPORTS),
                400, "INVALID_DATE_RANGE");
    }

    @Test
    void unknownJobsAreNotFoundEverywhere() {
        UUID absent = UUID.randomUUID();
        assertProblemEnvelope(authenticated(ownerToken).when().get(EXPORTS + "/" + absent),
                404, "EXPORT_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().get(EXPORTS + "/" + absent + "/content"),
                404, "EXPORT_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().delete(EXPORTS + "/" + absent),
                404, "EXPORT_NOT_FOUND");
    }

    @Test
    void exportsRequireAuthentication() {
        assertProblemEnvelope(given().when().get(EXPORTS), 401, "AUTHENTICATION_REQUIRED");
        assertProblemEnvelope(given().contentType(ContentType.JSON).body(exportBody(List.of("gps")))
                .when().post(EXPORTS), 401, "AUTHENTICATION_REQUIRED");
    }

    /**
     * Runs as its own user so the accumulated in-process jobs cannot leak into the lifecycle test —
     * {@code ExportJobManager} tracks active jobs per user in memory, and that state outlives a test
     * method.
     */
    @Test
    void tooManyActiveJobsIsRateLimited() {
        List<UUID> created = new ArrayList<>();
        try {
            Response first = authenticated(otherToken).body(exportBody(List.of("gps"))).when().post(EXPORTS);
            assertThat(first.statusCode()).isEqualTo(200);

            // export.max-jobs-per-user defaults to 5; keep creating until the limit trips.
            boolean limited = false;
            for (int i = 0; i < 10 && !limited; i++) {
                Response response = authenticated(otherToken).body(exportBody(List.of("gps"))).when().post(EXPORTS);
                if (response.statusCode() == 429) {
                    assertProblemEnvelope(response, 429, "RATE_LIMIT_EXCEEDED");
                    limited = true;
                } else {
                    assertThat(response.statusCode()).isEqualTo(200);
                    created.add(UUID.fromString(response.jsonPath().getString("exportJobId")));
                }
            }
            assertThat(limited)
                    .as("an active-job limit should reject eventually")
                    .isTrue();
        } finally {
            for (UUID jobId : created) {
                authenticated(otherToken).when().delete(EXPORTS + "/" + jobId);
            }
            authenticated(otherToken).when().delete(EXPORTS + "/" + UUID.randomUUID());
        }
    }

    @Test
    void debugExportValidatesItsRequestAndStreamsAZip() {
        Map<String, Object> missingDates = new HashMap<>();
        missingDates.put("latitudeShift", 0.0);
        missingDates.put("longitudeShift", 0.0);
        assertProblemEnvelope(authenticated(ownerToken).body(missingDates).when().post(EXPORTS + "/debug"),
                400, "INVALID_DEBUG_EXPORT_REQUEST");

        Map<String, Object> missingShifts = new HashMap<>();
        missingShifts.put("startDate", RANGE_START.toString());
        missingShifts.put("endDate", RANGE_END.toString());
        assertProblemEnvelope(authenticated(ownerToken).body(missingShifts).when().post(EXPORTS + "/debug"),
                400, "INVALID_DEBUG_EXPORT_REQUEST");

        assertProblemEnvelope(authenticated(ownerToken)
                        .body(debugBody(RANGE_END, RANGE_START))
                        .when().post(EXPORTS + "/debug"),
                400, "INVALID_DEBUG_EXPORT_REQUEST");

        Response zip = authenticated(ownerToken).body(debugBody(RANGE_START, RANGE_END))
                .when().post(EXPORTS + "/debug");
        assertThat(zip.statusCode()).isEqualTo(200);
        assertThat(zip.contentType()).startsWith("application/zip");
    }

    private static Map<String, Object> exportBody(List<String> dataTypes) {
        return exportBody(dataTypes, RANGE_START, RANGE_END);
    }

    private static Map<String, Object> exportBody(List<String> dataTypes, Instant start, Instant end) {
        Map<String, Object> body = new HashMap<>();
        body.put("dataTypes", dataTypes);
        body.put("dateRange", Map.of("startDate", start.toString(), "endDate", end.toString()));
        body.put("format", "json");
        return body;
    }

    private static Map<String, Object> debugBody(Instant start, Instant end) {
        Map<String, Object> body = new HashMap<>();
        body.put("startDate", start.toString());
        body.put("endDate", end.toString());
        body.put("latitudeShift", 0.0);
        body.put("longitudeShift", 0.0);
        return body;
    }

    private UserEntity register(String prefix) {
        String email = TestIds.uniqueEmail(prefix);
        return userService.registerUser(email, PASSWORD, prefix + " User", "UTC");
    }

    private String token(UserEntity user) {
        return authenticationService.authenticate(user.getEmail(), PASSWORD).getAccessToken();
    }

    private static RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
}
