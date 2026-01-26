package org.github.tess1o.geopulse.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FunctionCall(
        String name,
        String arguments
) {
}
