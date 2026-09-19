package org.github.tess1o.geopulse.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Documentation-only description of the error envelope.
 *
 * <p>OpenAPI generates the {@code ApiProblemResponse} schema from this class, which is why it is the single source
 * of truth for the documented error shape — the response body itself is assembled by {@link ProblemFactory} and
 * {@link GeoPulseProblemPostProcessor} and serialized as an {@code HttpProblem}.</p>
 *
 * <p>It must never be used as a runtime response type: the library's {@code JacksonProblemSerializer} only writes
 * the five RFC fields plus the problem's extension members, so this class's own fields would be dropped.</p>
 */
@Schema(name = "ApiProblemResponse", description = "RFC 9457 problem details with stable GeoPulse extensions",
        additionalProperties = Schema.True.class)
public final class ApiProblemResponse {

    public URI type;
    public String title;
    public int status;
    public String detail;
    public URI instance;

    @Schema(description = "Stable machine-readable error code", examples = "EXPORT_NOT_FOUND", required = true)
    public ApiErrorCode code;

    @Schema(description = "Unique identifier for this error", required = true)
    public String errorId;

    @Schema(description = "Request correlation identifier", required = true)
    public String requestId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Error-code specific parameters, used to format the detail")
    public Map<String, Object> parameters;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Validation failures, when applicable")
    public List<ApiViolation> violations;
}
