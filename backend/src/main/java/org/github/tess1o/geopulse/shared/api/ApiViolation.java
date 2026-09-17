package org.github.tess1o.geopulse.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiViolation(
        @Schema(description = "Invalid request field") String field,
        @Schema(description = "Request location containing the field") String in,
        @Schema(description = "Stable machine-readable validation code") ApiViolationCode code,
        @Schema(description = "Primitive values used to format the validation message") Map<String, Object> parameters,
        @Schema(description = "English fallback detail") String detail) {
}
