package org.github.tess1o.geopulse.ai.client.exception;

public class ContextLengthExceededException extends RuntimeException {

    public ContextLengthExceededException(String message) {
        super(message);
    }

    public ContextLengthExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
