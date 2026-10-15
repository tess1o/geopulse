package org.github.tess1o.geopulse.gps.integrations.homeassistant;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.dto.CreateApiTokenResponse;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
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
class HomeAssistantApiTokenIngestTest {

    private static final String INGEST = "/api/v1/gps/ingest/home-assistant";

    @Inject
    UserService userService;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    AuthenticationService authenticationService;

    private String sessionToken;
    private UUID readTokenId;
    private String writeToken;

    private UUID userId;
    private String fullToken;
    private String ingestToken;
    private UUID ingestTokenId;
    private String readToken;

    @BeforeEach
    @Transactional
    void setUp() {
        String email = TestIds.uniqueEmail("ha-api-token");
        UserEntity user = userService.registerUser(email, "password123", "HA API Token User", "UTC");
        userId = user.getId();
        sessionToken = authenticationService.authenticate(email, "password123").getAccessToken();
        Instant expires = Instant.now().plusSeconds(3600);
        fullToken = apiTokenService.createToken(userId, "admin script", expires, "127.0.0.1").getToken();
        CreateApiTokenResponse ingest = apiTokenService.createToken(userId, "Home Assistant", expires,
                List.of("read", "gps:ingest"), "127.0.0.1");
        ingestToken = ingest.getToken();
        ingestTokenId = ingest.getApiToken().getId();
        CreateApiTokenResponse read = apiTokenService.createToken(userId, "dashboard", expires, List.of("read"), "127.0.0.1");
        readToken = read.getToken();
        readTokenId = read.getApiToken().getId();
        writeToken = apiTokenService.createToken(userId, "script", expires, List.of("read", "write"), "127.0.0.1").getToken();
    }

    @Test
    void addingAnIntegrationSourceCreatesItsTokenAndTheSourceRightAway() {
        Map<String, Object> created = given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("type", "HOME_ASSISTANT", "connectionType", "HTTP",
                        "newApiToken", Map.of("name", "Living room HA")))
                .when().post("/api/v1/gps/sources/")
                .then().statusCode(201)
                .extract().jsonPath().getMap("$");
        String secret = (String) created.get("apiTokenSecret");
        assertThat(secret).startsWith("gp_sa_");
        assertThat(created.get("apiTokenName")).isEqualTo("Living room HA");
        assertThat(created.get("token")).isNull();
        assertThat(sources()).hasSize(1);

