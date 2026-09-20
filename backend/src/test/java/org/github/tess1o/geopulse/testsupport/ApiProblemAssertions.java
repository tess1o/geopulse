package org.github.tess1o.geopulse.testsupport;

import io.restassured.response.Response;

import static org.assertj.core.api.Assertions.assertThat;

public final class ApiProblemAssertions {

    private ApiProblemAssertions() {
    }

    public static void assertProblemEnvelope(Response response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(response.getHeader("X-Error-Id")).isNotBlank();
        assertThat(response.jsonPath().getString("code")).isEqualTo(code);
        assertThat(response.jsonPath().getString("type")).isEqualTo("urn:geopulse:error:" + code);
        assertThat(response.jsonPath().getString("instance")).isNotBlank();
        assertThat(response.jsonPath().getString("requestId")).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(response.jsonPath().getString("errorId")).isEqualTo(response.getHeader("X-Error-Id"));
    }
}
