package org.github.tess1o.geopulse.coverage.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/coverage}.
 *
 * <p>Ordering matters and is asserted as such: {@code COVERAGE_DISABLED} is checked <em>before</em>
 * bounding-box and grid validation, so the 403 hides the 400s until coverage is switched on. The
 * user flag is flipped directly through the repository — going via
 * {@code PUT /coverage/settings {"enabled": true}} would kick off an asynchronous full
 * recalculculation that outlives the request.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class CoverageContractTest {

    private static final String COVERAGE = "/api/v1/coverage";
    private static final String PASSWORD = "password123";
    private static final String VALID_BBOX = "-74.1,40.6,-73.9,40.8";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject UserRepository userRepository;
    @Inject EntityManager entityManager;

    private UUID ownerId;
    private String ownerToken;

    @BeforeEach
    @Transactional
    void setUp() {
        String email = TestIds.uniqueEmail("coverage-owner");
        UserEntity owner = userService.registerUser(email, PASSWORD, "Coverage Owner", "UTC");
        ownerId = owner.getId();
        ownerToken = authenticationService.authenticate(email, PASSWORD).getAccessToken();
    }

    @Test
    void statusIsReadableWhileDisabledAndSettingsTogglesIt() {
        Response status = authenticated(ownerToken).when().get(COVERAGE + "/status");
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.jsonPath().getBoolean("userEnabled")).isFalse();

        // enabled=false performs no background processing, unlike enabling.
        Response toggled = authenticated(ownerToken)
                .body(Map.of("enabled", false))
                .when().put(COVERAGE + "/settings");
        assertThat(toggled.statusCode()).isEqualTo(200);
        assertThat(toggled.jsonPath().getBoolean("userEnabled")).isFalse();
    }

    @Test
    void settingsRequiresAnExplicitEnabledFlag() {
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of())
                        .when().put(COVERAGE + "/settings"),
                400, "COVERAGE_ENABLED_REQUIRED");
        // A value Jackson cannot coerce to Boolean fails during deserialization, i.e. before bean
        // validation, so it carries the generic framework code rather than VALIDATION_FAILED.
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("enabled", "not-a-boolean"))
                        .when().put(COVERAGE + "/settings"),
                400, "BAD_REQUEST");
    }

    @Test
    void everythingIsForbiddenWhileCoverageIsDisabled() {
        // Note: the OpenAPI annotation on /recalculations documents "Coverage is not enabled" as 400,
        // but COVERAGE_DISABLED is a 403.
        assertProblemEnvelope(authenticated(ownerToken).when().post(COVERAGE + "/recalculations"),
                403, "COVERAGE_DISABLED");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("bbox", VALID_BBOX)
                        .when().get(COVERAGE + "/cells"),
                403, "COVERAGE_DISABLED");
        assertProblemEnvelope(authenticated(ownerToken).when().get(COVERAGE + "/summary"),
                403, "COVERAGE_DISABLED");
    }

    @Test
    void enabledCoverageServesCellsAndSummary() {
        enableCoverage();

        Response cells = authenticated(ownerToken).queryParam("bbox", VALID_BBOX).when().get(COVERAGE + "/cells");
        assertThat(cells.statusCode()).isEqualTo(200);
        assertThat(cells.jsonPath().getInt("gridMeters")).isEqualTo(50);
        assertThat(cells.jsonPath().getBoolean("truncated")).isFalse();
        assertThat(cells.jsonPath().getList("cells")).isEmpty();

        Response summary = authenticated(ownerToken).when().get(COVERAGE + "/summary");
        assertThat(summary.statusCode()).isEqualTo(200);

        // parseBbox normalises swapped corners with min/max, so an inverted box is legal.
        Response swapped = authenticated(ownerToken)
                .queryParam("bbox", "-73.9,40.8,-74.1,40.6")
                .when().get(COVERAGE + "/cells");
        assertThat(swapped.statusCode())
                .as("swapped corners are normalised, not rejected")
                .isEqualTo(200);
    }

    /** Cells are Web Mercator cell indexes; (-74.0, 40.7) is x -8237.6 km, y 4968.2 km. */
    @Test
    void cellsAreIndexTriplesInIndexOrderAndReportTruncation() {
        enableCoverage();
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "INSERT INTO coverage_cells (user_id, grid_m, cell_x, cell_y, first_seen, last_seen, seen_count) " +
                                "VALUES (:userId, 1000, -8237, 4968, NOW(), NOW(), 7), " +
                                "       (:userId, 1000, -8238, 4969, NOW(), NOW(), 1), " +
                                "       (:userId, 1000, -8238, 4968, NOW(), NOW(), 3), " +
                                "       (:userId, 1000, 0, 0, NOW(), NOW(), 9), " +
                                "       (:userId, 50, -164753, 99363, NOW(), NOW(), 2)")
                .setParameter("userId", ownerId)
                .executeUpdate());

        Response all = authenticated(ownerToken)
                .queryParam("bbox", VALID_BBOX).queryParam("grid", 1000)
                .when().get(COVERAGE + "/cells");
        assertThat(all.statusCode()).isEqualTo(200);
        assertThat(all.jsonPath().getInt("gridMeters")).isEqualTo(1000);
        assertThat(all.jsonPath().getBoolean("truncated")).isFalse();
        assertThat(all.jsonPath().getList("cells")).containsExactly(
                List.of(-8238, 4968, 3), List.of(-8238, 4969, 1), List.of(-8237, 4968, 7));

        Response limited = authenticated(ownerToken)
                .queryParam("bbox", VALID_BBOX).queryParam("grid", 1000).queryParam("limit", 2)
                .when().get(COVERAGE + "/cells");
        assertThat(limited.jsonPath().getBoolean("truncated")).isTrue();
        assertThat(limited.jsonPath().getList("cells")).containsExactly(
                List.of(-8238, 4968, 3), List.of(-8238, 4969, 1));

        Response exact = authenticated(ownerToken)
                .queryParam("bbox", VALID_BBOX).queryParam("grid", 1000).queryParam("limit", 3)
                .when().get(COVERAGE + "/cells");
        assertThat(exact.jsonPath().getBoolean("truncated")).isFalse();
        assertThat(exact.jsonPath().getList("cells")).hasSize(3);
    }

    @Test
    void boundingBoxAndGridValidationUseExactCodes() {
        enableCoverage();

        assertProblemEnvelope(authenticated(ownerToken).when().get(COVERAGE + "/cells"),
                400, "INVALID_BOUNDING_BOX");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("bbox", "-74.1,40.6,-73.9")
                        .when().get(COVERAGE + "/cells"),
                400, "INVALID_BOUNDING_BOX");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("bbox", "-74.1,40.6,-73.9,nope")
                        .when().get(COVERAGE + "/cells"),
                400, "INVALID_BOUNDING_BOX");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("bbox", "-181,40.6,-73.9,40.8")
                        .when().get(COVERAGE + "/cells"),
                400, "INVALID_BOUNDING_BOX");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("bbox", VALID_BBOX).queryParam("grid", 999)
                        .when().get(COVERAGE + "/cells"),
                400, "UNSUPPORTED_COVERAGE_GRID");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("grid", 999)
                        .when().get(COVERAGE + "/summary"),
                400, "UNSUPPORTED_COVERAGE_GRID");
    }

    /**
     * {@code limit} carries {@code @Min(1)}, so bean validation rejects {@code 0} before the method
     * body runs. The resource's own {@code INVALID_LIMIT} branch is therefore unreachable over HTTP —
     * this pins the code a client actually receives.
     */
    @Test
    void nonPositiveLimitIsAValidationFailureNotTheDomainCode() {
        enableCoverage();

        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("bbox", VALID_BBOX)
                        .queryParam("limit", 0)
                        .when().get(COVERAGE + "/cells"),
                400, "VALIDATION_FAILED");
    }

    /** Flipped directly to avoid the asynchronous full recalculation the settings endpoint triggers. */
    private void enableCoverage() {
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = userRepository.findById(ownerId);
            user.setCoverageEnabled(true);
        });
    }

    private static RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
}
