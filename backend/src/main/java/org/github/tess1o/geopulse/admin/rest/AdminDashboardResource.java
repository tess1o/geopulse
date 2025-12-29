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

import java.util.HashMap;
import java.util.Map;

/**
 * REST resource for admin dashboard statistics.
 */
@Path("/api/admin/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
public class AdminDashboardResource {

    @Inject
    UserMetrics userMetrics;

    @Inject
    GpsPointsMetrics gpsPointsMetrics;

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
}
