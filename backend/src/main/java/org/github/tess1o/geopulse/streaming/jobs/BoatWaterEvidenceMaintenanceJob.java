package org.github.tess1o.geopulse.streaming.jobs;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.streaming.service.boat.BoatSetupService;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class BoatWaterEvidenceMaintenanceJob {

    @Inject
    GpsPointEnvironmentService gpsPointEnvironmentService;

    @Inject
    BoatSetupService boatSetupService;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "geopulse.boat.water-evidence.maintenance.max-users-per-run", defaultValue = "25")
    int maxUsersPerRun;

    @Scheduled(every = "${geopulse.boat.water-evidence.maintenance.interval:15m}")
    @RunOnVirtualThread
    public void repairMissingBoatWaterEvidence() {
        String datasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
        if (datasetVersion == null) {
            return;
        }

        List<UUID> userIds = entityManager.createNativeQuery("""
                        SELECT gp.user_id
                        FROM gps_points gp
                        JOIN users u ON u.id = gp.user_id
                        LEFT JOIN gps_point_environment env ON env.gps_point_id = gp.id
                        WHERE u.timeline_preferences ->> 'boatEnabled' = 'true'
                          AND gp.coordinates IS NOT NULL
                          AND (
                              env.gps_point_id IS NULL
                              OR env.environment_dataset_version <> :datasetVersion
                          )
                        GROUP BY gp.user_id
                        ORDER BY MIN(gp.timestamp) ASC
                        LIMIT :maxUsersPerRun
                        """)
                .setParameter("datasetVersion", datasetVersion)
                .setParameter("maxUsersPerRun", Math.max(1, maxUsersPerRun))
                .getResultList()
                .stream()
                .map(UUID.class::cast)
                .toList();

        for (UUID userId : userIds) {
            try {
                boatSetupService.ensureReadyForEnabledUserInBackground(userId);
            } catch (Exception e) {
                log.warn("Failed to repair Boat water evidence for user {}: {}", userId, e.getMessage());
            }
        }
    }
}
