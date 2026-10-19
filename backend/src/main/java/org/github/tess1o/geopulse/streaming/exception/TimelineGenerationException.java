package org.github.tess1o.geopulse.streaming.exception;

/**
 * Exception thrown when a timeline generation run fails; the cause is the original failure.
 */
public class TimelineGenerationException extends RuntimeException {

    public TimelineGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
