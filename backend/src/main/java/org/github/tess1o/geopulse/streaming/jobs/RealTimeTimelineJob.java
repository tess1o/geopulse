package org.github.tess1o.geopulse.streaming.jobs;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.Identifier;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineRegenerationCampaignService;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

@ApplicationScoped
@Slf4j
public class RealTimeTimelineJob {

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    TimelineRegenerationCampaignService campaignService;

    @Inject
    @Identifier("timeline-processing")
    ExecutorService executorService;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @Inject
    BackupMaintenanceService backupMaintenanceService;

    @ConfigProperty(name = "geopulse.timeline.processing.thread-pool-size", defaultValue = "2")
    @StaticInitSafe
    int maxConcurrentTasks;

    private Semaphore semaphore;

    @PostConstruct
    void init() {
        semaphore = new Semaphore(maxConcurrentTasks);
        log.info("Initialized RealTimeTimelineJob with max {} concurrent tasks", maxConcurrentTasks);
    }

    @PreDestroy
    void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
            log.info("Shutdown RealTimeTimelineJob executor service");
        }
    }

    @Blocking
    @Scheduled(every = "${geopulse.timeline.job.interval:5m}", delayed = "${geopulse.timeline.job.delay:5m}")
    public void processRealTimeUpdates() {
        if (backupMaintenanceService != null && backupMaintenanceService.isRestoreBlocked()) {
            log.info("Skipping real-time timeline processing while full backup restore is running");
            return;
        }
        long startedAtNanos = metricsStart();
        List<UserEntity> users = UserEntity.list("timelineStatus", TimelineStatus.IDLE);
        countRealtimeUsers("discovered", users.size());
        log.debug("Starting real-time timeline processing for {} users", users.size());

        for (UserEntity user : users) {
            countRealtimeUsers("submitted", 1);
            CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    processUser(user);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for semaphore for user {}", user.getEmail());
                } finally {
                    semaphore.release();
                }
            }, executorService)
                    .exceptionally(throwable -> {
                        log.error("Failed to process user {}: {}", user.getEmail(), throwable.getMessage(), throwable);
                    return null;
                    });
        }
        recordRealtimeSchedulerDuration(startedAtNanos, "success");
    }

    @Transactional
    public void processUser(UserEntity user) {
        if (campaignService.hasActiveCampaignForUser(user.getId())) {
            log.debug("Skipping real-time timeline processing for user {} due to active forced regeneration campaign",
                    user.getId());
            countRealtimeUsers("skipped_campaign", 1);
            return;
        }
        timelineGenerationService.generateTimelineFromTimestamp(user.getId(), Instant.now(), "realtime");
    }

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordRealtimeSchedulerDuration(long startedAtNanos, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.timeline.realtime.scheduler.duration", startedAtNanos,
                "component", "timeline",
                "trigger", "realtime",
                "result", result);
    }

    private void countRealtimeUsers(String result, long count) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.timeline.realtime.users", count,
                "component", "timeline",
                "trigger", "realtime",
                "result", result);
    }
}
