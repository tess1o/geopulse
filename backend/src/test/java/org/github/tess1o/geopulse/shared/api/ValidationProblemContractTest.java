package org.github.tess1o.geopulse.shared.api;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shape of {@code violations} in a real problem response: every entry must carry a stable code and a
 * localizable message descriptor, so the frontend can translate rather than echo the server's English text.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class ValidationProblemContractTest {

    @Test
    void notBlankViolationsCarryALocalizableMessage() {
        Response response = register(Map.of());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("code")).isEqualTo("VALIDATION_FAILED");

        List<Map<String, Object>> violations = response.jsonPath().getList("violations");
        assertThat(violations).hasSize(2);
        assertThat(violations).allSatisfy(violation -> {
            assertThat(violation.get("code")).isEqualTo("NOT_BLANK");
            assertThat(violation.get("in")).isEqualTo("body");
            assertThat(violation.get("field")).isIn("email", "password");
            assertThat(detail(violation).get("key")).isEqualTo("validation.notBlank");
            assertThat(detail(violation).get("fallback")).isIn("Email is required", "Password is required");
        });
    }

    @Test
    void sizeViolationCarriesInterpolatableParameters() {
        Response response = register(Map.of("email", "user@example.com", "password", "ab"));

        assertThat(response.statusCode()).isEqualTo(400);
        List<Map<String, Object>> violations = response.jsonPath().getList("violations");
        assertThat(violations).hasSize(1);

        Map<String, Object> violation = violations.getFirst();
        assertThat(violation.get("code")).isEqualTo("SIZE");
        assertThat(violation.get("in")).isEqualTo("body");
        assertThat(violation.get("field")).isEqualTo("password");
        assertThat(violation.get("parameters")).isEqualTo(Map.of("min", 3, "max", 128));

        Map<String, Object> detail = detail(violation);
        assertThat(detail.get("key")).isEqualTo("validation.size");
        assertThat(detail.get("parameters")).isEqualTo(Map.of("min", 3, "max", 128));
        assertThat(detail.get("fallback")).isEqualTo("Password must be between 3 and 128 characters");
    }

    @Test
    void instanceIsAReadablePathNotAPercentEncodedSegment() {
        Response response = register(Map.of());

        String instance = response.jsonPath().getString("instance");
        assertThat(instance).startsWith("/");
        assertThat(instance).doesNotContain("%2F");
    }

    @Test
    void inIsNeverTheLibraryUnknownSentinel() {
        List<Map<String, Object>> violations = register(Map.of()).jsonPath().getList("violations");

        assertThat(violations).isNotEmpty();
        assertThat(violations).allSatisfy(violation ->
                assertThat(violation.get("in")).isNotEqualTo("?"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detail(Map<String, Object> violation) {
        return (Map<String, Object>) violation.get("detail");
    }

    private static Response register(Map<String, Object> body) {
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/v1/registrations");
    }
}
