package org.github.tess1o.geopulse.admin.backup;

public enum RestoreOperationState {
    PREPARING,
    ACTIVATING,
    SWAPPED_PENDING_RESTART,
    COMPLETED,
    PREPARATION_FAILED,
    ACTIVATION_RETRYABLE,
    ACTIVATION_FAILED,
    DISCARDED
}
