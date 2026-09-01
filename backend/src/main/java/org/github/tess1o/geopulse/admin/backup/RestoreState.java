package org.github.tess1o.geopulse.admin.backup;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

@RegisterForReflection
public class RestoreState {
    private static final Map<RestoreOperationState, EnumSet<RestoreOperationState>> ALLOWED_TRANSITIONS = allowedTransitions();
    private static final Map<RestoreOperationState, EnumSet<RestorePhase>> VALID_PHASES = validPhases();

    public String operationId = UUID.randomUUID().toString();
    public RestoreOperationState state = RestoreOperationState.PREPARING;
    public RestorePhase phase = RestorePhase.UPLOAD;
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
    public int progress = RestorePhase.UPLOAD.progress();

    public void transition(RestoreOperationState next, RestorePhase nextPhase, String nextError) {
        if (next == null || nextPhase == null) {
            throw new IllegalArgumentException("Restore state and phase are required");
        }
        if (next != state && !ALLOWED_TRANSITIONS.getOrDefault(state, EnumSet.noneOf(RestoreOperationState.class)).contains(next)) {
            throw new IllegalStateException("Invalid restore transition from " + state + " to " + next);
        }
        if (!VALID_PHASES.getOrDefault(next, EnumSet.noneOf(RestorePhase.class)).contains(nextPhase)) {
            throw new IllegalStateException("Invalid restore phase " + nextPhase + " for state " + next);
        }
        state = next;
        phase = nextPhase;
        progress = nextPhase.progress();
        error = nextError;
    }

    public void updatePhase(RestorePhase nextPhase) {
        if (nextPhase == null || !VALID_PHASES.getOrDefault(state, EnumSet.noneOf(RestorePhase.class)).contains(nextPhase)) {
            throw new IllegalStateException("Invalid restore phase " + nextPhase + " for state " + state);
        }
        if (nextPhase.progress() < progress) {
            throw new IllegalStateException("Restore phase progress cannot move backwards");
        }
        phase = nextPhase;
        progress = nextPhase.progress();
    }

    public void validateDurableShape() {
        UUID.fromString(operationId);
        Instant.parse(startedAt);
        Instant.parse(updatedAt);
        if (state == null || phase == null) {
            throw new IllegalArgumentException("Restore journal state and phase are required");
        }
        if (!VALID_PHASES.getOrDefault(state, EnumSet.noneOf(RestorePhase.class)).contains(phase)
                || progress != phase.progress()) {
            throw new IllegalArgumentException("Restore journal state, phase, and progress are inconsistent");
        }
        if (fileName == null || fileName.isBlank() || fileName.length() > 255
                || !fileName.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Restore journal source file name is invalid");
        }
        if (backupCreatedAt != null) {
            Instant.parse(backupCreatedAt);
        }
        if (EnumSet.of(RestoreOperationState.ACTIVATING, RestoreOperationState.SWAPPED_PENDING_RESTART,
                RestoreOperationState.COMPLETED, RestoreOperationState.ACTIVATION_RETRYABLE,
                RestoreOperationState.ACTIVATION_FAILED).contains(state)) {
            validateDatabaseIdentity(null, true);
        }
    }

    public void validateDatabaseIdentity(String configuredDatabase, boolean requireOids) {
        String suffix = operationId.replace("-", "");
        if (configuredDatabase != null && !configuredDatabase.equals(originalDatabase)) {
            throw new IllegalArgumentException("Original database identity does not match configuration");
        }
        if (!equalsExpected(stagingDatabase, "gp_restore_" + suffix)
                || !equalsExpected(previousDatabase, "gp_previous_" + suffix)) {
            throw new IllegalArgumentException("Restore database names do not match the operation identity");
        }
        if (requireOids && (originalOid <= 0 || stagingOid <= 0 || originalOid == stagingOid)) {
            throw new IllegalArgumentException("Restore database OIDs are invalid");
        }
    }

    private boolean equalsExpected(String value, String expected) {
        return value != null && value.equals(expected);
    }

