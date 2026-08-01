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
        log.info("Weather sample fetch job triggered");
        try {
            int processed = weatherService.fetchQueuedSamples();
            log.info("Weather sample fetch job completed: processedTargets={}", processed);
        } catch (Exception e) {
            log.error("Weather sample fetch job failed: {}", e.getMessage(), e);
        }
    }
}
