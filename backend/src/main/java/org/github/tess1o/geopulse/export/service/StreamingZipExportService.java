package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for creating ZIP exports with streaming JSON files.
 * Each file in the ZIP is generated using streaming to avoid memory accumulation.
 */
@ApplicationScoped
@Slf4j
public class StreamingZipExportService {

    @Inject
    ObjectMapper objectMapper;

    private final JsonFactory jsonFactory = new JsonFactory();

    /**
     * Adds a JSON file to ZIP using streaming approach for the data array.
     * This prevents loading all data into memory before writing to ZIP.
     *
     * @param <T> entity type
     * @param <D> DTO type
     * @param zos ZIP output stream
     * @param fileName name of the JSON file to add to ZIP
     * @param writeMetadata callback to write JSON object metadata fields
     * @param arrayFieldName name of the array field (e.g., "points", "trips")
     * @param batchProducer callback that publishes data batches
     * @param toDto function to convert entity to DTO
     * @param job export job for progress tracking
     * @param progressStart starting progress percentage
     * @param progressEnd ending progress percentage
     * @param progressPrefix prefix for progress messages
     * @return number of records written
     * @throws IOException if writing fails
     */
    public <T, D> int addStreamingJsonFileToZip(
            ZipOutputStream zos,
            String fileName,
            BiConsumer<JsonGenerator, ObjectMapper> writeMetadata,
            String arrayFieldName,
            StreamingExportService.BatchProducer<T> batchProducer,
            Function<T, D> toDto,
            ExportJob job,
            int progressStart,
            int progressEnd,
            String progressPrefix) throws IOException {

        log.debug("Adding streaming JSON file to ZIP: {}", fileName);

        // Create ZIP entry
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);

        // Create JSON generator that writes to ZIP stream
        JsonGenerator gen = jsonFactory.createGenerator(zos, JsonEncoding.UTF8);
        gen.setCodec(objectMapper);

        try {
            gen.writeStartObject();

            // Write metadata fields
            if (writeMetadata != null) {
                writeMetadata.accept(gen, objectMapper);
            }

            // Write array field
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
                            D dto = toDto.apply(entity);
                            gen.writeObject(dto);
                            totalWritten[0]++;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    batchCount[0]++;

                    if (job != null) {
                        int currentProgress = progressStart + (batchCount[0] % 10) * progressRange / 10;
                        job.updateProgress(
                                Math.min(currentProgress, progressEnd),
                                String.format("%s %s: %d records", progressPrefix, fileName, totalWritten[0])
                        );
                    }

                    if (batchCount[0] % 10 == 0) {
                        log.debug("Streamed {} records to {} in {} batches", totalWritten[0], fileName, batchCount[0]);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }

            gen.writeEndArray(); // End array field
            gen.writeEndObject(); // End root object
            gen.flush();

            log.info("Completed streaming {} records to {}", totalWritten[0], fileName);
            return totalWritten[0];

        } finally {
            // Don't close the generator (it would close the ZIP stream)
            // Just flush it
            gen.flush();
            zos.closeEntry();
        }
    }

    /**
     * Adds a simple JSON file to ZIP by serializing a single object.
     * Use this for small metadata files or files with limited data.
     */
    public void addSimpleJsonFileToZip(ZipOutputStream zos, String fileName, Object data) throws IOException {
        log.debug("Adding simple JSON file to ZIP: {}", fileName);

        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);

        String json = objectMapper.writeValueAsString(data);
        zos.write(json.getBytes());

        zos.closeEntry();
        log.debug("Added {} to ZIP ({} bytes)", fileName, json.getBytes().length);
    }
}
