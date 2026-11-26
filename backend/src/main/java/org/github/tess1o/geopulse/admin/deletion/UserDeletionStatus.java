package org.github.tess1o.geopulse.admin.deletion;

/**
 * Where a user deletion stands. Every status but {@link #COMPLETED} keeps the user locked out.
 */
public enum UserDeletionStatus {
    /** Requested; the worker has not yet locked the user out of timeline processing. */
    QUEUED,
    /** The worker is deleting the user's data. */
    RUNNING,
    /** The worker gave up after repeated errors; an admin can retry it. The user stays locked out. */
    FAILED,
    /** The user and all of their data are gone. */
    COMPLETED;

    public boolean isPending() {
        return this != COMPLETED;
    }

    /** Whether the worker picks the deletion up. */
    public boolean isWorkable() {
        return this == QUEUED || this == RUNNING;
    }
}
