package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.weather.service.WeatherService;

@ApplicationScoped
@Slf4j
public class WeatherProviderHealthProbeJob {

    @Inject
    WeatherService weatherService;

    @RunOnVirtualThread
    @Scheduled(
            every = "${geopulse.weather.health.probe.job.interval:10m}",
            delayed = "${geopulse.weather.health.probe.job.delay:4m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    public void probeWeatherProviderHealth() {
        try {
            boolean restored = weatherService.probeProviderHealth();
            if (restored) {
                log.info("Weather provider health probe restored provider health");
            }
        } catch (Exception e) {
            log.warn("Weather provider health probe failed: {}", e.getMessage(), e);
        }
    }
}
