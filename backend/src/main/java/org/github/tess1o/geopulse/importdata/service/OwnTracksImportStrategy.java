package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.owntracks.StreamingOwnTracksParser;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Import strategy for OwnTracks JSON format
 */
@ApplicationScoped
@Slf4j
public class OwnTracksImportStrategy extends BaseGpsImportStrategy {

    @Inject
    GpsPointMapper gpsPointMapper;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Override
    public String getFormat() {
        return "owntracks";
    }
    
    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        // Use streaming parser for validation - no memory overhead
        log.info("Validating OwnTracks file using streaming parser (memory-efficient from {})",
                job.hasTempFile() ? "temp file" : "memory");

        // Track timestamps during validation for use in clear mode
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);
        AtomicReference<Instant> lastTimestamp = new AtomicReference<>(null);

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingOwnTracksParser parser = new StreamingOwnTracksParser(dataStream, objectMapper);

            // Parse through entire file to validate structure, count messages, and track timestamps
            StreamingOwnTracksParser.ParsingStats stats = parser.parseMessages((message, currentStats) -> {
                if (!isValidGpsMessage(message)) {
                    return;
                }

                // Extract timestamp from message
                Instant timestamp = Instant.ofEpochSecond(message.getTst());

                if (firstTimestamp.get() == null || timestamp.isBefore(firstTimestamp.get())) {
                    firstTimestamp.set(timestamp);
                }
                if (lastTimestamp.get() == null || timestamp.isAfter(lastTimestamp.get())) {
                    lastTimestamp.set(timestamp);
                }
            });

            if (stats.totalMessages == 0) {
                throw new IllegalArgumentException("OwnTracks file contains no location data");
            }

            if (stats.validMessages == 0) {
                throw new IllegalArgumentException("OwnTracks file contains no valid GPS messages");
            }

            log.info("OwnTracks streaming validation successful: {} messages, {} valid messages, date range: {} to {}",
                    stats.totalMessages, stats.validMessages, firstTimestamp.get(), lastTimestamp.get());

            return new FormatValidationResult(stats.totalMessages, stats.validMessages,
                    firstTimestamp.get(), lastTimestamp.get());
        }
    }
    
    /**
     * Use template method pattern to handle streaming import workflow.
     */
    @Override
    public void processImportData(ImportJob job) throws IOException {
        processStreamingImport(job, this::streamingImportWithDirectWrites,
                "Parsing and inserting GPS data...", "messages");
    }

    /**
     * Perform streaming import with direct database writes - NO entity accumulation.
     * This is the true memory-efficient implementation.
     */
    private StreamingImportResult streamingImportWithDirectWrites(ImportJob job, UserEntity user, boolean clearMode)
            throws IOException {

        int batchSize = settingsService.getInteger("import.owntracks-streaming-batch-size");
        List<GpsPointEntity> currentBatch = new ArrayList<>(batchSize);
        AtomicInteger totalImported = new AtomicInteger(0);
        AtomicInteger totalSkipped = new AtomicInteger(0);
        AtomicInteger totalMessages = new AtomicInteger(0);
        AtomicReference<Instant> firstTimestamp = new AtomicReference<>(null);

        // Get total expected messages from validation for accurate progress tracking
        int totalExpectedMessages = job.getTotalRecordsFromValidation();

        log.info("Starting streaming import with batch size: {}, clear mode: {}, total expected messages: {}, from {}",
                batchSize, clearMode, totalExpectedMessages, job.hasTempFile() ? "temp file" : "memory");

        // Use getDataStream() to abstract whether data is in memory or on disk
        try (InputStream dataStream = job.getDataStream()) {
            StreamingOwnTracksParser parser = new StreamingOwnTracksParser(dataStream, objectMapper);

            parser.parseMessages((message, stats) -> {
                totalMessages.incrementAndGet();

                // Skip invalid messages
                if (!isValidGpsMessage(message)) {
                    return;
                }

                // Apply date range filter using base class method
                Instant messageTime = Instant.ofEpochSecond(message.getTst());
                if (isOutsideDateRange(messageTime, job)) {
                    totalSkipped.incrementAndGet();
                    return;
                }

                try {
                    String deviceId = message.getTid() != null ? message.getTid() : "owntracks-import";
                    GpsPointEntity gpsPoint = gpsPointMapper.toEntity(message, user, deviceId, GpsSourceType.OWNTRACKS);
                    addToBatchAndFlushIfNeeded(currentBatch, gpsPoint, firstTimestamp,
                        totalImported, totalSkipped, clearMode, job, totalExpectedMessages, batchSize);
                } catch (Exception e) {
                    log.warn("Failed to create GPS point from message: {}", e.getMessage());
                    totalSkipped.incrementAndGet();
                }
            });

            // Flush any remaining batch
            if (!currentBatch.isEmpty()) {
                flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedMessages);
            }

            log.info("Streaming import completed: {} messages processed, {} points imported, {} skipped",
                    totalMessages.get(), totalImported.get(), totalSkipped.get());

            return new StreamingImportResult(
                totalImported.get(),
                totalSkipped.get(),
                totalMessages.get(),
                firstTimestamp.get()
            );
        }
    }

    /**
     * Add GPS point to current batch and flush to database when batch is full.
     * This keeps memory usage constant by writing batches immediately.
     */
    private void addToBatchAndFlushIfNeeded(
            List<GpsPointEntity> currentBatch,
            GpsPointEntity gpsPoint,
            AtomicReference<Instant> firstTimestamp,
            AtomicInteger totalImported,
            AtomicInteger totalSkipped,
            boolean clearMode,
            ImportJob job,
            int totalExpectedMessages,
            int batchSize) {

        // Track first timestamp for timeline generation
        if (firstTimestamp.get() == null && gpsPoint.getTimestamp() != null) {
            firstTimestamp.set(gpsPoint.getTimestamp());
        }

        currentBatch.add(gpsPoint);

        // Flush when batch is full
        if (currentBatch.size() >= batchSize) {
            flushBatchToDatabase(currentBatch, clearMode, totalImported, totalSkipped, totalExpectedMessages);
            currentBatch.clear(); // CRITICAL: Clear to release memory

            // Update progress after DB flush based on actual imported count
            // Progress from 20% to 70% (50% range) based on database writes
            if (totalExpectedMessages > 0) {
                int processed = totalImported.get() + totalSkipped.get();
                int progress = 20 + (int)((double)processed / totalExpectedMessages * 50);
                job.updateProgress(Math.min(progress, 70),
                    "Importing to database: " + processed + " / " + totalExpectedMessages + " GPS points processed");
            }
        }
    }

    /**
     * Flush a batch directly to the database and clear it from memory.
     * This is where memory is freed continuously during import.
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

    private boolean isValidGpsMessage(OwnTracksLocationMessage message) {
        return message.getLat() != null && 
               message.getLon() != null && 
               message.getTst() != null;
    }
}