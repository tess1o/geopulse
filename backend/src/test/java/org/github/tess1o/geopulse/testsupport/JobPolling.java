package org.github.tess1o.geopulse.testsupport;

import org.github.tess1o.geopulse.geocoding.model.ReconciliationJobProgress;
import org.github.tess1o.geopulse.geocoding.service.ReconciliationJobProgressService;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class JobPolling {

    private JobPolling() {
    }

    public static ReconciliationJobProgress awaitTerminal(
            ReconciliationJobProgressService progressService, UUID jobId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            ReconciliationJobProgress job = progressService.getJobProgress(jobId).orElse(null);
            if (job != null && job.isTerminal()) {
                return job;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for job " + jobId, exception);
            }
        }
        throw new AssertionError("Job did not finish: " + jobId);
    }
}
