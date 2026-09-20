package org.github.tess1o.geopulse.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class AuthenticationErrorContractTest {

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;

    String email;
    String accessToken;

    @BeforeEach
    @Transactional
    void setUp() {
        email = TestIds.uniqueEmail("authentication-error-contract");
        UserEntity user = userService.registerUser(email, "correct-password", "Error Contract User", "UTC");
        accessToken = authenticationService.createAccessToken(user);
    }

    @Test
    void missingUserAndWrongPasswordHaveTheSameStableProblem() {
        Response missingUser = login(TestIds.uniqueEmail("missing-user"), "wrong-password");
        Response wrongPassword = login(email, "wrong-password");

        assertProblemEnvelope(missingUser, 401, "INVALID_CREDENTIALS");
        assertProblemEnvelope(wrongPassword, 401, "INVALID_CREDENTIALS");
        assertThat(stableBody(missingUser)).isEqualTo(stableBody(wrongPassword));
        assertThat(missingUser.path("detail").toString()).isEqualTo("Invalid email or password");
    }

    @Test
    void frameworkProblemsUseTheSameEnvelope() {
        assertProblemEnvelope(authenticated().when().get("/api/v1/route-that-does-not-exist"), 404, "NOT_FOUND");
        assertProblemEnvelope(authenticated().when().get("/api/v1/auth/sessions"), 405, "METHOD_NOT_ALLOWED");
        assertProblemEnvelope(given()
                .contentType(ContentType.JSON)
                .body(Map.of())
                .when().post("/api/v1/registrations"), 400, "VALIDATION_FAILED");
        assertProblemEnvelope(given().when().get("/api/v1/friends"), 401, "AUTHENTICATION_REQUIRED");
    }

    private io.restassured.specification.RequestSpecification authenticated() {
        return given().header("Authorization", "Bearer " + accessToken);
    }

    private Response login(String loginEmail, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", loginEmail, "password", password))
                .when().post("/api/v1/auth/sessions");
    }

    private Map<String, Object> stableBody(Response response) {
        Map<String, Object> body = new LinkedHashMap<>(response.jsonPath().getMap("$"));
        body.remove("requestId");
        body.remove("errorId");
        return body;
    }
}
