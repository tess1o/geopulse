package org.github.tess1o.geopulse.shared.api;

import io.quarkiverse.httpproblem.HttpProblem;

import java.util.Map;

public final class ApiProblems {

    private ApiProblems() {
    }

    public static HttpProblem problem(ApiErrorCode code, String detail) {
        return problem(code, detail, Map.of());
    }

    public static HttpProblem problem(ApiErrorCode code, String detail, Map<String, Object> parameters) {
        HttpProblem.Builder builder = HttpProblem.builder()
                .withStatus(code.statusCode())
                .withTitle(code.title())
                .withDetail(detail)
                .with("code", code);
        if (parameters != null && !parameters.isEmpty()) {
            builder.with("parameters", Map.copyOf(parameters));
        }
        return builder.build();
    }
}
