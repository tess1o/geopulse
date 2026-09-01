package org.github.tess1o.geopulse.admin.backup;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.UUID;

@RegisterForReflection
public class RestoreState {
    public String operationId = UUID.randomUUID().toString();
    public String state = "PREPARING";
    public String phase = "validating";
    public String fileName;
    public String backupCreatedAt;
    public String originalDatabase;
    public String stagingDatabase;
    public String previousDatabase;
    public long originalOid;
    public long stagingOid;
    public String keyFingerprint;
    public String error;
    public String startedAt = Instant.now().toString();
    public String updatedAt = startedAt;
    public int progress;
    public boolean blocked() { return switch (state) {
        case "ACTIVATING", "SWAPPED_PENDING_RESTART", "ACTIVATION_FAILED" -> true;
        default -> false;
    }; }
    public String message() { return switch (state) {
        case "PREPARING" -> "Restoration is being prepared in the background. GeoPulse remains available, but data and changes newer than this backup will be replaced when restoration activates.";
        case "ACTIVATING" -> "Activating restored data. Please wait.";
        case "SWAPPED_PENDING_RESTART" -> "Restored data was activated. GeoPulse is stopping the backend to complete restoration. A configured supervisor may restart it automatically.";
        case "ACTIVATION_FAILED" -> "Restored data could not be activated. GeoPulse remains unavailable. Administrator action is required.";
        case "ACTIVATION_RETRYABLE" -> "Activation did not complete. The original database remains active and the prepared restore can be retried.";
        case "COMPLETED" -> "Restoration completed. Please sign in again.";
        case "PREPARATION_FAILED" -> "Restoration preparation failed. The original database remains active.";
        case "DISCARDED" -> "Prepared restoration discarded. The original database remains active.";
        default -> "GeoPulse is available.";
    }; }
}
