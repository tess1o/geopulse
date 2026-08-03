package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
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
    WeatherService weatherService;

    @Inject
    WeatherConfigurationService configurationService;

    @RunOnVirtualThread
    @Scheduled(
            every = "${geopulse.weather.health.probe.job.interval:10m}",
            delayed = "${geopulse.weather.health.probe.job.delay:4m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    public void probeWeatherProviderHealth() {
        if (!configurationService.isEnabled()) {
            log.info("Weather provider health probe job skipped: weather is disabled");
            return;
        }

        log.info("Weather provider health probe job triggered");
        try {
            boolean restored = weatherService.probeProviderHealth();
            if (restored) {
                log.info("Weather provider health probe restored provider health");
            }
            log.info("Weather provider health probe job completed: restored={}", restored);
        } catch (Exception e) {
            log.error("Weather provider health probe job failed: {}", e.getMessage(), e);
        }
    }
}
