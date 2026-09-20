package org.github.tess1o.geopulse.shared.api;

import io.quarkiverse.httpproblem.ExceptionMapperBase;
import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.PostProcessorsRegistry;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.Provider;

/**
 * Turns a {@link GeoPulseException} into the GeoPulse problem envelope.
 *
 * <p>Extends {@link ExceptionMapperBase} rather than implementing {@link jakarta.ws.rs.ext.ExceptionMapper}
 * directly: the base class is the only place that runs {@code PostProcessorsRegistry} and the problem logger, so a
 * mapper that built an {@link HttpProblem} and called {@code toResponse()} on it would silently skip both.</p>
 */
@Provider
@Priority(Priorities.USER)
public class GeoPulseExceptionMapper extends ExceptionMapperBase<GeoPulseException> {

    @Inject
    public GeoPulseExceptionMapper(PostProcessorsRegistry postProcessorsRegistry) {
        super(postProcessorsRegistry);
    }

    @Override
    protected HttpProblem toProblem(GeoPulseException exception) {
        return ProblemFactory.from(exception);
    }
}
