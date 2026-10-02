package org.github.tess1o.geopulse.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * CSRF is enforced for requests carrying a session cookie and skipped for header-authenticated API clients.
 *
 * <p>Every request here asks for an unknown import format, so a request that passes the CSRF check is
 * answered by the resource with {@code INVALID_IMPORT_FORMAT} and creates nothing.</p>
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class CsrfProtectionContractTest {

    private static final String IMPORTS = "/api/v1/imports";
    private static final String UPLOADS = "/api/v1/import-uploads";
    private static final String PASSWORD = "password123";
    private static final String CSRF_TOKEN = "AAAAAAAAAAAAAAAAAAAAAA";
    private static final String OTHER_CSRF_TOKEN = "BBBBBBBBBBBBBBBBBBBBBB";
    private static final String REACHED_RESOURCE = "INVALID_IMPORT_FORMAT";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject ApiTokenService apiTokenService;

    private String accessToken;
    private String apiToken;

    @BeforeEach
    @Transactional
    void setUp() {
        String email = TestIds.uniqueEmail("csrf-user");
        UserEntity user = userService.registerUser(email, PASSWORD, "CSRF User", "UTC");
        accessToken = authenticationService.authenticate(email, PASSWORD).getAccessToken();
        apiToken = apiTokenService.createToken(user.getId(), "CSRF test",
                Instant.now().plusSeconds(3600), "127.0.0.1").getToken();
    }

    @Test
    void cookieSessionMultipartWithoutTokenIsRejected() {
        assertProblemEnvelope(multipart(given().cookie("access_token", accessToken)).when().post(IMPORTS),
                400, "CSRF_TOKEN_INVALID");
    }

    @Test
    void cookieSessionWithMismatchedTokenIsRejected() {
        assertProblemEnvelope(multipart(given()
                        .cookie("access_token", accessToken)
                        .cookie("csrf-token", CSRF_TOKEN)
                        .header("X-CSRF-Token", OTHER_CSRF_TOKEN)).when().post(IMPORTS),
                400, "CSRF_TOKEN_INVALID");

        // A token header without the matching cookie is rejected for JSON requests too.
        assertProblemEnvelope(json(given()
                        .cookie("access_token", accessToken)
                        .header("X-CSRF-Token", CSRF_TOKEN)).when().post(UPLOADS),
                400, "CSRF_TOKEN_INVALID");
    }

    @Test
    void cookieSessionWithMatchingTokenReachesTheResource() {
        assertProblemEnvelope(multipart(given()
                        .cookie("access_token", accessToken)
                        .cookie("csrf-token", CSRF_TOKEN)
                        .header("X-CSRF-Token", CSRF_TOKEN)).when().post(IMPORTS),
                400, REACHED_RESOURCE);
    }

    @Test
    void sessionCookieIsCheckedEvenWhenAnApiKeyIsAlsoSent() {
        assertProblemEnvelope(multipart(given()
                        .cookie("access_token", accessToken)
                        .header("X-API-Key", apiToken)).when().post(IMPORTS),
                400, "CSRF_TOKEN_INVALID");
    }

    @Test
    void headerAuthenticatedClientsNeedNoCsrfToken() {
        assertProblemEnvelope(multipart(given().header("X-API-Key", apiToken)).when().post(IMPORTS),
                400, REACHED_RESOURCE);
        assertProblemEnvelope(multipart(given().header("Authorization", "Bearer " + apiToken)).when().post(IMPORTS),
                400, REACHED_RESOURCE);
        assertProblemEnvelope(multipart(given().header("Authorization", "Bearer " + accessToken)).when().post(IMPORTS),
                400, REACHED_RESOURCE);
    }

    @Test
    void csrfCookieIsStillIssuedOnSafeRequests() {
        Response response = given().cookie("access_token", accessToken).when().get("/api/v1/users/me");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.getCookie("csrf-token")).isNotBlank();
    }

    private static RequestSpecification multipart(RequestSpecification spec) {
        return spec
                .multiPart("file", "sample.geojson", "{}".getBytes(StandardCharsets.UTF_8), "application/geo+json")
                .multiPart("format", "not-a-format");
    }

    private static RequestSpecification json(RequestSpecification spec) {
        return spec.contentType(ContentType.JSON)
                .body(Map.of("fileName", "sample.geojson", "fileSize", 10, "importFormat", "not-a-format"));
    }
}
