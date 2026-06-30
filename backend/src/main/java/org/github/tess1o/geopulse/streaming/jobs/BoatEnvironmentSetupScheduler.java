package org.github.tess1o.geopulse.streaming.jobs;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.service.boat.BoatSetupService;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class BoatEnvironmentSetupScheduler {

    @Inject
    GpsPointEnvironmentService gpsPointEnvironmentService;

    @Inject
    BoatSetupService boatSetupService;

    @Inject
    EntityManager entityManager;

    @Scheduled(every = "15m")
    @RunOnVirtualThread
    public void enrichMissingBoatEnvironmentEvidence() {
        String datasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
        if (datasetVersion == null) {
            return;
        }

        List<UUID> userIds = entityManager.createNativeQuery("""
                        SELECT id
                        FROM users
                        WHERE timeline_preferences ->> 'boatEnabled' = 'true'
                        LIMIT 5
                        """)
                .getResultList()
                .stream()
                .map(UUID.class::cast)
                .toList();

        for (UUID userId : userIds) {
            try {
                if (gpsPointEnvironmentService.countMissingOrStale(
                        userId,
                        GpsPointEnvironmentService.DEFAULT_START_DATE,
                        datasetVersion) > 0) {
                    boatSetupService.ensureReadyForEnabledUserInBackground(userId);
                }
            } catch (Exception e) {
                log.warn("Failed to enrich background Boat environment evidence for user {}: {}",
                        userId, e.getMessage());
            }
        }
    }
}
