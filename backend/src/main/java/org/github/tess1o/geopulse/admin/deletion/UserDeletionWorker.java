package org.github.tess1o.geopulse.admin.deletion;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;

import java.time.Duration;

/**
 * Works off requested user deletions, one short transaction per batch (see {@link UserDeletionSteps}). Every instance
 * runs it; the per-step row lock keeps them from working on the same deletion.
 */
@ApplicationScoped
@Slf4j
public class UserDeletionWorker {

    private final UserDeletionSteps steps;

    @Inject
    BackupMaintenanceService backupMaintenanceService;

    @ConfigProperty(name = "geopulse.user-deletion.max-attempts", defaultValue = "5")
    int maxAttempts;

    @ConfigProperty(name = "geopulse.user-deletion.retry-delay", defaultValue = "60s")
    Duration retryDelay;

    @Inject
    public UserDeletionWorker(UserDeletionSteps steps) {
        this.steps = steps;
    }

    @Scheduled(every = "${geopulse.user-deletion.worker.interval:5s}", delayed = "30s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "user-deletion-worker")
    void scheduled() {
        drain();
    }

    /** Runs steps until no deletion is due. Returns the number of steps that did work. */
    public int drain() {
        int worked = 0;
        while (!restoreBlocked() && step()) {
            worked++;
        }
        return worked;
    }

    /** Runs one step of the next due deletion. Returns false when there was nothing to do. */
    public boolean step() {
        try {
            return steps.runNext();
        } catch (UserDeletionSteps.StepFailedException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.debug("User deletion step failed", e);
            steps.recordFailure(e.userId(), e.revision(), describe(cause), maxAttempts, retryDelay);
            return true;
        } catch (RuntimeException e) {
            // Failed outside a step (claiming, committing): nothing to record, the next run tries again.
            log.error("User deletion worker failed: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean restoreBlocked() {
        return backupMaintenanceService != null && backupMaintenanceService.isRestoreBlocked();
    }

    /** The most specific message in the chain: the database error rather than the wrappers around it. */
    private static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && !root.getCause().equals(root)) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
