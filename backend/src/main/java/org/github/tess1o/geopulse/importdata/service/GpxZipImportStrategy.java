package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.gpx.StreamingGpxParser;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import strategy for ZIP files containing multiple GPX files.
 * Uses streaming parser for memory-efficient import - processes large ZIP files without loading into RAM.
 *
 * This strategy:
 * - Streams through ZIP file without loading everything into memory
 * - Uses StreamingGpxParser for each GPX file (no intermediate String)
 * - Batches GPS points across all files and flushes to DB regularly
 * - Triggers timeline regeneration only once after all files are imported
 * - Provides progress updates per-file
 */
@ApplicationScoped
@Slf4j
public class GpxZipImportStrategy extends BaseGpsImportStrategy {

    @Override
    public String getFormat() {
        return "gpx-zip";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        log.info("Validating GPX ZIP file using streaming parser (memory-efficient from {})",
                job.hasTempFile() ? "temp file" : "memory");

        int totalGpxFiles = 0;
        int totalValidPoints = 0;
        Instant firstTimestamp = null;
        Instant lastTimestamp = null;

        // Stream through ZIP file to count GPX files and validate
        try (InputStream inputStream = job.getDataStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Skip directories and non-GPX files
                if (entry.isDirectory() || !isGpxFile(entry.getName())) {
                    log.debug("Skipping non-GPX entry: {}", entry.getName());
                    zipInputStream.closeEntry();
                    continue;
                }

                log.debug("Validating GPX file: {}", entry.getName());
                totalGpxFiles++;

                // Validate this GPX file using streaming parser (no String allocation)
                try {
                    // Create non-closing wrapper to prevent ZipInputStream from being closed
                    InputStream nonClosingStream = new NonClosingInputStream(zipInputStream);
                    StreamingGpxParser parser = new StreamingGpxParser(nonClosingStream);

                    AtomicReference<Instant> fileFirst = new AtomicReference<>();
                    AtomicReference<Instant> fileLast = new AtomicReference<>();

                    StreamingGpxParser.ParsingStats stats = parser.parseGpsPoints((point, currentStats) -> {
                        if (point.time != null) {
                            if (fileFirst.get() == null || point.time.isBefore(fileFirst.get())) {
                                fileFirst.set(point.time);
                            }
                            if (fileLast.get() == null || point.time.isAfter(fileLast.get())) {
                                fileLast.set(point.time);
                            }
                        }
                    });

                    totalValidPoints += stats.totalGpsPoints;

                    // Track timestamp range across all files
                    if (fileFirst.get() != null) {
                        if (firstTimestamp == null || fileFirst.get().isBefore(firstTimestamp)) {
                            firstTimestamp = fileFirst.get();
                        }
                    }
                    if (fileLast.get() != null) {
                        if (lastTimestamp == null || fileLast.get().isAfter(lastTimestamp)) {
                            lastTimestamp = fileLast.get();
                        }
                    }

                    log.debug("Validated GPX file {}: {} valid points", entry.getName(), stats.totalGpsPoints);

                } catch (Exception e) {
                    log.warn("Failed to validate GPX file {}: {}", entry.getName(), e.getMessage());
                    // Continue with other files - don't fail entire import for one bad file
                }

                zipInputStream.closeEntry();
            }
        }

        if (totalGpxFiles == 0) {
            throw new IllegalArgumentException("ZIP file contains no valid GPX files");
        }

        if (totalValidPoints == 0) {
            throw new IllegalArgumentException("ZIP file contains no valid GPS data");
        }

        log.info("GPX ZIP validation successful: {} GPX files, {} total valid GPS points",
                totalGpxFiles, totalValidPoints);

