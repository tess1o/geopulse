package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.weather.dto.WeatherRunSummary;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.github.tess1o.geopulse.weather.service.WeatherProcessingCoordinator;
import org.github.tess1o.geopulse.weather.service.WeatherService;

/**
 * Attempts an external provider probe only when weather integration health is blocking
 * fetches and its retry time has arrived. A healthy provider makes scheduled runs a local
 * health-state check without an external request.
 */
@ApplicationScoped
@Slf4j
public class WeatherProviderHealthProbeJob {

    @Inject
    WeatherProcessingCoordinator weatherProcessingCoordinator;

    @Inject
    WeatherService weatherService;

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @RunOnVirtualThread
    @Scheduled(
            every = "${geopulse.weather.health.probe.job.interval:10m}",
            delayed = "${geopulse.weather.health.probe.job.delay:4m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    public void probeWeatherProviderHealth() {
        long startedAtNanos = metricsStart();
        String result = "success";
        if (!configurationService.isEnabled()) {
            log.info("Weather provider health probe job skipped: weather is disabled");
            recordJob(startedAtNanos, "skipped");
            return;
        }

        log.info("Weather provider health probe job triggered");
        try {
            boolean restored;
            if (weatherProcessingCoordinator == null) {
                restored = weatherService.probeProviderHealth();
            } else {
                WeatherRunSummary summary = weatherProcessingCoordinator.probeProviderHealth("scheduled health probe");
                restored = "restored".equals(summary.getResult());
            }
            result = restored ? "restored" : "success";
            if (restored) {
                log.info("Weather provider health probe restored provider health");
            }
            log.info("Weather provider health probe job completed: restored={}", restored);
        } catch (Exception e) {
            result = "error";
            log.error("Weather provider health probe job failed: {}", e.getMessage(), e);
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
                "job", "health_probe",
                "trigger", "scheduled",
                "result", result);
    }
}
