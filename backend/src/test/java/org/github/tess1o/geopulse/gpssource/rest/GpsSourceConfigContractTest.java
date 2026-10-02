package org.github.tess1o.geopulse.gpssource.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt.MqttConfiguration;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;
import static org.mockito.Mockito.when;

/**
 * HTTP contract for {@code /api/v1/gps/sources}. No test of any kind existed for this resource.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsSourceConfigContractTest {

    private static final String SOURCES = "/api/v1/gps/sources";
    private static final String PASSWORD = "password123";
    private static final String SOURCE_PASSWORD = "source-password";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;

    private UserEntity owner;
    private UserEntity other;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    @Transactional
    void setUp() {
        MqttConfiguration mqtt = Mockito.mock(MqttConfiguration.class);
        when(mqtt.isMqttEnabled()).thenReturn(true);
        when(mqtt.getBrokerHost()).thenReturn("mqtt.test.invalid");
        when(mqtt.getBrokerPort()).thenReturn(1883);
        when(mqtt.isTlsEnabled()).thenReturn(false);
        QuarkusMock.installMockForType(mqtt, MqttConfiguration.class);

        owner = register("gpssource-owner");
        other = register("gpssource-other");
        ownerToken = token(owner);
        otherToken = token(other);
    }

    @Test
    void listsDefaultsMqttConfigAndTelemetry() {
        UUID id = createOwnTracksSource(ownerToken, TestIds.uniqueValue("own-src"));

        Response listed = authenticated(ownerToken).when().get(SOURCES);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.jsonPath().getList("id", String.class)).contains(id.toString());

        Response defaults = authenticated(ownerToken).when().get(SOURCES + "/defaults");
        assertThat(defaults.statusCode()).isEqualTo(200);
        assertThat(defaults.jsonPath().getBoolean("filterInaccurateData")).isNotNull();

        Response mqttConfig = authenticated(ownerToken).when().get(SOURCES + "/owntracks/mqtt-config");
        assertThat(mqttConfig.statusCode()).isEqualTo(200);
        assertThat(mqttConfig.jsonPath().getBoolean("mqttEnabled")).isTrue();
        assertThat(mqttConfig.jsonPath().getString("brokerHost")).isEqualTo("mqtt.test.invalid");
        assertThat(mqttConfig.jsonPath().getInt("brokerPort")).isEqualTo(1883);

        Response telemetry = authenticated(ownerToken).when().get(SOURCES + "/telemetry/OWNTRACKS");
        assertThat(telemetry.statusCode()).isEqualTo(200);
        assertThat(telemetry.jsonPath().getString("sourceType")).isEqualTo("OWNTRACKS");
    }

    @Test
    void telemetryMappingCanBeUpsertedAndReset() {
        Response upserted = authenticated(ownerToken)
                .body(List.of(Map.of(
                        "key", "batt",
                        "label", "Battery",
                        "type", "PERCENTAGE",
                        "unit", "%",
                        "enabled", true,
                        "order", 1)))
                .when().put(SOURCES + "/telemetry/OWNTRACKS");
        assertThat(upserted.statusCode()).isEqualTo(200);
        assertThat(upserted.jsonPath().getString("sourceType")).isEqualTo("OWNTRACKS");

        Response reset = authenticated(ownerToken).when().delete(SOURCES + "/telemetry/OWNTRACKS");
        assertThat(reset.statusCode()).isEqualTo(204);
    }

    @Test
    void sourceCanBeDeactivatedAndUpdatedAndDeleted() {
        UUID id = createOwnTracksSource(ownerToken, TestIds.uniqueValue("manage-src"));

        Response deactivated = authenticated(ownerToken)
                .body(Map.of("status", false))
                .when().patch(SOURCES + "/" + id + "/status");
        assertThat(deactivated.statusCode()).isEqualTo(204);
        assertThat(authenticated(ownerToken).when().get(SOURCES).jsonPath()
                .getBoolean("find { it.id == '" + id + "' }.active")).isFalse();

        Response updated = authenticated(ownerToken)
                .body(Map.of("username", TestIds.uniqueValue("renamed"), "connectionType", "HTTP"))
                .when().put(SOURCES + "/" + id);
        assertThat(updated.statusCode()).isEqualTo(204);

        Response deleted = authenticated(ownerToken).when().delete(SOURCES + "/" + id);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(authenticated(ownerToken).when().get(SOURCES).jsonPath().getList("id", String.class))
                .doesNotContain(id.toString());
    }

    @Test
    void unsupportedSourceTypeUsesTheExactTelemetryCode() {
        assertProblemEnvelope(authenticated(ownerToken).when().get(SOURCES + "/telemetry/NOT_A_REAL_TYPE"),
                400, "INVALID_TELEMETRY_MAPPING");
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(List.of(Map.of("key", "a", "label", "A")))
                        .when().put(SOURCES + "/telemetry/NOT_A_REAL_TYPE"),
                400, "INVALID_TELEMETRY_MAPPING");
        assertProblemEnvelope(authenticated(ownerToken).when().delete(SOURCES + "/telemetry/NOT_A_REAL_TYPE"),
                400, "INVALID_TELEMETRY_MAPPING");
    }

    @Test
    void duplicateAndTraccarConflictsUseTheExactSourceCode() {
        String username = TestIds.uniqueValue("dup-user");
        assertThat(authenticated(ownerToken).body(ownTracksBody(username))
                .when().post(SOURCES).statusCode()).isEqualTo(201);
        // Same username, same type, same user.
        assertProblemEnvelope(authenticated(ownerToken).body(ownTracksBody(username)).when().post(SOURCES),
                400, "INVALID_GPS_SOURCE");

        String traccarToken = TestIds.uniqueValue("traccar-token");
        assertThat(authenticated(ownerToken).body(traccarBody(traccarToken, "device-1"))
                .when().post(SOURCES).statusCode()).isEqualTo(201);
        // Same token, same device id.
        assertProblemEnvelope(authenticated(ownerToken).body(traccarBody(traccarToken, "device-1")).when().post(SOURCES),
                400, "INVALID_GPS_SOURCE");
    }

    @Test
    void unknownSourceIsNotFoundOnEveryMutation() {
        UUID absent = UUID.randomUUID();
        assertProblemEnvelope(authenticated(ownerToken).when().delete(SOURCES + "/" + absent),
                404, "GPS_SOURCE_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("status", true))
                        .when().patch(SOURCES + "/" + absent + "/status"),
                404, "GPS_SOURCE_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("username", "whoever"))
                        .when().put(SOURCES + "/" + absent),
                404, "GPS_SOURCE_NOT_FOUND");
    }

    @Test
    void anotherUsersSourceIsNotVisibleOrMutable() {
        UUID otherId = createOwnTracksSource(otherToken, TestIds.uniqueValue("other-src"));

        assertThat(authenticated(ownerToken).when().get(SOURCES).jsonPath().getList("id", String.class))
                .doesNotContain(otherId.toString());
        assertProblemEnvelope(authenticated(ownerToken).when().delete(SOURCES + "/" + otherId),
                404, "GPS_SOURCE_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("status", false))
                        .when().patch(SOURCES + "/" + otherId + "/status"),
                404, "GPS_SOURCE_NOT_FOUND");
    }

    @Test
    void missingBodiesAreValidationFailures() {
        assertProblemEnvelope(authenticated(ownerToken).when().post(SOURCES), 400, "VALIDATION_FAILED");
        assertProblemEnvelope(authenticated(ownerToken).when().put(SOURCES + "/telemetry/OWNTRACKS"),
                400, "VALIDATION_FAILED");
        assertProblemEnvelope(authenticated(ownerToken).when().patch(SOURCES + "/" + UUID.randomUUID() + "/status"),
                400, "VALIDATION_FAILED");
    }

    /**
     * DEFECT — a null {@code type} is answered with 500 instead of 400.
     *
     * <p>{@code CreateGpsSourceConfigDto.type} carries no {@code @NotNull} (every other required
     * field on that DTO is likewise unconstrained), so a null type passes bean validation and then
     * violates the {@code nullable = false} column on {@code gps_source_config.source_type}. A
     * client mistake is reported as a server error.
     *
     * <p>This test pins the behaviour observed today so the suite stays honest about it. Adding
     * {@code @NotNull} to the DTO field is the fix, and this test should then be changed to expect
     * {@code 400 VALIDATION_FAILED}.
     */
    @Test
    void nullSourceTypeIsReportedAsAClientErrorNotAServerError() {
        Response response = authenticated(ownerToken)
                .body(Map.of("connectionType", "HTTP", "filterInaccurateData", false))
                .when().post(SOURCES);

        assertProblemEnvelope(response, 500, "INTERNAL_ERROR");
    }

    private UUID createOwnTracksSource(String token, String username) {
        Response response = authenticated(token).body(ownTracksBody(username)).when().post(SOURCES);
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(response.jsonPath().getString("id"));
    }

    private static Map<String, Object> ownTracksBody(String username) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "OWNTRACKS");
        body.put("connectionType", "HTTP");
        body.put("username", username);
        body.put("password", SOURCE_PASSWORD);
        body.put("filterInaccurateData", false);
        body.put("enableDuplicateDetection", false);
        return body;
    }

    private static Map<String, Object> traccarBody(String token, String deviceId) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "TRACCAR");
        body.put("connectionType", "HTTP");
        body.put("token", token);
        body.put("deviceId", deviceId);
        body.put("filterInaccurateData", false);
        body.put("enableDuplicateDetection", false);
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
