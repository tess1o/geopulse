package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;

import java.util.List;

/**
 * Utility service for batch processing operations during imports
 */
@ApplicationScoped
@Slf4j
public class BatchProcessor {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    EntityManager entityManager;

    /**
     * Process a batch of GPS points with proper duplicate detection
     */
    @Transactional
    public int processBatch(List<GpsPointEntity> gpsPoints) {
        if (gpsPoints.isEmpty()) {
            return 0;
        }

        int imported = 0;

        // Use proper spatial+temporal duplicate detection
        for (GpsPointEntity gpsPoint : gpsPoints) {
            try {
                // Check for existing GPS points using spatial+temporal criteria
                var existingPoints = gpsPointRepository.findByUserAndTimestampAndCoordinates(
                        gpsPoint.getUser(), gpsPoint.getTimestamp(), gpsPoint.getCoordinates());

                if (existingPoints.isEmpty()) {
                    // Insert new GPS point
                    gpsPointRepository.persist(gpsPoint);
                    imported++;
                } else {
                    // Update existing GPS point with better data if necessary
                    GpsPointEntity existing = existingPoints.get(0);
                    updateGpsPointIfNecessary(existing, gpsPoint);
                }

            } catch (Exception e) {
                log.warn("Failed to import GPS point at {}", gpsPoint.getTimestamp(), e);
            }
        }

        // Flush periodically to free memory
        if (gpsPoints.size() > 100) {
            entityManager.flush();
            entityManager.clear();
        }

        return imported;
    }

    /**
     * Update existing GPS point with better data if available
     */
    private void updateGpsPointIfNecessary(GpsPointEntity existing, GpsPointEntity newPoint) {
        boolean updated = false;

        // Update fields if they have better/newer data
        if (newPoint.getAccuracy() != null && (existing.getAccuracy() == null || newPoint.getAccuracy() < existing.getAccuracy())) {
            existing.setAccuracy(newPoint.getAccuracy());
            updated = true;
        }

        if (newPoint.getAltitude() != null && existing.getAltitude() == null) {
            existing.setAltitude(newPoint.getAltitude());
            updated = true;
        }

        if (newPoint.getVelocity() != null && existing.getVelocity() == null) {
            existing.setVelocity(newPoint.getVelocity());
            updated = true;
        }

        if (newPoint.getBattery() != null && existing.getBattery() == null) {
            existing.setBattery(newPoint.getBattery());
            updated = true;
        }

        if (updated) {
            gpsPointRepository.persist(existing);
        }
    }

    /**
     * Process GPS points in smaller batches to avoid memory issues and timeouts
     */
    public BatchResult processInBatches(List<GpsPointEntity> allPoints, int batchSize) {
        int totalImported = 0;
        int totalSkipped = 0;

        for (int i = 0; i < allPoints.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, allPoints.size());
            List<GpsPointEntity> batch = allPoints.subList(i, endIndex);

            int imported = processBatch(batch);
            totalImported += imported;
            totalSkipped += (batch.size() - imported);

            log.debug("Processed batch {}-{}: {} imported, {} skipped",
                    i, endIndex - 1, imported, batch.size() - imported);
        }

        return new BatchResult(totalImported, totalSkipped);
    }

    /**
     * Result of batch processing operation
     */
    public static class BatchResult {
        public final int imported;
        public final int skipped;

        public BatchResult(int imported, int skipped) {
            this.imported = imported;
            this.skipped = skipped;
        }
    }
}