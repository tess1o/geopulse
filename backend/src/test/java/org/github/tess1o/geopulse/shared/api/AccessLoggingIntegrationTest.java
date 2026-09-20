package org.github.tess1o.geopulse.shared.api;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.BeforeEach;
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

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(AccessLoggingIntegrationTest.AccessLogProfile.class)
@SerializedDatabaseTest
class AccessLoggingIntegrationTest {

    private static final String CATEGORY = "io.quarkus.http.access-log";

    @Inject UserService userService;

    String corruptHashEmail;

    @BeforeEach
    @Transactional
    void setUp() {
        corruptHashEmail = TestIds.uniqueEmail("access-log-500");
        UserEntity user = userService.registerUser(corruptHashEmail, "password", "Access Log User", "UTC");
        user.setPasswordHash("not-a-bcrypt-hash");
    }

    @Test
    void recordsSanitizedRequestsWithNumericDurationAndMatchingRequestId() {
        List<LogRecord> records = new ArrayList<>();
        Response response = capture(records, () -> given().queryParam("secret", "marker")
                .when().get("/api/v1/system/version"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getMessage())
                .doesNotContain("secret", "marker", "?")
                .contains("GET /api/v1/system/version 200 ")
                .contains("req=" + response.getHeader(RequestCorrelationHandler.REQUEST_ID_HEADER))
                .matches(".* \\d+(?:\\.\\d+)?ms req=.*");
    }

    @Test
    void recordsFourAndFiveHundredResponses() {
        List<LogRecord> badRequest = new ArrayList<>();
        capture(badRequest, () -> given().contentType("application/json").body(Map.of())
                .when().post("/api/v1/registrations"));
        assertThat(badRequest).singleElement().satisfies(record ->
                assertThat(record.getMessage()).contains("POST /api/v1/registrations 400 "));

        List<LogRecord> serverError = new ArrayList<>();
        capture(serverError, () -> given().contentType("application/json")
                .body(Map.of("email", corruptHashEmail, "password", "password"))
                .when().post("/api/v1/auth/sessions"));
        assertThat(serverError).singleElement().satisfies(record ->
                assertThat(record.getMessage()).contains("POST /api/v1/auth/sessions 500 "));
    }

    @Test
    void excludesOperationalAndTokenInPathRoutes() {
        List<LogRecord> records = new ArrayList<>();
        capture(records, () -> given().when().get("/api/v1/system/health"));
        capture(records, () -> given().when().get("/api/v1/registration-invitations/secret-token"));

        assertThat(records).isEmpty();
    }

    private Response capture(List<LogRecord> records, java.util.function.Supplier<Response> request) {
        List<LogRecord> synchronizedRecords = Collections.synchronizedList(records);
        Logger logger = LogContext.getLogContext().getLogger(CATEGORY);
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { synchronizedRecords.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            return request.get();
        } finally {
            logger.removeHandler(handler);
        }
    }

    public static class AccessLogProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.http.access-log.enabled", "true",
                    "quarkus.http.record-request-start-time", "true",
                    "quarkus.http.access-log.pattern", "%{METHOD} %{REQUEST_PATH} %{RESPONSE_CODE} %{BYTES_SENT} %{RESPONSE_TIME}ms req=%{o,X-Request-Id}",
                    "quarkus.http.access-log.exclude-pattern", "/api/v1/system/(health|metrics).*|/q/.*|/api/v1/registration-invitations/.*|/api/v1/public/share-links/.*",
                    "quarkus.log.category.\"io.quarkus.http.access-log\".level", "INFO"
            );
        }
    }
}
