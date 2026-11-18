package org.github.tess1o.geopulse.streaming.edits;

/**
 * The user-facing status of a correction. The stored statuses stay {@link TimelineEditStatus#ACTIVE},
 * {@link TimelineEditStatus#SUPERSEDED} and {@link TimelineEditStatus#REVERTED}; this enum is what the API and the
 * history report.
 */
public enum TimelineCorrectionStatus {
    IN_EFFECT,
    UNDONE,
    REPLACED;

    public static TimelineCorrectionStatus of(TimelineEditStatus status) {
        return switch (status) {
            case ACTIVE -> IN_EFFECT;
            case REVERTED -> UNDONE;
            case SUPERSEDED -> REPLACED;
        };
    }
}
