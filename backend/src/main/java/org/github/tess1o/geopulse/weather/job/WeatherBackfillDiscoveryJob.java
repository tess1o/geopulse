package org.github.tess1o.geopulse.weather.job;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.weather.dto.WeatherTargetQueueResponse;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.service.WeatherService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@ApplicationScoped
@Slf4j
public class WeatherBackfillDiscoveryJob {

    @Inject
    WeatherService weatherService;

    @Inject
    @Identifier("weather-processing")
    ExecutorService executorService;

    private final AtomicBoolean discoveryRunning = new AtomicBoolean(false);

    void onStartup(@Observes StartupEvent ignored) {
        runKickstartAsync("startup");
    }

    void onWeatherSettingsChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) WeatherSettingsChangedEvent event) {
        runKickstartAsync("weather setting changed: " + event.key());
    }

    void onTimelineDataChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) TimelineDataChangedEvent event) {
        runKickstartAsync(event);
    }

    @RunOnVirtualThread
    @Scheduled(every = "${geopulse.weather.backfill.discovery.job.interval:30m}", delayed = "${geopulse.weather.backfill.discovery.job.delay:5m}")
    public void discoverHistoricalWeatherTargets() {
        discoverHistoricalTargets("scheduled", weatherService::discoverHistoricalBackfillTargets);
    }

    private void runKickstartAsync(String reason) {
        try {
            CompletableFuture.runAsync(() -> runKickstart(reason), executorService)
                    .exceptionally(throwable -> {
                        log.warn("Weather backfill discovery kickstart failed on {}: {}", reason, throwable.getMessage(), throwable);
                        return null;
                    });
        } catch (RuntimeException e) {
            log.warn("Failed to submit weather backfill discovery kickstart on {}: {}", reason, e.getMessage(), e);
        }
    }

    private void runKickstartAsync(TimelineDataChangedEvent event) {
        try {
            CompletableFuture.runAsync(() -> runKickstart(event), executorService)
                    .exceptionally(throwable -> {
                        log.warn("Weather backfill discovery kickstart failed after timeline change {}: {}",
                                event, throwable.getMessage(), throwable);
                        return null;
                    });
        } catch (RuntimeException e) {
            log.warn("Failed to submit weather backfill discovery kickstart after timeline change {}: {}",
                    event, e.getMessage(), e);
        }
    }

    private void runKickstart(String reason) {
        DiscoveryOutcome outcome = discoverHistoricalTargets(reason, weatherService::discoverHistoricalBackfillTargets);
        if (!shouldFetchAfterDiscovery(outcome)) {
            return;
        }

        int fetched = weatherService.fetchQueuedSamples();
        log.info("Weather backfill kickstart fetched {} queued samples after {}", fetched, reason);
    }

    private void runKickstart(TimelineDataChangedEvent event) {
        String reason = "timeline data changed for user " + event.getUserId()
                + " from " + event.getAffectedFrom()
                + " to " + event.getAffectedTo();
        DiscoveryOutcome outcome = discoverHistoricalTargets(
                reason,
                () -> weatherService.discoverHistoricalBackfillTargets(
                        event.getUserId(),
                        event.getAffectedFrom(),
                        event.getAffectedTo()
                )
        );
        if (!shouldFetchAfterDiscovery(outcome)) {
            return;
        }

        int fetched = weatherService.fetchQueuedSamples();
        log.info("Weather backfill kickstart fetched {} queued samples after {}", fetched, reason);
    }

    private DiscoveryOutcome discoverHistoricalTargets(String reason, Supplier<WeatherTargetQueueResponse> discovery) {
        if (!discoveryRunning.compareAndSet(false, true)) {
            log.info("Weather backfill discovery is already running, skipping {}", reason);
            return null;
        }

        try {
            long resetFailedTargets = weatherService.resetStaleFailedTargetsForRetry();
            if (resetFailedTargets > 0) {
                log.info("Weather backfill discovery reset {} stale failed targets on {}", resetFailedTargets, reason);
            }

            WeatherTargetQueueResponse response = discovery.get();
            if (response.getTargetsCreated() > 0 || response.getTargetsAlreadyKnown() > 0) {
                log.info("Weather backfill discovery completed on {}: created={}, known={}, skipped={}",
                        reason, response.getTargetsCreated(), response.getTargetsAlreadyKnown(), response.getTargetsSkipped());
            } else {
                log.debug("Weather backfill discovery completed on {}: created={}, known={}, skipped={}",
                        reason, response.getTargetsCreated(), response.getTargetsAlreadyKnown(), response.getTargetsSkipped());
            }
            return new DiscoveryOutcome(response, resetFailedTargets);
        } catch (Exception e) {
            log.warn("Weather backfill discovery failed on {}: {}", reason, e.getMessage(), e);
            return null;
        } finally {
            discoveryRunning.set(false);
        }
    }

    private boolean shouldFetchAfterDiscovery(DiscoveryOutcome outcome) {
        return outcome != null
                && (outcome.resetFailedTargets() > 0
                || (outcome.response() != null
                && (outcome.response().getTargetsCreated() > 0
                || outcome.response().getTargetsAlreadyKnown() > 0)));
    }

    private record DiscoveryOutcome(WeatherTargetQueueResponse response, long resetFailedTargets) {
    }
}
