package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.exportimport.NativeSqlImportTemplates;

import java.util.List;

/**
 * Utility service for batch processing operations during imports
 */
@ApplicationScoped
@Slf4j
public class BatchProcessor {

    @Inject
    EntityManager entityManager;
    
    @ConfigProperty(name = "geopulse.import.bulk-insert-batch-size", defaultValue = "500")
    @StaticInitSafe
    int bulkInsertBatchSize;
    
    /**
     * Process a batch of GPS points using intelligent upsert logic.
     * Both CLEAR and MERGE modes use the same insert with ON CONFLICT DO UPDATE.
     * The only difference is CLEAR mode deletes old data first (handled by caller).
     *
     * @param gpsPoints The GPS points to process
     * @param clearModeEnabled Only used for logging (both modes use same upsert logic)
     * @return Number of GPS points imported or updated
     */
    @Transactional
    public int processBatch(List<GpsPointEntity> gpsPoints, boolean clearModeEnabled) {
        return processBatch(gpsPoints, clearModeEnabled, 0, 0);
    }

    /**
     * Process a batch of GPS points with context for better logging.
     * Uses bulk insert with ON CONFLICT DO UPDATE for both CLEAR and MERGE modes.
     *
     * @param gpsPoints The GPS points to process
     * @param clearModeEnabled Only used for logging (both modes use same upsert logic)
     * @param totalProcessedSoFar Total points already processed before this batch
     * @param totalExpected Total expected points (0 if unknown)
     * @return Number of GPS points imported or updated
     */
    @Transactional
    public int processBatch(List<GpsPointEntity> gpsPoints, boolean clearModeEnabled,
                           int totalProcessedSoFar, int totalExpected) {
        if (gpsPoints.isEmpty()) {
            return 0;
        }

        // Both CLEAR and MERGE modes use the same logic now
        // The only difference is CLEAR deletes data first (handled by caller)
        return processBatchWithUpsert(gpsPoints, clearModeEnabled, totalProcessedSoFar, totalExpected);
    }
    
    /**
     * Unified batch processing with intelligent upsert.
     * Uses ON CONFLICT DO UPDATE to handle duplicates and enrich existing data.
     * Works for both CLEAR and MERGE modes (caller handles deletion for CLEAR mode).
     */
    private int processBatchWithUpsert(List<GpsPointEntity> gpsPoints, boolean clearModeEnabled,
                                       int totalProcessedSoFar, int totalExpected) {
        final int BULK_INSERT_BATCH_SIZE = bulkInsertBatchSize;
        int totalUpserted = 0;
        long startTime = System.currentTimeMillis();
        String mode = clearModeEnabled ? "CLEAR" : "MERGE";

        for (int i = 0; i < gpsPoints.size(); i += BULK_INSERT_BATCH_SIZE) {
            int endIndex = Math.min(i + BULK_INSERT_BATCH_SIZE, gpsPoints.size());
            List<GpsPointEntity> subBatch = gpsPoints.subList(i, endIndex);

            long batchStartTime = System.currentTimeMillis();
            try {
                // Use bulk insert with ON CONFLICT DO UPDATE
                int upserted = bulkUpsertGpsPoints(subBatch);
                totalUpserted += upserted;

                long batchDuration = System.currentTimeMillis() - batchStartTime;

                // Build progress context message
                int currentTotal = totalProcessedSoFar + i + subBatch.size();
                String progressContext = totalExpected > 0
                    ? String.format(" [%d / %d points, %.1f%%]", currentTotal, totalExpected, (currentTotal * 100.0 / totalExpected))
                    : String.format(" [%d points processed]", currentTotal);

                log.info("{} MODE: Bulk upserted {} points in {}ms ({} points/sec){}",
                        mode, upserted, batchDuration,
                        batchDuration > 0 ? (subBatch.size() * 1000L / batchDuration) : 0,
                        progressContext);
            } catch (Exception e) {
                log.error("Failed to bulk upsert GPS points sub-batch {}-{}: {}", i, endIndex - 1, e.getMessage(), e);
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("{} MODE summary: {} points upserted in {}ms", mode, totalUpserted, totalDuration);
        return totalUpserted;
    }

    /**
     * Bulk insert/update GPS points using native SQL with ON CONFLICT DO UPDATE.
     * The unique constraint on (user_id, timestamp, coordinates) triggers updates for duplicates.
     * Updates enrich existing points with better accuracy or missing fields.
     *
     * @param gpsPoints List of GPS points to insert or update
     * @return Number of rows affected (inserts + updates)
     */
    private int bulkUpsertGpsPoints(List<GpsPointEntity> gpsPoints) {
        if (gpsPoints.isEmpty()) {
            return 0;
        }

        // Build multi-row INSERT using the template from NativeSqlImportTemplates
        // This leverages the unique constraint: idx_gps_points_no_duplicates (user_id, timestamp, coordinates)
        String singleRowTemplate = NativeSqlImportTemplates.GPS_POINTS_INSERT_OR_UPDATE;

        // Extract the VALUES clause to build multi-row insert
        // Template format: INSERT INTO ... VALUES (?, ?, ...) ON CONFLICT ...
        int valuesStart = singleRowTemplate.indexOf("VALUES") + 6;
        int valuesEnd = singleRowTemplate.indexOf("ON CONFLICT");
        String valueClause = singleRowTemplate.substring(valuesStart, valuesEnd).trim();

        StringBuilder sql = new StringBuilder();
        sql.append(singleRowTemplate, 0, valuesStart + 1); // INSERT INTO ... VALUES

        // Add value placeholders for each point
        for (int i = 0; i < gpsPoints.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(valueClause);
        }

        // Add ON CONFLICT clause
        sql.append(" ").append(singleRowTemplate.substring(valuesEnd));

        // Create native query
        var query = entityManager.createNativeQuery(sql.toString());

        // Bind parameters for each GPS point
        int paramIndex = 1;
        for (GpsPointEntity point : gpsPoints) {
            query.setParameter(paramIndex++, point.getUser().getId().toString());
            query.setParameter(paramIndex++, point.getDeviceId());
            query.setParameter(paramIndex++, point.getCoordinates().toText()); // WKT format
            query.setParameter(paramIndex++, point.getTimestamp());
            query.setParameter(paramIndex++, point.getAccuracy());
            query.setParameter(paramIndex++, point.getBattery());
            query.setParameter(paramIndex++, point.getVelocity());
            query.setParameter(paramIndex++, point.getAltitude());
            query.setParameter(paramIndex++, point.getSourceType() != null ? point.getSourceType().name() : null);
            query.setParameter(paramIndex++, point.getCreatedAt());
        }

        // Execute and get number of rows affected (inserts + updates)
        int rowsAffected = query.executeUpdate();

        // Clear persistence context to avoid memory buildup
        entityManager.flush();
        entityManager.clear();

        return rowsAffected;
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