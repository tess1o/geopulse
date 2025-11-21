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
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
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
    @Identifier("timeline-processing")
    ExecutorService executorService;

    @ConfigProperty(name = "geopulse.timeline.processing.max-concurrent-tasks", defaultValue = "2")
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
        List<UserEntity> users = UserEntity.list("timelineStatus", TimelineStatus.IDLE);
        log.debug("Starting real-time timeline processing for {} users", users.size());

        for (UserEntity user : users) {
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
    }

    @Transactional
    public void processUser(UserEntity user) {
        timelineGenerationService.generateTimelineFromTimestamp(user.getId(), Instant.now());
    }
}
