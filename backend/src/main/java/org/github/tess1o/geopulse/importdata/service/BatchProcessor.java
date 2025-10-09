package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;

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
    
    @ConfigProperty(name = "geopulse.import.bulk-insert-batch-size", defaultValue = "500")
    @StaticInitSafe
    int bulkInsertBatchSize;
    
    @ConfigProperty(name = "geopulse.import.merge-batch-size", defaultValue = "250")
    @StaticInitSafe
    int mergeBatchSize;
    
    /**
     * Process a batch of GPS points with two different paths based on clear mode
     * 
     * @param gpsPoints The GPS points to process
     * @param clearModeEnabled If true, use fast bulk insert (no duplicate detection)
     *                        If false, use merge mode with duplicate detection
     * @return Number of GPS points imported
     */
    @Transactional
    public int processBatch(List<GpsPointEntity> gpsPoints, boolean clearModeEnabled) {
        if (gpsPoints.isEmpty()) {
            return 0;
        }

        if (clearModeEnabled) {
            return processBatchClearMode(gpsPoints);
        } else {
            return processBatchMergeMode(gpsPoints);
        }
    }
    
    /**
     * Fast path: Clear mode - bulk insert without duplicate detection
     */
    private int processBatchClearMode(List<GpsPointEntity> gpsPoints) {
        log.debug("Processing batch of {} GPS points in CLEAR mode (bulk insert)", gpsPoints.size());
        
        final int BULK_INSERT_BATCH_SIZE = bulkInsertBatchSize;
        int totalImported = 0;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < gpsPoints.size(); i += BULK_INSERT_BATCH_SIZE) {
            int endIndex = Math.min(i + BULK_INSERT_BATCH_SIZE, gpsPoints.size());
            List<GpsPointEntity> subBatch = gpsPoints.subList(i, endIndex);
            
            long batchStartTime = System.currentTimeMillis();
            try {
                // Bulk insert sub-batch
                gpsPointRepository.persist(subBatch);
                entityManager.flush();
                entityManager.clear();
                
                totalImported += subBatch.size();
                long batchDuration = System.currentTimeMillis() - batchStartTime;
                log.debug("Bulk inserted sub-batch {}-{}: {} GPS points in {}ms ({} points/sec)", 
                        i, endIndex - 1, subBatch.size(), batchDuration, 
                        batchDuration > 0 ? (subBatch.size() * 1000L / batchDuration) : 0);
            } catch (Exception e) {
                log.error("Failed to bulk insert GPS points sub-batch {}-{}: {}", i, endIndex - 1, e.getMessage());
            }
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        double pointsPerSecond = totalDuration > 0 ? (gpsPoints.size() * 1000.0 / totalDuration) : 0;
        log.info("CLEAR MODE: Successfully bulk inserted {} GPS points in {} sub-batches. Duration: {}ms, Throughput: {} points/sec, Batch size: {}",
                totalImported, (gpsPoints.size() + BULK_INSERT_BATCH_SIZE - 1) / BULK_INSERT_BATCH_SIZE, 
                totalDuration, pointsPerSecond, BULK_INSERT_BATCH_SIZE);
        return totalImported;
    }
    
    /**
     * Optimized merge mode - batch duplicate detection
     */
    private int processBatchMergeMode(List<GpsPointEntity> gpsPoints) {
        log.debug("Processing batch of {} GPS points in MERGE mode (with duplicate detection)", gpsPoints.size());
        
        int imported = 0;
        long startTime = System.currentTimeMillis();

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
                    GpsPointEntity existing = existingPoints.getFirst();
                    updateGpsPointIfNecessary(existing, gpsPoint);
                }

            } catch (Exception e) {
                log.error("Failed to import GPS point at {}", gpsPoint.getTimestamp(), e);
            }
        }

        // Flush periodically to free memory
        if (gpsPoints.size() > 100) {
            entityManager.flush();
            entityManager.clear();
        }

        // Always flush at the end to ensure persistence
        entityManager.flush();

        long totalDuration = System.currentTimeMillis() - startTime;
        double pointsPerSecond = totalDuration > 0 ? (gpsPoints.size() * 1000.0 / totalDuration) : 0;
        log.info("MERGE MODE: Processed {} GPS points, imported {} new points. Duration: {}ms, Throughput: {} points/sec, Batch size: {}",
                gpsPoints.size(), imported, totalDuration, pointsPerSecond, mergeBatchSize);

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
     * Process GPS points in smaller batches to avoid memory issues and timeouts (merge mode)
     */
    public BatchResult processInBatches(List<GpsPointEntity> allPoints, int batchSize) {
        return processInBatches(allPoints, batchSize, false);
    }
    
    /**
     * Process GPS points in smaller batches with mode selection
     * 
     * @param allPoints All GPS points to process
     * @param batchSize Size of each batch
     * @param clearModeEnabled If true, use fast clear mode; if false, use merge mode
     * @return BatchResult with import statistics
     */
    public BatchResult processInBatches(List<GpsPointEntity> allPoints, int batchSize, boolean clearModeEnabled) {
        return processInBatches(allPoints, batchSize, clearModeEnabled, null, 35, 95);
    }
    
    /**
     * Process GPS points in smaller batches with mode selection and progress updates
     * 
     * @param allPoints All GPS points to process
     * @param batchSize Size of each batch
     * @param clearModeEnabled If true, use fast clear mode; if false, use merge mode
     * @param job Optional import job for progress updates
     * @param startProgress Starting progress percentage for this operation
     * @param endProgress Ending progress percentage for this operation
     * @return BatchResult with import statistics
     */
    public BatchResult processInBatches(List<GpsPointEntity> allPoints, int batchSize, boolean clearModeEnabled, 
                                      ImportJob job, int startProgress, int endProgress) {
        int totalImported = 0;
        int totalSkipped = 0;
        
        String mode = clearModeEnabled ? "CLEAR" : "MERGE";
        String importMode = clearModeEnabled ? "bulk inserting" : "merging with existing data";
        long overallStartTime = System.currentTimeMillis();
        
        log.info("Processing {} GPS points in {} batches using {} mode", 
                allPoints.size(), (allPoints.size() + batchSize - 1) / batchSize, mode);

        int totalBatches = (allPoints.size() + batchSize - 1) / batchSize;
        int batchCount = 0;
        
        for (int i = 0; i < allPoints.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, allPoints.size());
            List<GpsPointEntity> batch = allPoints.subList(i, endIndex);

            int imported = processBatch(batch, clearModeEnabled);
            totalImported += imported;
            totalSkipped += (batch.size() - imported);
            
            batchCount++;

            // Update progress if job is provided
            if (job != null) {
                int currentProgress = startProgress + (int) ((double) batchCount / totalBatches * (endProgress - startProgress));
                job.updateProgress(currentProgress, "Importing GPS points (" + importMode + ") - batch " + batchCount + "/" + totalBatches);
            }

            log.debug("Processed batch {}-{} out of {} using {} mode: {} imported, {} skipped",
                    i, endIndex - 1, allPoints.size(), mode, imported, batch.size() - imported);
        }

        long totalDuration = System.currentTimeMillis() - overallStartTime;
        double overallPointsPerSecond = totalDuration > 0 ? (totalImported * 1000.0 / totalDuration) : 0;
        log.info("Batch processing complete in {} mode: {} imported, {} skipped from {} total points. Total duration: {}ms, Overall throughput: {} points/sec",
                mode, totalImported, totalSkipped, allPoints.size(), totalDuration, overallPointsPerSecond);
        
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