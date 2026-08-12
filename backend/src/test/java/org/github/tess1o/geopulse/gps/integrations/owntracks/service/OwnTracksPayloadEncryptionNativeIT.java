package org.github.tess1o.geopulse.gps.integrations.owntracks.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusIntegrationTest
@QuarkusTestResource(value = OwnTracksPayloadEncryptionNativeTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class OwnTracksPayloadEncryptionNativeIT {

    private static final String PASSWORD = "password123";
    private static final String OWNTRACKS_PASSWORD = "owntracks-password";
    private static final String PAYLOAD_SECRET = "native-secret";

    @Test
    void nativeExecutableDecryptsEncryptedOwnTracksHttpPayloads() {
        String accessToken = registerAndLogin("owntracks-native-encrypted");
        String ownTracksUsername = TestIds.uniqueValue("owntracks-native-user");
        createOwnTracksSource(accessToken, ownTracksUsername, PAYLOAD_SECRET);

        String encryptedData = encryptSecretBox(
                """
                        {
                          "_type": "location",
                          "lat": 50.4501,
                          "lon": 30.5234,
                          "tst": 1786380000,
                          "acc": 8.0,
                          "batt": 76.0,
                          "vel": 3.5,
                          "alt": 179.0,
                          "topic": "owntracks/native-user/native-phone"
                        }
                        """,
                PAYLOAD_SECRET,
                deterministicNonce(1)
        );

        postOwnTracksPayload(ownTracksUsername, Map.of("_type", "encrypted", "data", encryptedData));

        assertMapPoints(accessToken, 1);
        assertMapPoint(accessToken, 0, 50.4501, 30.5234, "2026-08-10T16:40:00Z");
    }

    @Test
    void nativeExecutableStillAcceptsPlaintextOwnTracksHttpPayloads() {
        String accessToken = registerAndLogin("owntracks-native-plaintext");
        String ownTracksUsername = TestIds.uniqueValue("owntracks-native-plain-user");
        createOwnTracksSource(accessToken, ownTracksUsername, null);

        postOwnTracksPayload(
                ownTracksUsername,
                Map.of(
                        "_type", "location",
                        "lat", 51.5007,
                        "lon", -0.1246,
                        "tst", 1786380060,
                        "acc", 6.0
                )
        );

        assertMapPoints(accessToken, 1);
        assertMapPoint(accessToken, 0, 51.5007, -0.1246, "2026-08-10T16:41:00Z");
    }

    @Test
    void nativeExecutableSkipsEncryptedOwnTracksPayloadsWhenSecretDoesNotMatch() {
        String accessToken = registerAndLogin("owntracks-native-bad-secret");
        String ownTracksUsername = TestIds.uniqueValue("owntracks-native-bad-user");
        createOwnTracksSource(accessToken, ownTracksUsername, PAYLOAD_SECRET);

        String encryptedData = encryptSecretBox(
                """
                        {
                          "_type": "location",
                          "lat": 48.8566,
                          "lon": 2.3522,
                          "tst": 1786380120,
                          "topic": "owntracks/native-user/bad-secret-phone"
                        }
                        """,
                "wrong-secret",
                deterministicNonce(2)
        );

        postOwnTracksPayload(ownTracksUsername, Map.of("_type", "encrypted", "data", encryptedData));

        assertMapPoints(accessToken, 0);
    }

    private String registerAndLogin(String prefix) {
        String email = TestIds.uniqueEmail(prefix);
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", email,
                        "password", PASSWORD,
                        "fullName", "OwnTracks Native User",
                        "timezone", "UTC"
                ))
                .when()
                .post("/api/users/register")
                .then()
                .statusCode(201)
                .body("status", equalTo("success"));

        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .when()
                .post("/api/auth/api-login")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .extract()
                .path("data.accessToken");
    }

    private void createOwnTracksSource(String accessToken, String ownTracksUsername, String payloadSecret) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", "OWNTRACKS");
        payload.put("username", ownTracksUsername);
        payload.put("password", OWNTRACKS_PASSWORD);
        payload.put("connectionType", "HTTP");
        payload.put("filterInaccurateData", false);
        payload.put("enableDuplicateDetection", false);
        if (payloadSecret != null) {
            payload.put("payloadEncryptionSecret", payloadSecret);
        }

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/gps/source/")
                .then()
                .statusCode(200)
                .body("hasPayloadEncryptionSecret", equalTo(payloadSecret != null))
                .body("active", equalTo(true));
    }

    private void postOwnTracksPayload(String ownTracksUsername, Map<String, Object> payload) {
        given()
                .auth().preemptive().basic(ownTracksUsername, OWNTRACKS_PASSWORD)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/owntracks")
                .then()
                .statusCode(200)
                .body(equalTo("[]"));
    }

    private void assertMapPoints(String accessToken, int count) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("startTime", "2026-08-10T16:39:00Z")
                .queryParam("endTime", "2026-08-10T16:43:00Z")
                .queryParam("limit", 10)
                .when()
                .get("/api/gps/map-points")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.totalCount", equalTo(count))
                .body("data.returnedCount", equalTo(count));
    }

    private void assertMapPoint(String accessToken, int index, double lat, double lon, String timestamp) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("startTime", "2026-08-10T16:39:00Z")
                .queryParam("endTime", "2026-08-10T16:43:00Z")
                .queryParam("limit", 10)
                .when()
                .get("/api/gps/map-points")
                .then()
                .statusCode(200)
                .body("data.points[%d].latitude".formatted(index), equalTo((float) lat))
                .body("data.points[%d].longitude".formatted(index), equalTo((float) lon))
                .body("data.points[%d].timestamp".formatted(index), equalTo(timestamp))
                .body("data.points[%d].sourceType".formatted(index), equalTo("OWNTRACKS"));
    }

    private String encryptSecretBox(String plaintext, String secret, byte[] nonce) {
        byte[] key = OwnTracksEncryptionKeyUtil.toSecretBoxKey(secret);
        byte[] ciphertext = cryptSecretBoxPayload(plaintext.getBytes(StandardCharsets.UTF_8), key, nonce);
        byte[] mac = computePoly1305(ciphertext, firstSecretBoxBlock(key, nonce));

        byte[] combined = new byte[nonce.length + mac.length + ciphertext.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(mac, 0, combined, nonce.length, mac.length);
        System.arraycopy(ciphertext, 0, combined, nonce.length + mac.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private byte[] firstSecretBoxBlock(byte[] key, byte[] nonce) {
        XSalsa20Engine engine = new XSalsa20Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(key), nonce));

        byte[] zeros = new byte[32];
        byte[] poly1305Key = new byte[32];
        engine.processBytes(zeros, 0, zeros.length, poly1305Key, 0);
        return poly1305Key;
    }

    private byte[] cryptSecretBoxPayload(byte[] input, byte[] key, byte[] nonce) {
        XSalsa20Engine engine = new XSalsa20Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(key), nonce));

        byte[] discarded = new byte[32];
        engine.processBytes(discarded, 0, discarded.length, discarded, 0);

        byte[] output = new byte[input.length];
        engine.processBytes(input, 0, input.length, output, 0);
        return output;
    }

    private byte[] computePoly1305(byte[] message, byte[] key) {
        Mac mac = new Poly1305();
        mac.init(new KeyParameter(key));
        mac.update(message, 0, message.length);
        byte[] output = new byte[16];
        mac.doFinal(output, 0);
        return output;
    }

    private byte[] deterministicNonce(int offset) {
        byte[] nonce = new byte[24];
        for (int i = 0; i < nonce.length; i++) {
            nonce[i] = (byte) (i + offset);
        }
        return nonce;
    }
}
