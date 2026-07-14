package org.github.tess1o.geopulse.streaming.jobs;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.Identifier;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.streaming.service.TimelineRegenerationCampaignService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

@ApplicationScoped
@Slf4j
public class TimelineRegenerationCampaignJob {

    @Inject
    TimelineRegenerationCampaignService campaignService;

    @Inject
    @Identifier("timeline-processing")
    ExecutorService executorService;

    @ConfigProperty(name = "geopulse.timeline.regeneration-campaign.max-concurrent-tasks", defaultValue = "1")
    @StaticInitSafe
    int maxConcurrentTasks;

    @ConfigProperty(name = "geopulse.timeline.regeneration-campaign.max-attempts", defaultValue = "5")
    @StaticInitSafe
    int maxAttempts;

    private Semaphore semaphore;

    @PostConstruct
    void init() {
        semaphore = new Semaphore(Math.max(1, maxConcurrentTasks));
        log.info("Initialized TimelineRegenerationCampaignJob with max {} concurrent tasks and {} attempts",
                maxConcurrentTasks, maxAttempts);
    }

    @Blocking
    @Scheduled(
            every = "${geopulse.timeline.regeneration-campaign.interval:1m}",
            delayed = "${geopulse.timeline.regeneration-campaign.delay:1m}",
            identity = "timeline-regeneration-campaign"
    )
    public void processCampaigns() {
        try {
            campaignService.recoverStaleRunningCampaignUsers();
            campaignService.reconcileActiveCampaigns();
        } catch (Exception e) {
            log.error("Failed to reconcile forced timeline regeneration campaigns", e);
            return;
        }

        int availableWorkers = semaphore.availablePermits();
        for (int i = 0; i < availableWorkers; i++) {
            CompletableFuture.runAsync(() -> {
                if (!semaphore.tryAcquire()) {
                    return;
                }
                try {
                    campaignService.processNextDueCampaignUser(maxAttempts);
                } catch (Exception e) {
                    log.error("Failed to process forced timeline regeneration campaign work", e);
                } finally {
                    semaphore.release();
                }
            }, executorService);
        }
    }
}
