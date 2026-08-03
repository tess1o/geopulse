package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
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
    WeatherService weatherService;

    @Inject
    WeatherConfigurationService configurationService;

    @RunOnVirtualThread
    @Scheduled(
            every = "${geopulse.weather.sample-fetch.job.interval:10m}",
            delayed = "${geopulse.weather.sample-fetch.job.delay:3m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    public void fetchWeatherSamples() {
        if (!configurationService.isEnabled()) {
            log.info("Weather sample fetch job skipped: weather is disabled");
            return;
        }

        log.info("Weather sample fetch job triggered");
        try {
            int processed = weatherService.fetchQueuedSamples();
            log.info("Weather sample fetch job completed: processedTargets={}", processed);
        } catch (Exception e) {
            log.error("Weather sample fetch job failed: {}", e.getMessage(), e);
        }
    }
}
