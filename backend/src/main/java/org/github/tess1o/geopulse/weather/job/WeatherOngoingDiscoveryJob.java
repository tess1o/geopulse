package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.weather.dto.WeatherTargetQueueResponse;
import org.github.tess1o.geopulse.weather.service.WeatherService;

@ApplicationScoped
@Slf4j
public class WeatherOngoingDiscoveryJob {

    @Inject
    WeatherService weatherService;

    @RunOnVirtualThread
    @Scheduled(every = "${geopulse.weather.ongoing.job.interval:15m}", delayed = "${geopulse.weather.ongoing.job.delay:2m}")
    public void discoverOngoingWeatherTargets() {
        try {
            WeatherTargetQueueResponse response = weatherService.discoverOngoingTargets();
            log.debug("Weather ongoing discovery queued: created={}, known={}, skipped={}",
                    response.getTargetsCreated(), response.getTargetsAlreadyKnown(), response.getTargetsSkipped());
        } catch (Exception e) {
            log.warn("Weather ongoing discovery failed: {}", e.getMessage(), e);
        }
    }
}
