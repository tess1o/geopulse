package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.prometheus.UserMetrics;
import org.github.tess1o.geopulse.prometheus.GpsPointsMetrics;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.weather.service.WeatherStatusService;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;
import org.github.tess1o.geopulse.integration.service.ExternalIntegrationHealthService;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingWorker;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;

import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource for admin dashboard statistics.
 */
@Path("/api/admin/dashboard")
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
    @Inject BackupMaintenanceService backupMaintenanceService;
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
    @Path("/stats")
    public Response getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Log metrics status for debugging
            log.debug("Metrics status - User: {}, GPS: {}",
                    userMetrics.isEnabled(),
                    gpsPointsMetrics.isEnabled());

            // User metrics (queries DB directly if metrics disabled)
            stats.put("totalUsers", userMetrics.getTotalUsersCount());
            stats.put("activeUsers24h", userMetrics.getActiveUsersLast24h());

            // GPS metrics (queries DB directly if metrics disabled)
            stats.put("totalGpsPoints", gpsPointsMetrics.getTotalGpsPoints());
            stats.put("gpsActivity24h", gpsPointsMetrics.getGpsPointsLast24h());
            stats.put("weatherStatus", weatherStatusService.status());
            stats.put("health", health());

            // Add metadata about metrics status
            stats.put("metricsEnabled", Map.of(
                    "user", userMetrics.isEnabled(),
                    "gps", gpsPointsMetrics.isEnabled()
            ));

            log.debug("Dashboard stats retrieved: {}", stats);

            return Response.ok(stats).build();
        } catch (Exception e) {
            log.error("Failed to retrieve dashboard stats", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to retrieve dashboard statistics"))
                    .build();
        }
    }

    private Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        try {
            var config = backupService.getConfig();
            Instant latestBackup = backupService.getLatestLocalBackupAt();
            boolean stale = config.getHealthMaxAgeDays() > 0 && latestBackup != null
                    && latestBackup.isBefore(Instant.now().minus(Duration.ofDays(config.getHealthMaxAgeDays())));
            Map<String, Object> backup = new HashMap<>();
            backup.put("scheduled", config.isScheduledEnabled());
            backup.put("latestBackupAt", latestBackup == null ? "" : latestBackup.toString());
            backup.put("status", backupMaintenanceService.getStatus().getStatus());
            backup.put("healthMaxAgeDays", config.getHealthMaxAgeDays());
            backup.put("stale", stale);
            health.put("backup", backup);
        } catch (Exception e) {
            health.put("backup", Map.of("scheduled", false, "latestBackupAt", "", "status", "unavailable", "healthMaxAgeDays", 0, "stale", false));
        }

        Instant latestReceived = gpsPointRepository.findLatestReceived()
                .map(point -> point.getCreatedAt()).orElse(null);
        health.put("ingestion", Map.of(
                "latestReceivedAt", latestReceived == null ? "" : latestReceived.toString(),
                "pointsLast24h", gpsPointsMetrics.getGpsPointsLast24h()));
        health.put("geocoding", reverseGeocodingManagementService.getProviderHealth());
        health.put("mapMatching", mapMatchingHealth());
        health.put("timeline", timelineJobProgressService.getStatistics());

        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (!backupService.getConfig().isScheduledEnabled()) warnings.add("Scheduled backups are disabled");
        health.put("security", Map.of("warnings", warnings));
        return health;
    }

    private Map<String, Object> mapMatchingHealth() {
        Map<String, Object> health = new HashMap<>();
        String provider = mapMatchingConfiguration.provider();
        health.put("enabled", mapMatchingConfiguration.isEnabled());
        health.put("configured", mapMatchingConfiguration.valhallaConfigured());
        health.put("provider", provider);
        health.put("providerHealth", integrationHealthService.findCurrentHealth(ExternalIntegrationType.MAP_MATCHING, provider));
        health.put("status", mapMatchingWorker.status());
        return health;
    }
}
