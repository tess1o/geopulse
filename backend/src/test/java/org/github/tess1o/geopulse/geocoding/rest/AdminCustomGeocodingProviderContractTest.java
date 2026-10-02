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
import org.github.tess1o.geopulse.testsupport.AdminTestUsers;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/admin/geocoding/providers}.
 *
 * <p>Role handling is the interesting part here: the class carries no class-level
 * {@code @RolesAllowed} — roles are per method, and {@code GET} additionally admits
 * {@code DEMO_ADMIN_READ} while the writes are ADMIN-only.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class AdminCustomGeocodingProviderContractTest {

    private static final String PROVIDERS = "/api/v1/admin/geocoding/providers";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;

    private TestActor admin;
    private TestActor plainUser;

    @BeforeEach
    @Transactional
    void setUp() {
        admin = AdminTestUsers.createAdmin(userService, authenticationService, "custom-provider-admin");
        plainUser = AdminTestUsers.createRegularUser(userService, authenticationService, "custom-provider-user");
    }

    @Test
    void adminCanCreateListUpdateAndDeleteAProvider() {
        String name = uniqueName("cp");

        Response created = authenticated(admin).body(providerBody(name, "My Provider", "https://example.test/geo"))
                .when().post(PROVIDERS);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.jsonPath().getString("name")).isEqualTo(name);
        assertThat(created.jsonPath().getString("type")).isEqualTo("nominatim");
        assertThat(created.jsonPath().getBoolean("enabled")).isTrue();

        Response listed = authenticated(admin).when().get(PROVIDERS);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.jsonPath().getList("name", String.class)).contains(name);

        Response updated = authenticated(admin)
                .body(providerBody(name, "Renamed Provider", "https://example.test/geo2"))
                .when().put(PROVIDERS + "/" + name);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.jsonPath().getString("displayName")).isEqualTo("Renamed Provider");
        assertThat(updated.jsonPath().getString("url")).isEqualTo("https://example.test/geo2");

        Response deleted = authenticated(admin).when().delete(PROVIDERS + "/" + name);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(authenticated(admin).when().get(PROVIDERS).jsonPath().getList("name", String.class))
                .doesNotContain(name);
    }

    @Test
    void requestValidationRejectsBadNamesTypesAndUrls() {
        // name pattern: lowercase letters, digits and hyphens only
        assertProblemEnvelope(authenticated(admin)
                        .body(providerBody("Bad Name", "Bad", "https://example.test/geo"))
                        .when().post(PROVIDERS),
                400, "VALIDATION_FAILED");
        // type must be photon or nominatim
        assertProblemEnvelope(authenticated(admin)
                        .body(providerBody(uniqueName("bad-type"), "Bad Type", "https://example.test/geo", "openstreetmap"))
                        .when().post(PROVIDERS),
                400, "VALIDATION_FAILED");
        // displayName is mandatory
        Response missingDisplayName = authenticated(admin).body(Map.of(
                        "name", uniqueName("no-display"),
                        "type", "nominatim",
                        "url", "https://example.test/geo"))
                .when().post(PROVIDERS);
        assertProblemEnvelope(missingDisplayName, 400, "VALIDATION_FAILED");
    }

    @Test
    void builtInNamesDuplicatesAndBadUrlsAreRejectedWithTheDomainCode() {
        // Built-in collision — these names are reserved.
        assertProblemEnvelope(authenticated(admin)
                        .body(providerBody("nominatim", "Clash", "https://example.test/geo"))
                        .when().post(PROVIDERS),
                400, "INVALID_CUSTOM_GEOCODING_PROVIDER");

        String name = uniqueName("dup");
        assertThat(authenticated(admin).body(providerBody(name, "First", "https://example.test/a"))
                .when().post(PROVIDERS).statusCode()).isEqualTo(201);
        assertProblemEnvelope(authenticated(admin)
                        .body(providerBody(name, "Second", "https://example.test/b"))
                        .when().post(PROVIDERS),
                400, "INVALID_CUSTOM_GEOCODING_PROVIDER");

        assertProblemEnvelope(authenticated(admin)
                        .body(providerBody(uniqueName("badurl"), "Bad Url", "not-a-url"))
                        .when().post(PROVIDERS),
                400, "INVALID_CUSTOM_GEOCODING_PROVIDER");
        assertProblemEnvelope(authenticated(admin)
                        .body(providerBody(uniqueName("badscheme"), "Bad Scheme", "ftp://example.test/geo"))
                        .when().post(PROVIDERS),
                400, "INVALID_CUSTOM_GEOCODING_PROVIDER");
    }

    @Test
    void unknownProviderIsNotFoundOnUpdateAndDelete() {
        assertProblemEnvelope(authenticated(admin)
                        .body(providerBody(uniqueName("absent"), "Absent", "https://example.test/geo"))
                        .when().put(PROVIDERS + "/does-not-exist"),
                404, "NOT_FOUND");
        assertProblemEnvelope(authenticated(admin).when().delete(PROVIDERS + "/does-not-exist"),
                404, "NOT_FOUND");
    }

    @Test
    void plainUsersAreDeniedOnEveryMethod() {
        String name = uniqueName("role");
        assertProblemEnvelope(authenticated(plainUser).when().get(PROVIDERS), 403, "ACCESS_DENIED");
        assertProblemEnvelope(authenticated(plainUser)
                        .body(providerBody(name, "Nope", "https://example.test/geo"))
                        .when().post(PROVIDERS),
                403, "ACCESS_DENIED");
        assertProblemEnvelope(authenticated(plainUser)
                        .body(providerBody(name, "Nope", "https://example.test/geo"))
                        .when().put(PROVIDERS + "/" + name),
                403, "ACCESS_DENIED");
        assertProblemEnvelope(authenticated(plainUser).when().delete(PROVIDERS + "/" + name),
                403, "ACCESS_DENIED");
    }

    private static String uniqueName(String prefix) {
        // name must match ^[a-z0-9][a-z0-9-]*$ so TestIds' default charset cannot be used verbatim.
        return TestIds.uniqueValue(prefix).toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    private static Map<String, Object> providerBody(String name, String displayName, String url) {
        return providerBody(name, displayName, url, "nominatim");
    }

    private static Map<String, Object> providerBody(String name, String displayName, String url, String type) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("displayName", displayName);
        body.put("type", type);
        body.put("url", url);
        body.put("enabled", true);
        return body;
    }

    private static RequestSpecification authenticated(TestActor actor) {
        return authenticated(actor.accessToken());
    }

    private static RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
}
