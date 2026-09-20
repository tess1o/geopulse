package org.github.tess1o.geopulse.sharing.rest;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SharedLinkResourceContractTest {

    private static final String LINKS = "/api/v1/share-links";
    private static final String PUBLIC_LINKS = "/api/v1/public/share-links";
    private static final String PASSWORD = "password123";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;

    private TestUser owner;
    private TestUser other;

    @BeforeEach
    @Transactional
    void setUp() {
        owner = register("share-owner", "Share Owner");
        other = register("share-other", "Share Other");
    }

    @Test
    void managementRoutesCreateListUpdateAndDelete() {
        UUID linkId = createLiveLink(owner, "Managed link", "secret1");

        Response links = authenticated(owner).when().get(LINKS);
        assertThat(links.statusCode()).isEqualTo(200);
        assertThat(links.jsonPath().getList("links.id", String.class)).contains(linkId.toString());

        Response updated = authenticated(owner)
                .body(Map.of(
                        "name", "Updated link",
                        "password", "secret2",
                        "show_history", false,
                        "history_hours", 0,
                        "share_type", "LIVE_LOCATION",
                        "show_current_location", true))
                .when().put(LINKS + "/" + linkId);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.jsonPath().getString("name")).isEqualTo("Updated link");

        Response deleted = authenticated(owner).when().delete(LINKS + "/" + linkId);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(authenticated(owner).when().get(LINKS).jsonPath().getList("links.id", String.class))
                .doesNotContain(linkId.toString());
    }

    @Test
    void publicRoutesExposeOnlyAuthorizedSharedData() {
        Instant now = Instant.now();
        ingestPoint(owner, now.minusSeconds(30));
        UUID liveLinkId = createLiveLink(owner, "Public live link", "secret1");
        UUID timelineLinkId = createTimelineLink(owner, "Public timeline link", "secret2", true);

        Response info = given().when().get(PUBLIC_LINKS + "/" + liveLinkId);
        assertThat(info.statusCode()).isEqualTo(200);
        assertThat(info.jsonPath().getString("shared_by")).isEqualTo("Share Owner");

        String liveToken = accessToken(liveLinkId, "secret1");
        Response location = publicAuthorized(liveToken).when().get(PUBLIC_LINKS + "/" + liveLinkId + "/location");
        assertThat(location.statusCode()).isEqualTo(200);
        assertThat(location.jsonPath().getDouble("current.latitude")).isEqualTo(50.45);

        String timelineToken = accessToken(timelineLinkId, "secret2");
        Response timeline = publicAuthorized(timelineToken)
                .when().get(PUBLIC_LINKS + "/" + timelineLinkId + "/timeline");
        assertThat(timeline.statusCode()).isEqualTo(200);
        assertThat(timeline.jsonPath().getString("userId")).isEqualTo(owner.id().toString());

        Response notes = publicAuthorized(timelineToken)
                .when().get(PUBLIC_LINKS + "/" + timelineLinkId + "/notes");
        assertThat(notes.statusCode()).isEqualTo(200);
        assertThat(notes.jsonPath().getInt("totalCount")).isZero();

        Response path = publicAuthorized(timelineToken)
                .when().get(PUBLIC_LINKS + "/" + timelineLinkId + "/path");
        assertThat(path.statusCode()).isEqualTo(200);
        assertThat(path.jsonPath().getInt("pointCount")).isEqualTo(1);

        Response current = publicAuthorized(timelineToken)
                .when().get(PUBLIC_LINKS + "/" + timelineLinkId + "/current-location");
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(current.jsonPath().getDouble("longitude")).isEqualTo(30.52);
    }

    @Test
    void publicFailuresUseExactCodes() {
        UUID protectedLink = createLiveLink(owner, "Protected link", "secret1");
        assertProblemEnvelope(given().when().get(PUBLIC_LINKS + "/" + protectedLink + "/location"),
                401, "SHARED_LINK_TOKEN_REQUIRED");
        assertProblemEnvelope(given()
                        .contentType(ContentType.JSON)
                        .body(Map.of("password", "wrong-password"))
                        .when().post(PUBLIC_LINKS + "/" + protectedLink + "/access-tokens"),
                403, "SHARED_LINK_PASSWORD_INVALID");
        assertProblemEnvelope(given().when().get(PUBLIC_LINKS + "/" + UUID.randomUUID()),
                404, "SHARED_LINK_NOT_FOUND");

        UUID secondLink = createLiveLink(owner, "Second protected link", "secret2");
        String firstToken = accessToken(protectedLink, "secret1");
        assertProblemEnvelope(publicAuthorized(firstToken).when().get(PUBLIC_LINKS + "/" + secondLink + "/location"),
                403, "SHARED_LINK_ACCESS_DENIED");

        UUID hiddenCurrent = createTimelineLink(owner, "Hidden current link", "secret3", false);
        String hiddenToken = accessToken(hiddenCurrent, "secret3");
        assertProblemEnvelope(publicAuthorized(hiddenToken)
                        .when().get(PUBLIC_LINKS + "/" + hiddenCurrent + "/current-location"),
                404, "SHARED_LOCATION_NOT_FOUND");

        UUID timelineLink = createTimelineLink(owner, "Invalid range link", "secret4", true);
        String timelineToken = accessToken(timelineLink, "secret4");
        assertProblemEnvelope(publicAuthorized(timelineToken)
                        .queryParam("from", Instant.now().plusSeconds(60).toString())
                        .queryParam("to", Instant.now().minusSeconds(60).toString())
                        .when().get(PUBLIC_LINKS + "/" + timelineLink + "/timeline"),
                400, "INVALID_SHARED_TIME_RANGE");
    }

    @Test
    void managementFailuresUseExactCodes() {
        assertProblemEnvelope(given().when().get(LINKS), 401, "AUTHENTICATION_REQUIRED");
        assertProblemEnvelope(authenticated(owner)
                        .body(Map.of("name", "Broken timeline", "share_type", "TIMELINE"))
                        .when().post(LINKS),
                400, "INVALID_SHARE_LINK");

        UUID otherLink = createLiveLink(other, "Other user link", null);
        assertProblemEnvelope(authenticated(owner)
                        .body(Map.of("name", "Cannot update"))
                        .when().put(LINKS + "/" + otherLink),
                404, "SHARED_LINK_NOT_FOUND");

        Response links = authenticated(owner).when().get(LINKS);
        int maxLinks = links.jsonPath().getInt("max_links");
        for (int i = 0; i < maxLinks; i++) {
            createLiveLink(owner, "Limit link " + i, null);
        }
        assertProblemEnvelope(authenticated(owner)
                        .body(liveLinkBody("Over limit", null))
                        .when().post(LINKS),
                429, "SHARED_LINK_LIMIT_EXCEEDED");
    }

    private TestUser register(String prefix, String name) {
        String email = TestIds.uniqueEmail(prefix);
        UserEntity user = userService.registerUser(email, PASSWORD, name, "UTC");
        String token = authenticationService.authenticate(email, PASSWORD).getAccessToken();
        return new TestUser(user.getId(), token);
    }

    private UUID createLiveLink(TestUser user, String name, String password) {
        Response response = authenticated(user).body(liveLinkBody(name, password)).when().post(LINKS);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.jsonPath().getString("name")).isEqualTo(name);
        return UUID.fromString(response.jsonPath().getString("id"));
    }

    private static Map<String, Object> liveLinkBody(String name, String password) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("expires_at", Instant.now().plusSeconds(3600).toString());
        body.put("show_history", true);
        body.put("history_hours", 24);
        body.put("share_type", "LIVE_LOCATION");
        body.put("show_current_location", true);
        if (password != null) {
            body.put("password", password);
        }
        return body;
    }

    private UUID createTimelineLink(TestUser user, String name, String password, boolean showCurrentLocation) {
        Instant now = Instant.now();
        Response response = authenticated(user)
                .body(Map.of(
                        "name", name,
                        "expires_at", now.plusSeconds(7200).toString(),
                        "password", password,
                        "show_history", true,
                        "history_hours", 24,
                        "share_type", "TIMELINE",
                        "start_date", now.minusSeconds(3600).toString(),
                        "end_date", now.plusSeconds(3600).toString(),
                        "show_current_location", showCurrentLocation,
                        "show_notes", false))
                .when().post(LINKS);
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(response.jsonPath().getString("id"));
    }

    private String accessToken(UUID linkId, String password) {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("password", password))
                .when().post(PUBLIC_LINKS + "/" + linkId + "/access-tokens");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getLong("expires_in")).isPositive();
        return response.jsonPath().getString("access_token");
    }

    private void ingestPoint(TestUser user, Instant timestamp) {
        authenticated(user)
                .header("X-Device-Id", "sharing-contract")
                .body(Map.of("points", List.of(Map.of(
                        "timestamp", timestamp.toString(),
                        "coordinates", Map.of("lat", 50.45, "lng", 30.52),
                        "accuracy", 5.0,
                        "battery", 90.0,
                        "velocity", 1.0,
                        "altitude", 100.0))))
                .when().post("/api/v1/gps/points")
                .then().statusCode(200);
    }

    private static RequestSpecification authenticated(TestUser user) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.token());
    }

    private static RequestSpecification publicAuthorized(String token) {
        return given().header("Authorization", "Bearer " + token);
    }

    private record TestUser(UUID id, String token) {
    }
}
