package org.github.tess1o.geopulse.streaming.jobs;

import io.quarkus.scheduler.Scheduled;
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
import java.util.concurrent.Executors;

@ApplicationScoped
@Slf4j
public class RealTimeTimelineJob {

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @ConfigProperty(name = "geopulse.timeline.processing.thread-pool-size", defaultValue = "2")
    int threadPoolSize;

    private ExecutorService executorService;

    @PostConstruct
    void initThreadPool() {
        executorService = Executors.newFixedThreadPool(threadPoolSize);
        log.info("Initialized RealTimeTimelineJob thread pool with {} threads", threadPoolSize);
    }

    @PreDestroy
    void shutdownThreadPool() {
        if (executorService != null) {
            executorService.shutdown();
            log.info("Shutdown RealTimeTimelineJob thread pool");
        }
    }

    @Scheduled(every = "2m", delay = 2)
    public void processRealTimeUpdates() {
        List<UserEntity> users = UserEntity.list("timelineStatus", TimelineStatus.IDLE);
        log.debug("Starting real-time timeline processing for {} users", users.size());

        for (UserEntity user : users) {
            CompletableFuture.runAsync(() -> processUser(user), executorService)
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
