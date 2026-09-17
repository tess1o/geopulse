package org.github.tess1o.geopulse.ai.client.exception;

public class OpenAiApiException extends RuntimeException {

    public OpenAiApiException(Throwable cause) {
        super(cause);
    }

    public OpenAiApiException(String message) {
        super(message);
    }

    public OpenAiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}