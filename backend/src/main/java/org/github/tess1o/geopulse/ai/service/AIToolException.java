package org.github.tess1o.geopulse.ai.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain-specific exception for AI tool execution failures.
 * Produces structured payloads that can be returned directly to the LLM.
 */
public class AIToolException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public AIToolException(String code, String message) {
        this(code, message, Map.of());
    }

    public AIToolException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details != null ? details : Map.of();
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Map<String, Object> toErrorPayload() {
        Map<String, Object> errorPayload = new LinkedHashMap<>();
        errorPayload.put("code", code);
        errorPayload.put("message", getMessage());
        errorPayload.putAll(details);
        return Map.of("error", errorPayload);
    }
}
