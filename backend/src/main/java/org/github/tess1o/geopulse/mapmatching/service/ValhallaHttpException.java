package org.github.tess1o.geopulse.mapmatching.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValhallaHttpException extends RuntimeException {

    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("\\\"error_code\\\"\\s*:\\s*(\\d+)");

    private final int httpStatus;
    private final Integer errorCode;
    private final String responseBody;

    public ValhallaHttpException(int httpStatus, String responseBody, Throwable cause) {
        super(message(httpStatus, responseBody), cause);
        this.httpStatus = httpStatus;
        this.errorCode = extractErrorCode(responseBody);
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        return httpStatus <= 0 || httpStatus == 408 || httpStatus == 429 || httpStatus >= 500;
    }

    private static String message(int httpStatus, String responseBody) {
        return "Valhalla trace_route failed with HTTP " + httpStatus
                + (responseBody == null || responseBody.isBlank() ? "" : ": " + responseBody);
    }

    private static Integer extractErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        Matcher matcher = ERROR_CODE_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
