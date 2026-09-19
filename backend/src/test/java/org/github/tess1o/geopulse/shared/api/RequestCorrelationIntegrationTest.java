package org.github.tess1o.geopulse.shared.api;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class RequestCorrelationIntegrationTest {

    @Test
    void concurrentRequestsNeverCrossCorrelationIds() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Correlation>> requests = java.util.stream.IntStream.range(0, 24)
                    .mapToObj(ignored -> (Callable<Correlation>) this::request)
                    .toList();
            List<Correlation> correlations = executor.invokeAll(requests).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();

            assertThat(correlations).allSatisfy(correlation -> {
                assertThat(correlation.header()).isNotBlank();
                assertThat(correlation.body()).isEqualTo(correlation.header());
            });
            assertThat(correlations.stream().map(Correlation::header).collect(java.util.stream.Collectors.toSet()))
                    .hasSize(correlations.size());
        }
    }

    private Correlation request() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of())
                .when().post("/api/v1/registrations");
        assertThat(response.statusCode()).isEqualTo(400);
        return new Correlation(response.getHeader("X-Request-Id"), response.jsonPath().getString("requestId"));
    }

    private record Correlation(String header, String body) {
    }
}
