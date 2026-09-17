package org.github.tess1o.geopulse.shared.api;

import io.quarkiverse.httpproblem.HttpProblem;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(name = "GeoPulseHttpProblem", description = "RFC 9457 problem details with a stable GeoPulse error code",
        additionalProperties = Schema.True.class)
public final class GeoPulseHttpProblem extends HttpProblem {

    @Schema(description = "Stable machine-readable error code", examples = "EXPORT_NOT_FOUND", required = true)
    public ApiErrorCode code;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Primitive values used to format the error detail")
    public Map<String, Object> parameters;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Validation failures, when applicable")
    public List<ApiViolation> violations;

    private GeoPulseHttpProblem() {
        super(HttpProblem.builder());
    }
}
