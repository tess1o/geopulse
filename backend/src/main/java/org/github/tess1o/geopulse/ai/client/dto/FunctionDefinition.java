package org.github.tess1o.geopulse.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FunctionDefinition(
        String type,
        @JsonProperty("function") FunctionSpec function
) {
}
