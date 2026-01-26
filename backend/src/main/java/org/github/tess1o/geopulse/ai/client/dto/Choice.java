package org.github.tess1o.geopulse.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Choice(
        int index,
        ChatMessage message,
        @JsonProperty("finish_reason") String finishReason
) {
}