        given().header("Authorization", "Bearer " + secret)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T12:00:00Z", 50.45))
                .when().post(INGEST)
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + secret)
                .when().get("/api/v1/users/me/status")
                .then().statusCode(200);

        assertThat(sources()).as("points go to the source created with the token").hasSize(1);
        assertThat(gpsPointRepository.findByUserId(userId)).hasSize(1);
    }

    @Test
    void anExistingTokenCanBeConnectedOnceAndMustBeAbleToSendLocations() {
        given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("type", "HOME_ASSISTANT", "connectionType", "HTTP", "apiTokenId", ingestTokenId.toString()))
                .when().post("/api/v1/gps/sources/")
                .then().statusCode(201);

        assertProblemEnvelope(given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("type", "HOME_ASSISTANT", "connectionType", "HTTP", "apiTokenId", ingestTokenId.toString()))
                .when().post("/api/v1/gps/sources/"), 400, "INVALID_GPS_SOURCE");

        assertProblemEnvelope(given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("type", "HOME_ASSISTANT", "connectionType", "HTTP", "apiTokenId", readTokenId.toString()))
                .when().post("/api/v1/gps/sources/"), 400, "INVALID_GPS_SOURCE");

        given().header("Authorization", "Bearer " + sessionToken)
                .queryParam("scope", "gps:ingest")
                .when().get("/api/v1/api-tokens")
                .then().statusCode(200)
                .body("name", org.hamcrest.Matchers.contains("Home Assistant"));
    }

    @Test
    void limitedTokensCannotCreateNewTokensThroughSources() {
        assertProblemEnvelope(given().header("X-API-Key", writeToken)
                .contentType(ContentType.JSON)
                .body(Map.of("type", "HOME_ASSISTANT", "connectionType", "HTTP",
                        "newApiToken", Map.of("name", "escalated")))
                .when().post("/api/v1/gps/sources/"), 403, "API_TOKEN_SCOPE_REQUIRED");
    }

    @Test
    void deletingTheSourceCanRevokeItsToken() {
        String sourceId = connect(ingestTokenId);

        given().header("Authorization", "Bearer " + sessionToken)
                .queryParam("revokeApiToken", true)
                .when().delete("/api/v1/gps/sources/" + sourceId)
                .then().statusCode(204);

        assertThat(sources()).isEmpty();
        given().header("Authorization", "Bearer " + ingestToken)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T13:01:00Z", 50.46))
                .when().post(INGEST)
                .then().statusCode(401);
    }

    @Test
    void deletingTheSourceWithoutRevokingStopsSendingButKeepsTheToken() {
        String sourceId = connect(ingestTokenId);

        given().header("Authorization", "Bearer " + sessionToken)
                .when().delete("/api/v1/gps/sources/" + sourceId)
                .then().statusCode(204);

        assertProblemEnvelope(given().header("Authorization", "Bearer " + ingestToken)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T14:01:00Z", 50.46))
                .when().post(INGEST), 403, "GPS_SOURCE_NOT_CONNECTED");
        assertThat(sources()).as("no source is created implicitly").isEmpty();

        given().header("Authorization", "Bearer " + ingestToken)
                .when().get("/api/v1/users/me/status")
                .then().statusCode(200);
    }

    @Test
    void anUnconnectedTokenIsToldToConnectItFirst() {
        assertProblemEnvelope(given().header("Authorization", "Bearer " + ingestToken)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T07:00:00Z", 50.45))
                .when().post(INGEST), 403, "GPS_SOURCE_NOT_CONNECTED");

        assertThat(sources()).isEmpty();
        assertThat(gpsPointRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void pointsSentWithAnApiTokenGoToItsConnectedSource() {
        connect(ingestTokenId);
        given().header("Authorization", "Bearer " + ingestToken)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T08:00:00Z", 50.45))
                .when().post(INGEST)
                .then().statusCode(200);

        given().header("X-API-Key", ingestToken)
                .contentType(ContentType.JSON)
                .body(List.of(point("2026-05-29T08:01:00Z", 50.46), point("2026-05-29T08:02:00Z", 50.47)))
                .when().post(INGEST + "/batch")
                .then().statusCode(200);

        List<Map<String, Object>> sources = sources();
        assertThat(sources).hasSize(1);
        Map<String, Object> source = sources.getFirst();
        assertThat(source.get("type")).isEqualTo("HOME_ASSISTANT");
        assertThat(source.get("apiTokenId")).isEqualTo(ingestTokenId.toString());
        assertThat(source.get("apiTokenName")).isEqualTo("Home Assistant");
        assertThat(source.get("token")).isNull();
        assertThat(source.get("ingestStatus")).isNotNull();
        assertThat(gpsPointRepository.findByUserId(userId)).hasSize(3);
    }

    @Test
    void tokenWithoutGpsIngestScopeIsRejected() {
        assertProblemEnvelope(given().header("Authorization", "Bearer " + readToken)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T09:00:00Z", 50.45))
                .when().post(INGEST), 403, "API_TOKEN_SCOPE_REQUIRED");

        assertThat(sources()).isEmpty();
        assertThat(gpsPointRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void disabledLinkedSourceRejectsPoints() {
        String sourceId = connect(ingestTokenId);

        given().header("X-API-Key", fullToken)
                .contentType(ContentType.JSON)
                .body(Map.of("status", false))
                .when().patch("/api/v1/gps/sources/" + sourceId + "/status")
                .then().statusCode(204);

        assertProblemEnvelope(given().header("Authorization", "Bearer " + ingestToken)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T10:01:00Z", 50.46))
                .when().post(INGEST), 403, "GPS_SOURCE_DISABLED");
    }

    @Test
    void theConnectedSourceIsVisibleToItsTokenWithoutCredentials() {
        CreateApiTokenResponse sender = apiTokenService.createToken(userId, "sender", Instant.now().plusSeconds(3600),
                List.of("gps:ingest"), "127.0.0.1");
        String sendOnly = sender.getToken();
        assertProblemEnvelope(given().header("Authorization", "Bearer " + sendOnly)
                .when().get("/api/v1/gps/sources/current"), 404, "GPS_SOURCE_NOT_FOUND");
        connect(sender.getApiToken().getId());

        given().header("Authorization", "Bearer " + sendOnly)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T15:00:00Z", 50.45))
                .when().post(INGEST)
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + sendOnly)
                .when().get("/api/v1/gps/sources/current")
                .then().statusCode(200)
                .body("type", org.hamcrest.Matchers.equalTo("HOME_ASSISTANT"))
                .body("active", org.hamcrest.Matchers.equalTo(true))
                .body("ingestStatus.lastReceivedAt", org.hamcrest.Matchers.notNullValue())
                .body("token", org.hamcrest.Matchers.nullValue());

        assertProblemEnvelope(given().header("Authorization", "Bearer " + sessionToken)
                .when().get("/api/v1/gps/sources/current"), 404, "GPS_SOURCE_NOT_FOUND");
    }

    @Test
    void limitedTokensDoNotSeeOtherSourcesCredentials() {
        given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("type", "OVERLAND", "connectionType", "HTTP", "token", "overland-secret-" + userId))
                .when().post("/api/v1/gps/sources/")
                .then().statusCode(201);

        given().header("X-API-Key", readToken)
                .when().get("/api/v1/gps/sources")
                .then().statusCode(200)
                .body("[0].type", org.hamcrest.Matchers.equalTo("OVERLAND"))
                .body("[0].token", org.hamcrest.Matchers.nullValue());

        given().header("X-API-Key", fullToken)
                .when().get("/api/v1/gps/sources")
                .then().statusCode(200)
                .body("[0].token", org.hamcrest.Matchers.equalTo("overland-secret-" + userId));
    }

    @Test
    void revokedTokenIsRejected() {
        given().header("X-API-Key", fullToken)
                .when().delete("/api/v1/api-tokens/" + ingestTokenId)
                .then().statusCode(204);

        given().header("Authorization", "Bearer " + ingestToken)
                .contentType(ContentType.JSON)
                .body(point("2026-05-29T11:00:00Z", 50.45))
                .when().post(INGEST)
                .then().statusCode(401);
    }

    /** Connects a Home Assistant source to the token, as Location Sources does; returns the source id. */
    private String connect(UUID apiTokenId) {
        return given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("type", "HOME_ASSISTANT", "connectionType", "HTTP", "apiTokenId", apiTokenId.toString()))
                .when().post("/api/v1/gps/sources/")
                .then().statusCode(201)
                .extract().path("id");
    }

    private List<Map<String, Object>> sources() {
        JsonPath json = given().header("X-API-Key", fullToken)
                .when().get("/api/v1/gps/sources")
                .then().statusCode(200)
                .extract().jsonPath();
        return json.getList("$");
    }

    private Map<String, Object> point(String timestamp, double latitude) {
        return Map.of("device_id", "phone", "timestamp", timestamp,
                "location", Map.of("latitude", latitude, "longitude", 30.52, "accuracy", 6.0));
    }
}
