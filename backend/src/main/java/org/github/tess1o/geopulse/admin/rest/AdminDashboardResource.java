package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.AdminDashboardResponse;
import org.github.tess1o.geopulse.prometheus.UserMetrics;
import org.github.tess1o.geopulse.prometheus.GpsPointsMetrics;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.weather.service.WeatherStatusService;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;
import org.github.tess1o.geopulse.integration.service.ExternalIntegrationHealthService;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingWorker;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource for admin dashboard statistics.
 */
@Path("/admin/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
@Slf4j
@Tag(name = "Admin: Dashboard", description = "Read administrator dashboard metrics and system statistics.")
public class AdminDashboardResource {

    @Inject
    UserMetrics userMetrics;

    @Inject
    GpsPointsMetrics gpsPointsMetrics;

    @Inject
    WeatherStatusService weatherStatusService;

    @Inject AdminFullBackupService backupService;
    @Inject GpsPointRepository gpsPointRepository;
    @Inject TimelineJobProgressService timelineJobProgressService;
    @Inject ReverseGeocodingManagementService reverseGeocodingManagementService;
    @Inject MapMatchingConfiguration mapMatchingConfiguration;
    @Inject MapMatchingWorker mapMatchingWorker;
    @Inject ExternalIntegrationHealthService integrationHealthService;

    /**
     * Get dashboard statistics
     *
     * @return Dashboard statistics including user and GPS metrics
     */
    @GET
    public AdminDashboardResponse getDashboardStats() {
        log.debug("Metrics status - User: {}, GPS: {}", userMetrics.isEnabled(), gpsPointsMetrics.isEnabled());
        AdminDashboardResponse.BackupHealth backup = backupHealth();
        Instant latestReceived = gpsPointRepository.findLatestReceived()
                .map(point -> point.getCreatedAt()).orElse(null);
        List<AdminDashboardResponse.SecurityWarningCode> warnings = new ArrayList<>();
        if (!backup.scheduled()) {
            warnings.add(AdminDashboardResponse.SecurityWarningCode.SCHEDULED_BACKUPS_DISABLED);
        }

        return new AdminDashboardResponse(
                userMetrics.getTotalUsersCount(),
                userMetrics.getActiveUsersLast24h(),
                gpsPointsMetrics.getTotalGpsPoints(),
                gpsPointsMetrics.getGpsPointsLast24h(),
                weatherStatusService.status(),
                new AdminDashboardResponse.Health(
                        backup,
                        new AdminDashboardResponse.IngestionHealth(latestReceived, gpsPointsMetrics.getGpsPointsLast24h()),
                        reverseGeocodingManagementService.getProviderHealth(),
                        mapMatchingHealth(),
                        timelineJobProgressService.getStatistics(),
                        new AdminDashboardResponse.SecurityHealth(warnings)),
                new AdminDashboardResponse.MetricsEnabled(userMetrics.isEnabled(), gpsPointsMetrics.isEnabled()));
    }

    private AdminDashboardResponse.BackupHealth backupHealth() {
        try {
            var config = backupService.getConfig();
            Instant latestBackup = backupService.getLatestLocalBackupAt();
            boolean stale = config.getHealthMaxAgeDays() > 0 && latestBackup != null
                    && latestBackup.isBefore(Instant.now().minus(Duration.ofDays(config.getHealthMaxAgeDays())));
            return new AdminDashboardResponse.BackupHealth(
                    config.isScheduledEnabled(), latestBackup, config.getHealthMaxAgeDays(), stale);
        } catch (Exception e) {
            log.warn("Backup health is unavailable", e);
            return new AdminDashboardResponse.BackupHealth(false, null, 0, false);
        }
    }

    private AdminDashboardResponse.MapMatchingHealth mapMatchingHealth() {
        String provider = mapMatchingConfiguration.provider();
        return new AdminDashboardResponse.MapMatchingHealth(
                mapMatchingConfiguration.isEnabled(),
                mapMatchingConfiguration.valhallaConfigured(),
                provider,
                integrationHealthService.findCurrentHealth(ExternalIntegrationType.MAP_MATCHING, provider),
                mapMatchingWorker.status());
    }
}
