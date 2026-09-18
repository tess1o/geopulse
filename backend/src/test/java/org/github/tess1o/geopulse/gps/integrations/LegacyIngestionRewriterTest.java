package org.github.tess1o.geopulse.gps.integrations;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verifies that the ingestion URLs kept for backward compatibility keep resolving to the
 * canonical {@code /api/v1/gps/ingest/*} endpoints and that the deprecation warning is emitted.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class LegacyIngestionRewriterTest {

    private static final String PASSWORD = "password123";
    private static final String OWNTRACKS_PASSWORD = "owntracks-password";

    @Test
    void legacyApiPathStillIngestsAndLogsDeprecationWarning() {
        List<String> captured = Collections.synchronizedList(new ArrayList<>());
        Logger logger = LogContext.getLogContext().getLogger(LegacyIngestionRewriter.class.getName());
        Handler handler = recordingHandler(captured);
        logger.addHandler(handler);
        try {
            String accessToken = ingestVia("/api/owntracks");
            assertMapPoints(accessToken, 1);
            assertThat(captured).anyMatch(message -> message != null && message.contains("[DEPRECATION]"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void canonicalGpsIngestPathIngests() {
        String accessToken = ingestVia("/api/v1/gps/ingest/owntracks");
        assertMapPoints(accessToken, 1);
    }

    private String ingestVia(String path) {
        String accessToken = registerAndLogin();
        String ownTracksUsername = TestIds.uniqueValue("legacy-owntracks");
        createOwnTracksSource(accessToken, ownTracksUsername);

        given()
                .auth().preemptive().basic(ownTracksUsername, OWNTRACKS_PASSWORD)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "_type", "location",
                        "lat", 50.4501,
                        "lon", 30.5234,
                        "tst", 1786380000,
                        "acc", 8.0
                ))
                .when()
                .post(path)
                .then()
                .statusCode(200)
                .body(equalTo("[]"));

        return accessToken;
    }

    private Handler recordingHandler(List<String> messages) {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    private String registerAndLogin() {
        String email = TestIds.uniqueEmail("legacy-ingest");
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", email,
                        "password", PASSWORD,
                        "fullName", "Legacy Ingest User",
                        "timezone", "UTC"
                ))
                .when()
                .post("/api/v1/registrations")
                .then()
                .statusCode(201);

        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .when()
                .post("/api/v1/auth/api-sessions")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    private void createOwnTracksSource(String accessToken, String ownTracksUsername) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "type", "OWNTRACKS",
                        "username", ownTracksUsername,
                        "password", OWNTRACKS_PASSWORD,
                        "connectionType", "HTTP",
                        "filterInaccurateData", false,
                        "enableDuplicateDetection", false
                ))
                .when()
                .post("/api/v1/gps/sources/")
                .then()
                .statusCode(201);
    }

    private void assertMapPoints(String accessToken, int count) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("startTime", "2026-08-10T16:39:00Z")
                .queryParam("endTime", "2026-08-10T16:43:00Z")
                .queryParam("limit", 10)
                .when()
                .get("/api/v1/gps/points/map")
                .then()
                .statusCode(200)
                .body("totalCount", equalTo(count));
    }
}
