package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.weather.service.WeatherService;

@ApplicationScoped
@Slf4j
public class WeatherTargetCleanupJob {

    @Inject
    WeatherService weatherService;

    @ConfigProperty(name = "geopulse.weather.targets.completed-retention-days", defaultValue = "7")
    int completedRetentionDays;

    @ConfigProperty(name = "geopulse.weather.targets.failed-retention-days", defaultValue = "30")
    int failedRetentionDays;

    @RunOnVirtualThread
    @Scheduled(cron = "${geopulse.weather.target-cleanup.job.cron:0 30 3 * * ?}")
    public void cleanupWeatherTargets() {
        log.info("Weather target cleanup job triggered: completedRetentionDays={}, failedRetentionDays={}",
                completedRetentionDays, failedRetentionDays);
        try {
            long deleted = weatherService.cleanupTargets(completedRetentionDays, failedRetentionDays);
            log.info("Weather target cleanup job completed: deletedTargets={}", deleted);
        } catch (Exception e) {
            log.error("Weather target cleanup job failed: {}", e.getMessage(), e);
        }
    }
}
