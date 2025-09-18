package org.github.tess1o.geopulse.streaming.exception;

import java.util.UUID;

/**
 * Exception thrown when timeline generation cannot proceed due to an existing lock.
 * This indicates that timeline generation is already in progress for the same user.
 */
public class TimelineGenerationLockException extends RuntimeException {

    private final UUID userId;

    public TimelineGenerationLockException(UUID userId) {
        super("Timeline regeneration already in progress for user " + userId);
        this.userId = userId;
    }

    public TimelineGenerationLockException(UUID userId, String message) {
        super(message);
        this.userId = userId;
    }

    public TimelineGenerationLockException(UUID userId, String message, Throwable cause) {
        super(message, cause);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}