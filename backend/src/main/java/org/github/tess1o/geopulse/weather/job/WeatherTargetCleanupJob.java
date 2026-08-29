package org.github.tess1o.geopulse.weather.job;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.github.tess1o.geopulse.weather.service.WeatherService;

/**
 * Deletes terminal weather target queue records after their configured retention period.
 * Stored weather samples are not deleted by this job.
 */
@ApplicationScoped
@Slf4j
public class WeatherTargetCleanupJob {

    @Inject
    WeatherService weatherService;

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @ConfigProperty(name = "geopulse.weather.targets.completed-retention-days", defaultValue = "7")
    int completedRetentionDays;

    @ConfigProperty(name = "geopulse.weather.targets.failed-retention-days", defaultValue = "30")
    int failedRetentionDays;

    @RunOnVirtualThread
    @Scheduled(cron = "${geopulse.weather.target-cleanup.job.cron:0 30 3 * * ?}")
    public void cleanupWeatherTargets() {
        long startedAtNanos = metricsStart();
        String result = "success";
        if (!configurationService.isEnabled()) {
            log.info("Weather target cleanup job skipped: weather is disabled");
            recordJob(startedAtNanos, "skipped");
            return;
        }

        int completedRetentionDays = configurationService.completedTargetRetentionDays();
        int failedRetentionDays = configurationService.failedTargetRetentionDays();
        log.info("Weather target cleanup job triggered: completedRetentionDays={}, failedRetentionDays={}",
                completedRetentionDays, failedRetentionDays);
        try {
            long deleted = weatherService.cleanupTargets(completedRetentionDays, failedRetentionDays);
            log.info("Weather target cleanup job completed: deletedTargets={}", deleted);
        } catch (Exception e) {
            result = "error";
            log.error("Weather target cleanup job failed: {}", e.getMessage(), e);
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
                "job", "cleanup",
                "trigger", "scheduled",
                "result", result);
    }
}
