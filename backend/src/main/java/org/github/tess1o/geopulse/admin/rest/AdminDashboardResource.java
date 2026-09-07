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
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;

import java.time.Instant;
import java.util.Comparator;
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
            Instant latestBackup = backupService.listLocalBackups().stream()
                    .map(file -> file.getLastModifiedAt())
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            health.put("backup", Map.of(
                    "scheduled", backupService.getConfig().isScheduledEnabled(),
                    "latestBackupAt", latestBackup == null ? "" : latestBackup.toString(),
                    "status", backupMaintenanceService.getStatus().getStatus()));
        } catch (Exception e) {
            health.put("backup", Map.of("scheduled", false, "latestBackupAt", "", "status", "unavailable"));
        }

        Instant latestReceived = gpsPointRepository.findLatestReceived()
                .map(point -> point.getCreatedAt()).orElse(null);
        health.put("ingestion", Map.of(
                "latestReceivedAt", latestReceived == null ? "" : latestReceived.toString(),
                "pointsLast24h", gpsPointsMetrics.getGpsPointsLast24h()));
        health.put("timeline", timelineJobProgressService.getStatistics());

        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (!backupService.getConfig().isScheduledEnabled()) warnings.add("Scheduled backups are disabled");
        health.put("security", Map.of("warnings", warnings));
        return health;
    }
}
