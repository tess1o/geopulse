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
 * Consumes pending weather targets and stores provider responses as weather samples.
 * Quotas, ongoing capacity reservation, provider health, retry timing, and concurrent
 * claims are enforced by {@code WeatherService} and the target repository.
 */
@ApplicationScoped
@Slf4j
public class WeatherSampleFetchJob {

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
            every = "${geopulse.weather.sample-fetch.job.interval:10m}",
            delayed = "${geopulse.weather.sample-fetch.job.delay:3m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    public void fetchWeatherSamples() {
        long startedAtNanos = metricsStart();
        String result = "success";
        if (!configurationService.isEnabled()) {
            log.info("Weather sample fetch job skipped: weather is disabled");
            recordJob(startedAtNanos, "skipped");
            return;
        }

        log.info("Weather sample fetch job triggered");
        try {
            if (weatherProcessingCoordinator == null) {
                int processed = weatherService.fetchQueuedSamples();
                log.info("Weather sample fetch job completed: processedTargets={}", processed);
            } else {
                WeatherRunSummary summary = weatherProcessingCoordinator.fetchScheduled("scheduled sample fetch");
                log.info("Weather sample fetch job completed: processedTargets={}, result={}, message={}",
                        summary.getFetchedTargets(), summary.getResult(), summary.getMessage());
            }
        } catch (Exception e) {
            result = "error";
            log.error("Weather sample fetch job failed: {}", e.getMessage(), e);
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
                "job", "sample_fetch",
                "trigger", "scheduled",
                "result", result);
    }
}
