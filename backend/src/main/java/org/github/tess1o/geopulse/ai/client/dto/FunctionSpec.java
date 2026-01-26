package org.github.tess1o.geopulse.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FunctionSpec(
        String name,
        String description,
        Map<String, Object> parameters
) {
}
