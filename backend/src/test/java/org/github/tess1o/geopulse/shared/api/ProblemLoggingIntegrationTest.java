package org.github.tess1o.geopulse.shared.api;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.auth.service.ApiTokenAuthenticationMechanism;
import org.github.tess1o.geopulse.auth.service.ApiTokenSecretService;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ProblemLoggingIntegrationTest {

    @Inject UserService userService;

    String corruptHashEmail;

    @BeforeEach
    @Transactional
    void setUp() {
        corruptHashEmail = TestIds.uniqueEmail("corrupt-password-hash");
        UserEntity user = userService.registerUser(corruptHashEmail, "password", "Corrupt Hash User", "UTC");
        user.setPasswordHash("not-a-bcrypt-hash");
    }

    @Test
    void problemLoggerEmitsOneInfoWithoutStackFor4xx() {
        List<LogRecord> records = capture("http-problem", () -> given()
                .contentType("application/json")
                .body(Map.of("email", TestIds.uniqueEmail("missing-log-user"), "password", "wrong-password"))
                .when().post("/api/v1/auth/sessions")
                .then().statusCode(401));

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getLevel()).isEqualTo(Level.INFO);
        assertThat(records.getFirst().getThrown()).isNull();
    }

    @Test
    void problemLoggerEmitsOneErrorWithOriginalCauseFor5xx() {
        AtomicReference<io.restassured.response.Response> response = new AtomicReference<>();
        List<LogRecord> records = capture("http-problem", () -> response.set(given()
                .contentType("application/json")
                .body(Map.of("email", corruptHashEmail, "password", "password"))
                .when().post("/api/v1/auth/sessions")));

        assertProblemEnvelope(response.get(), 500, "INTERNAL_ERROR");
        assertThat(response.get().body().asString()).doesNotContain("bcrypt");
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getLevel()).isEqualTo(Level.SEVERE);
        assertThat(causeChain(records.getFirst().getThrown()))
                .anyMatch(InvalidPasswordException.class::isInstance);
    }

    @Test
    void rawApiTokenChallengeDoesNotAddAnApplicationDuplicate() {
        List<LogRecord> records = capture(ApiTokenAuthenticationMechanism.class.getName(), () -> given()
                .header("X-API-Key", ApiTokenSecretService.TOKEN_PREFIX + "invalid")
                .when().get("/api/v1/users/me")
                .then().statusCode(401));

        assertThat(records).isEmpty();
    }

    private List<LogRecord> capture(String category, Runnable request) {
        List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
        Logger logger = LogContext.getLogContext().getLogger(category);
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override public void flush() {
            }

            @Override public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            request.run();
            return List.copyOf(records);
        } finally {
            logger.removeHandler(handler);
        }
    }

    private List<Throwable> causeChain(Throwable throwable) {
        List<Throwable> causes = new ArrayList<>();
        while (throwable != null) {
            causes.add(throwable);
            throwable = throwable.getCause();
        }
        return causes;
    }
}
