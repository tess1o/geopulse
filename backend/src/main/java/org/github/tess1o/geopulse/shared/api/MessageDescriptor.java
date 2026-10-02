package org.github.tess1o.geopulse.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MessageDescriptor(
        @Schema(description = "Stable frontend translation key") String key,
        @Schema(description = "Primitive values used to format the message") Map<String, Object> parameters,
        @Schema(description = "English fallback text") String fallback) {

    public MessageDescriptor {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
