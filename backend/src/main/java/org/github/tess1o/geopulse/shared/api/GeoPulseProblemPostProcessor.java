package org.github.tess1o.geopulse.shared.api;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import io.quarkiverse.httpproblem.postprocessing.ProblemPostProcessor;
import io.quarkiverse.httpproblem.validation.HttpValidationProblem;
import io.quarkiverse.httpproblem.validation.Violation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.vertx.ext.web.RoutingContext;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.VALIDATION_FAILED;

/**
 * Normalizes every problem for the wire, whatever produced it: application exceptions, the guard filters,
 * Bean Validation, or the framework's own mappers (routing 404/405, security, malformed bodies).
 *
 * <p>Runs at priority 102 so it executes before the library's {@code ProblemLogger} (101), {@code MdcPropertiesInjector}
 * (100) and {@code ProblemDefaultsProvider} (99) — they therefore no-op, and this class owns {@code code},
 * {@code type}, {@code errorId}, {@code instance} and {@code requestId} for both the response and the log line.
 * Ordering is descending; see {@code ProblemPostProcessor.DEFAULT_ORDERING}.</p>
 *
 * <p>Everything this class reads back out of a problem — an existing {@code code}, an application exception in the
 * cause chain — is matched by <em>name</em>, never by class identity. The library stores post-processors in a
 * static registry that it clears only on live reload ({@code ProblemProcessor.resetRecorder}), so an application
 * restart inside one JVM — which is what the test suite and continuous testing do per profile or per test
 * resource — leaves the <em>previous</em> application's instance registered next to this one. That instance runs
 * over this application's problems and cannot see this application's {@code ApiErrorCode} or
 * {@code GeoPulseException} classes, only same-named classes of its own classloader. Matching by name keeps the
 * wire contract identical no matter which instance runs last.</p>
 */
@ApplicationScoped
public class GeoPulseProblemPostProcessor implements ProblemPostProcessor {

    private static final int PRIORITY = 102;

    /** The library serializes its "unknown" location as "?"; expose one value for one concept. */
    private static final String UNKNOWN_LOCATION = "unknown";

    /** {@link GeoPulseException}'s name, the only thing shared with an instance from another classloader. */
    private static final String APP_EXCEPTION_TYPE = GeoPulseException.class.getName();

    @Inject
    RoutingContext routingContext;

    /** An application exception's payload, detached from the class that carried it. */
    private record AppError(ApiErrorCode code, String detail, Map<String, Object> parameters, String errorId) {
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public HttpProblem apply(HttpProblem problem, ProblemContext problemContext) {
        HttpProblem normalized = problem;
        if (problemContext.cause instanceof ConstraintViolationException exception) {
            normalized = validationProblem(problem, exception);
        }

        // A problem that already carries a code was built from its exception by GeoPulseExceptionMapper (or by the
        // guard filters), so there is nothing left to derive. Only when nothing classified it do we look for an
        // application exception in the cause chain — a GeoPulseException wrapped in a library exception never
        // reached its own mapper, and would otherwise be answered with a generic code.
        if (!normalized.getParameters().containsKey("code")) {
            AppError wrapped = findAppError(problemContext.cause);
            if (wrapped != null) {
                normalized = ProblemFactory.of(wrapped.code(), wrapped.detail(), wrapped.parameters(),
                        wrapped.errorId(), normalized);
            }
        }

        ApiErrorCode code = resolveCode(normalized);
        Object existingErrorId = normalized.getParameters().get("errorId");
        String errorId = existingErrorId == null ? UUID.randomUUID().toString() : existingErrorId.toString();
        RequestCorrelationHandler.attachErrorId(routingContext, errorId);

        HttpProblem.Builder builder = HttpProblem.builder(normalized)
                .withType(code.typeUri())
                .with("code", code)
                .with("errorId", errorId)
                .withHeader(RequestCorrelationHandler.ERROR_ID_HEADER, errorId);
        if (normalized.getInstance() == null && problemContext.path != null) {
            builder.withInstance(instanceUri(problemContext.path));
        }
        String requestId = routingContext.get(RequestCorrelationHandler.REQUEST_ID);
        if (requestId != null) {
            builder.with("requestId", requestId);
        }
        return builder.build();
    }

