package org.github.tess1o.geopulse.shared.api;

import io.quarkiverse.httpproblem.HttpProblem;

import java.util.Map;

/**
 * The single implementation of "application exception to problem envelope".
 *
 * <p>Called by {@link GeoPulseExceptionMapper} on the normal path, and by
 * {@link GeoPulseProblemPostProcessor} as a fallback for the case where a {@link GeoPulseException} reaches the
 * post-processor without having passed through its own mapper — for example when it is wrapped inside a library
 * exception.</p>
 *
 * <p>This is not the wire shape in full: {@code type}, {@code instance}, {@code requestId} and the
 * {@code X-Error-Id} header are added later, by the post-processor, for every problem regardless of origin.</p>
 */
final class ProblemFactory {

    private ProblemFactory() {
    }

    static HttpProblem from(GeoPulseException exception) {
        return of(exception.code(), exception.detail(), exception.parameters(), exception.errorId(), null);
    }

    /**
     * Builds the envelope from an application exception's payload rather than from the exception itself, so that an
     * exception loaded by a previous application's classloader — see {@link GeoPulseProblemPostProcessor} — can be
     * classified without being cast.
     *
     * @param base existing problem whose headers, instance and other non-exception fields should be kept, or
     *             {@code null} to start from scratch
     */
    static HttpProblem of(ApiErrorCode code, String detail, Map<String, Object> parameters, String errorId,
                          HttpProblem base) {
        HttpProblem.Builder builder = base == null ? HttpProblem.builder() : HttpProblem.builder(base);
        builder.withStatus(code.statusCode())
                .withTitle(code.title())
                .withDetail(detail)
                .with("code", code);
        if (errorId != null) {
            builder.with("errorId", errorId);
        }
        if (!parameters.isEmpty()) {
            builder.with("parameters", parameters);
        }
        return builder.build();
    }
}
