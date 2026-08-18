package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.weather.dto.WeatherRunSummary;
import org.github.tess1o.geopulse.weather.dto.WeatherTargetQueueResponse;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.github.tess1o.geopulse.weather.service.WeatherProcessingCoordinator;
import org.github.tess1o.geopulse.weather.service.WeatherService;

/**
 * Periodically creates at most one current weather target per active user from the latest
 * active stay or trip. This job only discovers targets; provider calls are handled by
 * {@link WeatherSampleFetchJob}.
 */
@ApplicationScoped
@Slf4j
public class WeatherOngoingDiscoveryJob {

    @Inject
    WeatherProcessingCoordinator weatherProcessingCoordinator;

    @Inject
    WeatherService weatherService;

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @RunOnVirtualThread
    @Scheduled(every = "${geopulse.weather.ongoing.job.interval:15m}", delayed = "${geopulse.weather.ongoing.job.delay:2m}")
    public void discoverOngoingWeatherTargets() {
        long startedAtNanos = metricsStart();
        String result = "success";
        if (!configurationService.isEnabled()) {
            log.info("Weather ongoing discovery job skipped: weather is disabled");
            recordJob(startedAtNanos, "skipped");
            return;
        }
        if (!configurationService.ongoingEnabled()) {
            log.info("Weather ongoing discovery job skipped: weather ongoing is disabled");
            recordJob(startedAtNanos, "skipped");
            return;
        }

        log.info("Weather ongoing discovery job triggered");
        try {
            if (weatherProcessingCoordinator == null) {
                WeatherTargetQueueResponse response = weatherService.discoverOngoingTargets();
                log.info("Weather ongoing discovery job completed: created={}, known={}, skipped={}",
                        response.getTargetsCreated(), response.getTargetsAlreadyKnown(), response.getTargetsSkipped());
            } else {
                WeatherRunSummary response = weatherProcessingCoordinator.processOngoingDiscovery("scheduled ongoing discovery");
                log.info("Weather ongoing discovery job completed: created={}, known={}, skipped={}",
                        response.getTargetsCreated(), response.getTargetsAlreadyKnown(), response.getTargetsSkipped());
            }
        } catch (Exception e) {
            result = "error";
            log.error("Weather ongoing discovery job failed: {}", e.getMessage(), e);
        } finally {
            recordJob(startedAtNanos, result);
        }
    }

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordJob(long startedAtNanos, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.weather.job.duration", startedAtNanos,
                "component", "weather",
                "job", "ongoing_discovery",
                "trigger", "scheduled",
                "result", result);
    }
}