    public boolean blocked() { return switch (state) {
        case ACTIVATING, SWAPPED_PENDING_RESTART, ACTIVATION_FAILED -> true;
        default -> false;
    }; }
    public String message() { return switch (state) {
        case PREPARING -> "Restoration is being prepared in the background. GeoPulse remains available, but data and changes newer than this backup will be replaced when restoration activates.";
        case ACTIVATING -> "Activating restored data. Please wait.";
        case SWAPPED_PENDING_RESTART -> "Restored data was activated. GeoPulse is stopping the backend to complete restoration. A configured supervisor may restart it automatically.";
        case ACTIVATION_FAILED -> "Restored data could not be activated. GeoPulse remains unavailable. Administrator action is required.";
        case ACTIVATION_RETRYABLE -> "Activation did not complete. The original database remains active and the prepared restore can be retried.";
        case COMPLETED -> "Restoration completed. Please sign in again.";
        case PREPARATION_FAILED -> "Restoration preparation failed. The original database remains active.";
        case DISCARDED -> "Prepared restoration discarded. The original database remains active.";
    }; }

    private static Map<RestoreOperationState, EnumSet<RestoreOperationState>> allowedTransitions() {
        Map<RestoreOperationState, EnumSet<RestoreOperationState>> transitions = new EnumMap<>(RestoreOperationState.class);
        transitions.put(RestoreOperationState.PREPARING, EnumSet.of(RestoreOperationState.ACTIVATING,
                RestoreOperationState.PREPARATION_FAILED, RestoreOperationState.ACTIVATION_RETRYABLE,
                RestoreOperationState.ACTIVATION_FAILED));
        transitions.put(RestoreOperationState.ACTIVATING, EnumSet.of(RestoreOperationState.SWAPPED_PENDING_RESTART,
                RestoreOperationState.COMPLETED, RestoreOperationState.ACTIVATION_RETRYABLE,
                RestoreOperationState.ACTIVATION_FAILED));
        transitions.put(RestoreOperationState.SWAPPED_PENDING_RESTART, EnumSet.of(RestoreOperationState.COMPLETED,
                RestoreOperationState.ACTIVATION_RETRYABLE, RestoreOperationState.ACTIVATION_FAILED));
        transitions.put(RestoreOperationState.ACTIVATION_RETRYABLE, EnumSet.of(RestoreOperationState.ACTIVATING,
                RestoreOperationState.DISCARDED, RestoreOperationState.ACTIVATION_FAILED));
        return transitions;
    }

    private static Map<RestoreOperationState, EnumSet<RestorePhase>> validPhases() {
        Map<RestoreOperationState, EnumSet<RestorePhase>> phases = new EnumMap<>(RestoreOperationState.class);
        phases.put(RestoreOperationState.PREPARING, EnumSet.of(RestorePhase.UPLOAD, RestorePhase.PREFLIGHT,
                RestorePhase.RESTORING, RestorePhase.SECRETS, RestorePhase.VALIDATING));
        phases.put(RestoreOperationState.ACTIVATING, EnumSet.of(RestorePhase.CUTOVER));
        phases.put(RestoreOperationState.SWAPPED_PENDING_RESTART, EnumSet.of(RestorePhase.RESTARTING));
        phases.put(RestoreOperationState.COMPLETED, EnumSet.of(RestorePhase.COMPLETED));
        phases.put(RestoreOperationState.PREPARATION_FAILED, EnumSet.of(RestorePhase.FAILED, RestorePhase.INTERRUPTED));
        phases.put(RestoreOperationState.ACTIVATION_RETRYABLE, EnumSet.of(RestorePhase.ACTIVATION_ROLLED_BACK));
        phases.put(RestoreOperationState.ACTIVATION_FAILED,
                EnumSet.of(RestorePhase.ACTIVATION_FAILED, RestorePhase.IDENTITY_MISMATCH));
        phases.put(RestoreOperationState.DISCARDED, EnumSet.of(RestorePhase.DISCARDED));
        return phases;
    }
}
