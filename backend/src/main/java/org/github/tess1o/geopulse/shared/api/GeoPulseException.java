package org.github.tess1o.geopulse.shared.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class GeoPulseException extends RuntimeException {

    private final ApiErrorCode code;
    private final String detail;
    private final Map<String, Object> parameters;
    private final String errorId;

    public GeoPulseException(ApiErrorCode code, String detail) {
        this(code, detail, Map.of(), null);
    }

    public GeoPulseException(ApiErrorCode code, String detail, Throwable cause) {
        this(code, detail, Map.of(), cause);
    }

    public GeoPulseException(ApiErrorCode code, String detail, Map<String, Object> parameters) {
        this(code, detail, parameters, null);
    }

    public GeoPulseException(ApiErrorCode code, String detail, Map<String, Object> parameters, Throwable cause) {
        super(detail, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.detail = Objects.requireNonNull(detail, "detail");
        this.parameters = parameters == null || parameters.isEmpty() ? Map.of() : Map.copyOf(parameters);
        this.errorId = UUID.randomUUID().toString();
    }

    public ApiErrorCode code() {
        return code;
    }

    public String detail() {
        return detail;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public String errorId() {
        return errorId;
    }
}