    /**
     * Returns the <em>outermost</em> application exception in the chain, as data rather than as an instance:
     * see {@link #readForeignAppError} for why the class cannot be relied on.
     *
     * <p>This is deliberately not a defence against a broad catch that re-wraps an already classified exception
     * into a new {@code GeoPulseException}: the outer one wins, and the inner classification is lost. That bug
     * class is prevented by the {@code pmd-error-handling.xml} gate, not here. Targeting the innermost instead
     * would silently override deliberate re-wraps.</p>
     */
    private static AppError findAppError(Throwable cause) {
        while (cause != null) {
            if (cause instanceof GeoPulseException exception) {
                return new AppError(exception.code(), exception.detail(), exception.parameters(), exception.errorId());
            }
            if (APP_EXCEPTION_TYPE.equals(cause.getClass().getName())) {
                AppError foreign = readForeignAppError(cause);
                if (foreign != null) {
                    return foreign;
                }
            }
            cause = cause.getCause();
        }
        return null;
    }

    /**
     * Reads a {@code GeoPulseException} that was loaded by a previous application's classloader.
     *
     * <p>An application restart in the same JVM (tests, continuous testing) leaves the previous application's
     * post-processor registered — see the class javadoc — and that instance sees this application's exception
     * through a {@code Class} object it cannot cast to. The accessors are public on a public class, so they can
     * be invoked reflectively; anything unexpected simply means "not an application exception", which leaves the
     * problem to the status-derived code it would have had before this fallback existed.</p>
     */
    @SuppressWarnings("unchecked")
    private static AppError readForeignAppError(Throwable exception) {
        try {
            Class<?> type = exception.getClass();
            Object code = codeByName(type.getMethod("code").invoke(exception));
            Object detail = type.getMethod("detail").invoke(exception);
            Object parameters = type.getMethod("parameters").invoke(exception);
            Object errorId = type.getMethod("errorId").invoke(exception);
            if (!(code instanceof ApiErrorCode resolved) || !(detail instanceof String text)) {
                return null;
            }
            return new AppError(resolved, text,
                    parameters instanceof Map<?, ?> values ? (Map<String, Object>) values : Map.of(),
                    errorId instanceof String id ? id : null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static ApiErrorCode resolveCode(HttpProblem problem) {
        ApiErrorCode existing = codeByName(problem.getParameters().get("code"));
        if (existing != null) {
            return existing;
        }
        return problem instanceof HttpValidationProblem || problem.getParameters().containsKey("violations")
                ? VALIDATION_FAILED
                : ApiErrorCode.forStatus(problem.getStatusCode());
    }

    /**
     * Returns the {@link ApiErrorCode} named by {@code value} — an enum of this classloader, an enum of a previous
     * application's classloader, or a plain name — or {@code null} when it names no code of this application.
     *
     * <p>Resolving by name rather than by {@code instanceof} is what keeps the code stable when a previous
     * application's post-processor instance runs over this application's problem: every application in one JVM is
     * built from the same classes, so the foreign enum carries the same constant names.</p>
     */
    private static ApiErrorCode codeByName(Object value) {
        final String name;
        if (value instanceof Enum<?> named) {
            name = named.name();
        } else if (value instanceof String text) {
            name = text;
        } else {
            return null;
        }
        try {
            return ApiErrorCode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** A violation's field and location, detached from the class that carried them. */
    private record OriginalViolation(String field, String in) {
    }

    private static HttpProblem validationProblem(HttpProblem problem, ConstraintViolationException exception) {
        List<OriginalViolation> original = readOriginalViolations(problem);
        List<ConstraintViolation<?>> constraints = new ArrayList<>(exception.getConstraintViolations());
        List<ApiViolation> violations = new ArrayList<>(constraints.size());

        for (int index = 0; index < constraints.size(); index++) {
            ConstraintViolation<?> constraint = constraints.get(index);
            OriginalViolation location = index < original.size() ? original.get(index) : null;
            Annotation annotation = constraint.getConstraintDescriptor().getAnnotation();
            ApiViolationCode code = ApiViolationCode.from(annotation);
            Map<String, Object> parameters = constraintParameters(annotation);
            violations.add(new ApiViolation(
                    location == null ? constraint.getPropertyPath().toString() : location.field(),
                    locationIn(location),
                    code,
                    parameters,
                    new MessageDescriptor(code.messageKey(), parameters, constraint.getMessage())));
        }

        return HttpProblem.builder(problem)
                .with("code", VALIDATION_FAILED)
                .with("violations", violations)
                .build();
    }

    /**
     * Reads back the violations the library already put on the problem.
     *
     * <p>This is the one value read back out of a problem that cannot be matched by name alone, because it is an
     * object rather than a code, so the elements are converted into {@link OriginalViolation} without naming
     * {@code Violation} in a cast. When the problem was built by an instance from another classloader — see the
     * class javadoc — the elements are that classloader's {@code Violation}, and casting to ours throws while
     * reading its two public fields by name does not.</p>
     */
    private static List<OriginalViolation> readOriginalViolations(HttpProblem problem) {
        if (!(problem.getParameters().get("violations") instanceof List<?> violations) || violations.isEmpty()) {
            return List.of();
        }

        List<OriginalViolation> original = new ArrayList<>(violations.size());
        for (Object violation : violations) {
            original.add(toOriginalViolation(violation));
        }
        return original;
    }

    private static OriginalViolation toOriginalViolation(Object violation) {
        if (violation == null) {
            return null;
        }
        if (violation instanceof Violation own) {
            return new OriginalViolation(own.field, own.in);
        }
        return new OriginalViolation(readStringField(violation, "field"), readStringField(violation, "in"));
    }

    private static String readStringField(Object violation, String name) {
        try {
            Object value = violation.getClass().getField(name).get(violation);
            return value instanceof String text ? text : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static String locationIn(OriginalViolation location) {
        if (location == null || location.in() == null || "?".equals(location.in())) {
            return UNKNOWN_LOCATION;
        }
        return location.in();
    }

    /**
     * Builds a readable instance reference, e.g. {@code /api/v1/trips/42}.
     *
     * <p>Deliberately not {@code InstanceUtils.pathToInstance}: in the pinned quarkus-http-problem 3.33.1 that
     * runs the path through {@code encodeUnwiseCharacters}, which percent-encodes "/" and collapses every instance
     * into one opaque segment ({@code api%2Fv1%2Ftrips%2F42}). The main branch has since changed to a component
     * constructor that does not do this, so this can be dropped once the dependency is upgraded.</p>
     */
    private static URI instanceUri(String path) {
        String absolutePath = path.startsWith("/") ? path : "/" + path;
        try {
            return new URI(null, null, absolutePath, null);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static Map<String, Object> constraintParameters(Annotation annotation) {
        if (annotation instanceof Size size) {
            return Map.of("min", size.min(), "max", size.max());
        }
        if (annotation instanceof Min min) {
            return Map.of("min", min.value());
        }
        if (annotation instanceof Max max) {
            return Map.of("max", max.value());
        }
        if (annotation instanceof DecimalMin min) {
            return Map.of("min", min.value(), "inclusive", min.inclusive());
        }
        if (annotation instanceof DecimalMax max) {
            return Map.of("max", max.value(), "inclusive", max.inclusive());
        }
        if (annotation instanceof Digits digits) {
            return Map.of("integer", digits.integer(), "fraction", digits.fraction());
        }
        if (annotation instanceof Pattern pattern) {
            return Map.of("pattern", pattern.regexp());
        }
        return Map.of();
    }
}
