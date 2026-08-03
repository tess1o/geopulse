package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.export.model.ExportJob;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base service for streaming export operations.
 * Provides common utilities for memory-efficient exports using Jackson streaming API.
 *
 * This service mirrors the streaming pattern used in imports:
 * - Process data in batches from repositories
 * - Write directly to output streams without accumulating in memory
 * - Track progress for UI updates
 * - Maintain constant memory usage O(batch_size) instead of O(total_records)
 */
@ApplicationScoped
@Slf4j
public class StreamingExportService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SystemSettingsService settingsService;

    private final JsonFactory jsonFactory = new JsonFactory();

    /**
     * Get the batch size for streaming exports from configuration.
     * Falls back to default of 1000 if not configured.
     */
    public int getBatchSize() {
        return settingsService.getInteger("export.batch-size");
    }

    /**
     * Streams a JSON array to output stream by processing data in batches.
     *
     * @param <T> the entity type
     * @param <D> the DTO type
     * @param outputStream the output stream to write to
     * @param batchProducer callback that publishes entity batches
     * @param toDto function to convert entity to DTO
     * @param job the export job for progress tracking
     * @param totalRecords estimated total records for progress calculation
     * @param progressStart starting progress percentage (0-100)
     * @param progressEnd ending progress percentage (0-100)
     * @param progressPrefix prefix for progress messages
     * @return total number of records written
     * @throws IOException if writing fails
     */
    public <T, D> int streamJsonArray(
            OutputStream outputStream,
            BatchProducer<T> batchProducer,
            Function<T, D> toDto,
            ExportJob job,
            int totalRecords,
            int progressStart,
            int progressEnd,
            String progressPrefix) throws IOException {

        JsonGenerator gen = jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8);
        gen.setCodec(objectMapper);

        try {
            gen.writeStartArray();

            int progressRange = progressEnd - progressStart;
            int[] totalWritten = {0};
            int[] batchCount = {0};

            try {
                batchProducer.produce(batch -> {
                    if (batch == null || batch.isEmpty()) {
                        return;
                    }

                    try {
                        for (T entity : batch) {
                            D dto = toDto.apply(entity);
                            gen.writeObject(dto);
                            totalWritten[0]++;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    batchCount[0]++;

                    if (totalRecords > 0 && job != null) {
                        int currentProgress = progressStart + (int) ((double) totalWritten[0] / totalRecords * progressRange);
                        job.updateProgress(
                                Math.min(currentProgress, progressEnd),
                                String.format("%s %d / %d records", progressPrefix, totalWritten[0], totalRecords)
                        );
                    }

                    if (batchCount[0] % 10 == 0) {
                        log.debug("Streamed {} records in {} batches", totalWritten[0], batchCount[0]);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }

            gen.writeEndArray();
            gen.flush();

            log.info("Completed streaming {} records in {} batches", totalWritten[0], batchCount[0]);
            return totalWritten[0];

        } finally {
            gen.close();
        }
    }

    /**
     * Streams a JSON object with a data array field to output stream.
     * Useful for exporting objects like {"type": "FeatureCollection", "features": [...]}
     *
     * @param <T> the entity type
     * @param outputStream the output stream to write to
     * @param writeObjectFields callback to write object fields before array
     * @param arrayFieldName name of the array field (e.g., "features", "points")
     * @param batchProducer callback that publishes entity batches
     * @param writeArrayItem callback to write each array item using JsonGenerator
     * @param job the export job for progress tracking
     * @param totalRecords estimated total records
     * @param progressStart starting progress percentage
     * @param progressEnd ending progress percentage
     * @param progressPrefix prefix for progress messages
     * @return total number of records written
     * @throws IOException if writing fails
     */
    public <T> int streamJsonObjectWithArray(
            OutputStream outputStream,
            ObjectFieldsWriter writeObjectFields,
            String arrayFieldName,
            BatchProducer<T> batchProducer,
            TriConsumer<JsonGenerator, T, ObjectMapper> writeArrayItem,
            ExportJob job,
            int totalRecords,
            int progressStart,
            int progressEnd,
            String progressPrefix) throws IOException {

        JsonGenerator gen = jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8);
        gen.setCodec(objectMapper);

        try {
            gen.writeStartObject();

            // Write object fields (metadata, type, etc.)
            if (writeObjectFields != null) {
                writeObjectFields.write(gen, objectMapper);
            }

            // Start array field
            gen.writeArrayFieldStart(arrayFieldName);

            int progressRange = progressEnd - progressStart;
            int[] totalWritten = {0};
            int[] batchCount = {0};

            try {
                batchProducer.produce(batch -> {
                    if (batch == null || batch.isEmpty()) {
                        return;
                    }

                    try {
                        for (T entity : batch) {
                            writeArrayItem.accept(gen, entity, objectMapper);
                            totalWritten[0]++;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    batchCount[0]++;

                    if (totalRecords > 0 && job != null) {
                        int currentProgress = progressStart + (int) ((double) totalWritten[0] / totalRecords * progressRange);
                        job.updateProgress(
                                Math.min(currentProgress, progressEnd),
                                String.format("%s %d / %d records", progressPrefix, totalWritten[0], totalRecords)
                        );
                    }

                    if (batchCount[0] % 10 == 0) {
                        log.debug("Streamed {} records in {} batches", totalWritten[0], batchCount[0]);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }

            gen.writeEndArray(); // End array field
            gen.writeEndObject(); // End root object
            gen.flush();

            log.info("Completed streaming {} records in {} batches", totalWritten[0], batchCount[0]);
            return totalWritten[0];

        } finally {
            gen.close();
        }
    }

    @FunctionalInterface
    public interface BatchProducer<T> {
        void produce(Consumer<List<T>> batchConsumer);
    }

    @FunctionalInterface
    public interface ObjectFieldsWriter {
        void write(JsonGenerator gen, ObjectMapper objectMapper) throws IOException;
    }

    /**
     * Functional interface for tri-consumer (3 parameters).
     */
    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c) throws IOException;
    }

    /**
     * Counts total records for progress tracking.
     * Uses repository count method if available, otherwise estimates from the first published batch.
     */
    public <T> int estimateRecordCount(BatchProducer<T> batchProducer, int knownCount) {
        if (knownCount > 0) {
            return knownCount;
        }

        List<?>[] firstBatch = new List<?>[1];
        try {
            batchProducer.produce(batch -> {
                if (firstBatch[0] == null) {
                    firstBatch[0] = batch;
                    throw new BatchPeekCompleteException();
                }
            });
        } catch (BatchPeekCompleteException ignored) {
            // Expected control flow: stop after one batch when no count query is available.
        }

        if (firstBatch[0] == null || firstBatch[0].isEmpty()) {
            return 0;
        }

        // If first batch is full, there are likely more batches.
        if (firstBatch[0].size() >= getBatchSize()) {
            log.debug("Estimating record count based on batch size (actual count unavailable)");
            return -1;
        }

        return firstBatch[0].size();
    }

    private static class BatchPeekCompleteException extends RuntimeException {
    }
}
