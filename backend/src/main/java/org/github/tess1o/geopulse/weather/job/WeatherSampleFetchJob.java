package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.weather.service.WeatherService;

@ApplicationScoped
@Slf4j
public class WeatherSampleFetchJob {

    @Inject
    WeatherService weatherService;

    @RunOnVirtualThread
    @Scheduled(
            every = "${geopulse.weather.sample-fetch.job.interval:10m}",
            delayed = "${geopulse.weather.sample-fetch.job.delay:3m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    public void fetchWeatherSamples() {
        try {
            int processed = weatherService.fetchQueuedSamples();
            log.debug("Weather sample fetch completed: {} targets processed", processed);
        } catch (Exception e) {
            log.warn("Weather sample fetch failed: {}", e.getMessage(), e);
        }
    }
}
