package org.github.tess1o.geopulse.shared.api;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import io.quarkiverse.httpproblem.postprocessing.ProblemPostProcessor;
import io.quarkiverse.httpproblem.validation.HttpValidationProblem;
import io.quarkiverse.httpproblem.validation.Violation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.VALIDATION_FAILED;

@ApplicationScoped
public class GeoPulseProblemPostProcessor implements ProblemPostProcessor {

    @Override
    public HttpProblem apply(HttpProblem problem, ProblemContext context) {
        if (context.cause instanceof ConstraintViolationException exception) {
            return validationProblem(problem, exception);
        }

        if (problem.getParameters().containsKey("code")) {
            return problem;
        }

        ApiErrorCode code = problem instanceof HttpValidationProblem || problem.getParameters().containsKey("violations")
                ? VALIDATION_FAILED
                : ApiErrorCode.forStatus(problem.getStatusCode());
        return HttpProblem.builder(problem).with("code", code).build();
    }

    @SuppressWarnings("unchecked")
    private static HttpProblem validationProblem(HttpProblem problem, ConstraintViolationException exception) {
        List<Violation> original = problem.getParameters().get("violations") instanceof List<?> violations
                ? (List<Violation>) violations
                : List.of();
        List<ConstraintViolation<?>> constraints = new ArrayList<>(exception.getConstraintViolations());
        List<ApiViolation> violations = new ArrayList<>(constraints.size());

        for (int index = 0; index < constraints.size(); index++) {
            ConstraintViolation<?> constraint = constraints.get(index);
            Violation location = index < original.size() ? original.get(index) : null;
            Annotation annotation = constraint.getConstraintDescriptor().getAnnotation();
            violations.add(new ApiViolation(
                    location == null ? constraint.getPropertyPath().toString() : location.field,
                    location == null ? "unknown" : location.in,
                    ApiViolationCode.from(annotation),
                    constraintParameters(annotation),
                    constraint.getMessage()));
        }

        return HttpProblem.builder(problem)
                .with("code", VALIDATION_FAILED)
                .with("violations", violations)
                .build();
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
        if (annotation instanceof Pattern pattern) {
            return Map.of("pattern", pattern.regexp());
        }
        return Map.of();
    }
}
