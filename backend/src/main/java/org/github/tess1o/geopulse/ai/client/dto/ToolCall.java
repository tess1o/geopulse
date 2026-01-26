package org.github.tess1o.geopulse.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCall(
        String id,
        String type,
        @JsonProperty("function") FunctionCall function
) {
}