        return new FormatValidationResult(totalValidPoints, totalValidPoints, firstTimestamp, lastTimestamp);
    }

    /**
     * Use template method pattern to handle streaming import workflow.
     */
    @Override
    public void processImportData(ImportJob job) throws IOException {
        processStreamingImport(job, this::streamingImportFromZip,
                "Processing GPX files from ZIP...", "GPS points");
    }

    /**
     * Stream through ZIP file and process each GPX file with direct database writes.
     */
    private StreamingImportResult streamingImportFromZip(ImportJob job, UserEntity user, boolean clearMode)
            throws IOException {

        int batchSize = settingsService.getInteger("import.gpx-streaming-batch-size");
        List<GpsPointEntity> currentBatch = new ArrayList<>(batchSize);
        AtomicInteger totalImported = new AtomicInteger(0);
        AtomicInteger totalSkipped = new AtomicInteger(0);
        AtomicInteger totalGpsPoints = new AtomicInteger(0);
        AtomicInteger processedFiles = new AtomicInteger(0);
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);

        int totalExpectedPoints = job.getTotalRecordsFromValidation();

        // First pass: count total GPX files for progress tracking
        int totalFiles = 0;
        try (InputStream inputStream = job.getDataStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && isGpxFile(entry.getName())) {
                    totalFiles++;
                }
                zipInputStream.closeEntry();
            }
        }

        log.info("Found {} GPX files in ZIP, starting streaming import with batch size: {}, clear mode: {}, total expected points: {}",
                totalFiles, batchSize, clearMode, totalExpectedPoints);

        // Second pass: process each GPX file
        try (InputStream inputStream = job.getDataStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Skip directories and non-GPX files
                if (entry.isDirectory() || !isGpxFile(entry.getName())) {
                    zipInputStream.closeEntry();
                    continue;
                }

                processedFiles.incrementAndGet();
                String fileName = entry.getName();
                log.debug("Processing GPX file {}/{}: {}", processedFiles.get(), totalFiles, fileName);

                job.updateProgress(
                        25 + (int) ((double) processedFiles.get() / totalFiles * 35), // Progress from 25% to 60%
                        String.format("Processing file %d of %d: %s", processedFiles.get(), totalFiles, getShortFileName(fileName))
                );

                // Parse GPX file using streaming parser (no String allocation!)
                try {
                    // Create non-closing wrapper to prevent ZipInputStream from being closed
                    InputStream nonClosingStream = new NonClosingInputStream(zipInputStream);
                    StreamingGpxParser parser = new StreamingGpxParser(nonClosingStream);

                    parser.parseGpsPoints((point, stats) -> {
                        totalGpsPoints.incrementAndGet();

                        // Apply date range filter if specified
                        if (isOutsideDateRange(point.time, job)) {
                            return;
                        }

                        // Convert GPX point to GPS entity
                        GpsPointEntity gpsEntity = convertGpxPointToGpsEntity(point, user);
                        if (gpsEntity != null) {
                            addToBatchAndFlushIfNeeded(currentBatch, gpsEntity, firstTimestamp,
                                    totalImported, totalSkipped, clearMode, job, totalExpectedPoints, batchSize);
                        }
                    });

                    log.debug("Completed processing GPX file: {}", fileName);

                } catch (Exception e) {
                    log.warn("Failed to process GPX file {}: {}", fileName, e.getMessage(), e);
                    // Continue with other files - don't fail entire import for one bad file
                }

                zipInputStream.closeEntry();
            }
        }

        // Flush any remaining batch
        if (!currentBatch.isEmpty()) {
            flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedPoints);
        }

        log.warn("GPX ZIP import STATISTICS: processed {} files, extracted {} GPS points, {} imported, {} skipped (SKIP RATE: {}%). " +
                "Note: Skipped points may include duplicates (detected by unique constraint) and filtered points.",
                processedFiles.get(), totalGpsPoints.get(), totalImported.get(), totalSkipped.get(),
                totalGpsPoints.get() > 0 ? (totalSkipped.get() * 100.0 / totalGpsPoints.get()) : 0);

        return new StreamingImportResult(
                totalImported.get(),
                totalSkipped.get(),
                totalGpsPoints.get(),
                firstTimestamp.get(),
                processedFiles.get()
        );
    }

    /**
     * Add GPS point to current batch and flush to database when batch is full.
     */
    private void addToBatchAndFlushIfNeeded(
            List<GpsPointEntity> currentBatch,
            GpsPointEntity gpsPoint,
            AtomicReference<Instant> firstTimestamp,
            AtomicInteger totalImported,
            AtomicInteger totalSkipped,
            boolean clearMode,
            ImportJob job,
            int totalExpectedPoints,
            int batchSize) {

        // Track first timestamp for timeline generation
        if (firstTimestamp.get() == null && gpsPoint.getTimestamp() != null) {
            firstTimestamp.set(gpsPoint.getTimestamp());
        }

        currentBatch.add(gpsPoint);

        // Flush when batch is full
        if (currentBatch.size() >= batchSize) {
            flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedPoints);
            currentBatch.clear(); // CRITICAL: Clear to release memory
        }
    }

    /**
     * Flush a batch directly to the database and clear it from memory.
     */
    private void flushBatchToDatabase(
            List<GpsPointEntity> batch,
            boolean clearMode,
            AtomicInteger totalImported,
            AtomicInteger totalSkipped,
            int totalExpected) {

        if (batch.isEmpty()) {
            return;
        }

        try {
            int alreadyProcessed = totalImported.get() + totalSkipped.get();
            int imported = batchProcessor.processBatch(batch, clearMode, alreadyProcessed, totalExpected);
            totalImported.addAndGet(imported);
            totalSkipped.addAndGet(batch.size() - imported);
        } catch (Exception e) {
            log.error("Failed to flush batch to database: {}", e.getMessage(), e);
            // Continue processing even if one batch fails
        }
    }

    /**
     * Convert StreamingGpxParser.GpxPoint to GpsPointEntity
     */
    private GpsPointEntity convertGpxPointToGpsEntity(StreamingGpxParser.GpxPoint point, UserEntity user) {
        try {
            GpsPointEntity gpsEntity = new GpsPointEntity();
            gpsEntity.setUser(user);
            gpsEntity.setDeviceId("trackpoint".equals(point.type) ? "gpx-import" : "gpx-waypoint-import");
            gpsEntity.setCoordinates(GeoUtils.createPoint(point.lon, point.lat));
            gpsEntity.setTimestamp(point.time);
            gpsEntity.setSourceType(GpsSourceType.GPX);
            gpsEntity.setCreatedAt(Instant.now());

            // Set elevation if available
            if (point.elevation != null) {
                gpsEntity.setAltitude(point.elevation);
            }

            // Set speed if available (convert from m/s to km/h for trackpoints)
            if (point.speed != null) {
                gpsEntity.setVelocity(point.speed * 3.6); // Convert m/s to km/h
            } else if ("waypoint".equals(point.type)) {
                // Waypoints are typically stationary
                gpsEntity.setVelocity(0.0);
            }

            return gpsEntity;

        } catch (Exception e) {
            log.warn("Failed to create GPS entity from GPX point: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a ZIP entry is a GPX file based on extension
     */
    private boolean isGpxFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".gpx");
    }

    /**
     * Get short file name for display (last component of path)
     */
    private String getShortFileName(String fullPath) {
        if (fullPath == null) {
            return "unknown";
        }
        int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
    }

    /**
     * InputStream wrapper that prevents the underlying stream from being closed.
     * Used to allow StreamingGpxParser to work with ZipInputStream without closing it.
     */
    private static class NonClosingInputStream extends InputStream {
        private final InputStream delegate;

        NonClosingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return delegate.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return delegate.skip(n);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            // DO NOT close the delegate - this is intentional!
            // The ZipInputStream will be closed by the caller
        }

        @Override
        public void mark(int readlimit) {
            delegate.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            delegate.reset();
        }

        @Override
        public boolean markSupported() {
            return delegate.markSupported();
        }
    }
}
