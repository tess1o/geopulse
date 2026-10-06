package org.github.tess1o.geopulse.shared.api;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verifies that the pre-v1 health and metrics URLs used by 1.x healthchecks, probes and scrape
 * configs keep resolving, unauthenticated, to the canonical {@code /api/v1/system/*} endpoints.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class LegacySystemPathRewriterTest {

    @Test
    void legacyHealthPathReturnsHealthStatus() {
        given()
                .when()
                .get("/api/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void legacyPrometheusPathReturnsMetrics() {
        given()
                .when()
                .get("/api/prometheus/metrics")
                .then()
                .statusCode(200)
                .body(containsString("jvm_"));
    }
}
