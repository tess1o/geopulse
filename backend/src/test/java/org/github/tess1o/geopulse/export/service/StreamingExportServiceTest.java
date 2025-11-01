package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamingExportService.
 * Tests the base streaming utilities used by all export services.
 */
@QuarkusTest
@Slf4j
class StreamingExportServiceTest {

    @Inject
    StreamingExportService streamingExportService;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testStreamJsonArray_EmptyData() throws Exception {
        // Arrange
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportJob job = new ExportJob();
        job.setProgress(0);

        // Act
        int totalWritten = streamingExportService.streamJsonArray(
            baos,
            page -> List.of(), // Empty data
            entity -> entity,
            job,
            0,
            0,
            100,
            "Test"
        );

        // Assert
        assertEquals(0, totalWritten);
        String json = baos.toString();
        assertEquals("[]", json);
    }

    @Test
    void testStreamJsonArray_SingleBatch() throws Exception {
        // Arrange
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportJob job = new ExportJob();
        job.setProgress(0);

        List<TestEntity> data = List.of(
            new TestEntity(1, "A"),
            new TestEntity(2, "B"),
            new TestEntity(3, "C")
        );

        // Act
        int totalWritten = streamingExportService.streamJsonArray(
            baos,
            page -> page == 0 ? data : List.of(),
            entity -> new TestDto(entity.id, entity.name.toLowerCase()),
            job,
            3,
            0,
            100,
            "Test"
        );

        // Assert
        assertEquals(3, totalWritten);

        String json = baos.toString();
        log.info("Generated JSON: {}", json);

        TestDto[] result = objectMapper.readValue(json, TestDto[].class);
        assertEquals(3, result.length);
        assertEquals(1, result[0].getId());
        assertEquals("a", result[0].getValue());
    }

    @Test
    void testStreamJsonArray_MultipleBatches() throws Exception {
        // Arrange
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportJob job = new ExportJob();
        job.setProgress(0);

        // Simulate 3 batches
        List<List<TestEntity>> batches = List.of(
            List.of(new TestEntity(1, "A"), new TestEntity(2, "B")),
            List.of(new TestEntity(3, "C"), new TestEntity(4, "D")),
            List.of(new TestEntity(5, "E"))
        );

        // Act
        int totalWritten = streamingExportService.streamJsonArray(
            baos,
            page -> page < batches.size() ? batches.get(page) : List.of(),
            entity -> new TestDto(entity.id, entity.name),
            job,
            5,
            0,
            100,
            "Test"
        );

        // Assert
        assertEquals(5, totalWritten);

        String json = baos.toString();
        TestDto[] result = objectMapper.readValue(json, TestDto[].class);
        assertEquals(5, result.length);

        // Verify correct order
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, result[i].getId());
        }
    }

    @Test
    void testStreamJsonArray_ProgressTracking() throws Exception {
        // Arrange
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportJob job = new ExportJob();
        job.setProgress(0);

        List<TestEntity> data = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            data.add(new TestEntity(i, "Item" + i));
        }

        // Act
        int totalWritten = streamingExportService.streamJsonArray(
            baos,
            page -> page == 0 ? data : List.of(),
            entity -> new TestDto(entity.id, entity.name),
            job,
            10,
            10,  // Start at 10%
            90,  // End at 90%
            "Testing"
        );

        // Assert
        assertEquals(10, totalWritten);
        assertTrue(job.getProgress() >= 10 && job.getProgress() <= 90,
            "Progress should be between 10 and 90, got: " + job.getProgress());
        assertNotNull(job.getProgressMessage());
        assertTrue(job.getProgressMessage().contains("Testing"));
    }

    @Test
    void testStreamJsonObjectWithArray_EmptyData() throws Exception {
        // Arrange
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportJob job = new ExportJob();

        // Act
        int totalWritten = streamingExportService.streamJsonObjectWithArray(
            baos,
            (gen, mapper) -> {
                try {
                    gen.writeStringField("type", "test");
                    gen.writeNumberField("count", 0);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            "items",
            page -> List.of(),
            (gen, entity, mapper) -> gen.writeObject(entity),
            job,
            0,
            0,
            100,
            "Test"
        );

        // Assert
        assertEquals(0, totalWritten);

        String json = baos.toString();
        log.info("Generated JSON object: {}", json);

        assertTrue(json.contains("\"type\":\"test\""));
        assertTrue(json.contains("\"items\":[]"));
    }

    @Test
    void testStreamJsonObjectWithArray_WithData() throws Exception {
        // Arrange
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportJob job = new ExportJob();

        List<TestEntity> data = List.of(
            new TestEntity(1, "First"),
            new TestEntity(2, "Second")
        );

        // Act
        int totalWritten = streamingExportService.streamJsonObjectWithArray(
            baos,
            (gen, mapper) -> {
                try {
                    gen.writeStringField("dataType", "test");
                    gen.writeStringField("version", "1.0");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            "records",
            page -> page == 0 ? data : List.of(),
            (gen, entity, mapper) -> {
                TestDto dto = new TestDto(entity.id, entity.name);
                gen.writeObject(dto);
            },
            job,
            2,
            0,
            100,
            "Test"
        );

        // Assert
        assertEquals(2, totalWritten);

        String json = baos.toString();
        log.info("Generated JSON: {}", json);

        assertTrue(json.contains("\"dataType\":\"test\""));
        assertTrue(json.contains("\"version\":\"1.0\""));
        assertTrue(json.contains("\"records\":["));
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"id\":2"));
    }

    @Test
    void testStreamJsonArray_LargeDataset() throws Exception {
        // Arrange - Simulate large dataset with many batches
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportJob job = new ExportJob();

        int totalRecords = 5000;
        int batchSize = StreamingExportService.DEFAULT_BATCH_SIZE;

        // Act
        int totalWritten = streamingExportService.streamJsonArray(
            baos,
            page -> {
                int start = page * batchSize;
                if (start >= totalRecords) {
                    return List.of();
                }

                int end = Math.min(start + batchSize, totalRecords);
                List<TestEntity> batch = new ArrayList<>();
                for (int i = start; i < end; i++) {
                    batch.add(new TestEntity(i, "Item" + i));
                }
                return batch;
            },
            entity -> new TestDto(entity.id, entity.name),
            job,
            totalRecords,
            0,
            100,
            "Streaming"
        );

        // Assert
        assertEquals(totalRecords, totalWritten);

        // Verify JSON is valid
        String json = baos.toString();
        TestDto[] result = objectMapper.readValue(json, TestDto[].class);
        assertEquals(totalRecords, result.length);

        // Verify first and last items
        assertEquals(0, result[0].getId());
        assertEquals(totalRecords - 1, result[result.length - 1].getId());

        log.info("Successfully streamed {} records, JSON size: {} bytes",
            totalRecords, json.length());
    }

    @Test
    void testEstimateRecordCount_KnownCount() {
        // Act
        int estimated = streamingExportService.estimateRecordCount(
            page -> List.of(new TestEntity(1, "A")),
            1000
        );

        // Assert
        assertEquals(1000, estimated);
    }

    @Test
    void testEstimateRecordCount_UnknownCountSmallBatch() {
        // Arrange - Small batch (< DEFAULT_BATCH_SIZE)
        List<TestEntity> smallBatch = List.of(
            new TestEntity(1, "A"),
            new TestEntity(2, "B")
        );

        // Act
        int estimated = streamingExportService.estimateRecordCount(
            page -> smallBatch,
            0 // Unknown
        );

        // Assert
        assertEquals(2, estimated);
    }

    @Test
    void testEstimateRecordCount_UnknownCountLargeBatch() {
        // Arrange - Full batch (= DEFAULT_BATCH_SIZE, suggests more data)
        List<TestEntity> fullBatch = new ArrayList<>();
        for (int i = 0; i < StreamingExportService.DEFAULT_BATCH_SIZE; i++) {
            fullBatch.add(new TestEntity(i, "Item" + i));
        }

        // Act
        int estimated = streamingExportService.estimateRecordCount(
            page -> fullBatch,
            0 // Unknown
        );

        // Assert
        assertEquals(-1, estimated, "Should return -1 for unknown large datasets");
    }

    @Test
    void testEstimateRecordCount_EmptyData() {
        // Act
        int estimated = streamingExportService.estimateRecordCount(
            page -> List.of(),
            0
        );

        // Assert
        assertEquals(0, estimated);
    }

    @Test
    void testStreamJsonArray_ProgressMessageFormat() throws Exception {
        // Arrange
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExportJob job = new ExportJob();

        List<TestEntity> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.add(new TestEntity(i, "Item" + i));
        }

        // Act
        streamingExportService.streamJsonArray(
            baos,
            page -> page == 0 ? data : List.of(),
            entity -> new TestDto(entity.id, entity.name),
            job,
            100,
            0,
            100,
            "Exporting GPS points:"
        );

        // Assert
        assertNotNull(job.getProgressMessage());
        assertTrue(job.getProgressMessage().contains("Exporting GPS points:"));
        assertTrue(job.getProgressMessage().contains("/ 100 records"));
        log.info("Final progress message: {}", job.getProgressMessage());
    }

    // ========================================
    // Test DTOs
    // ========================================

    @Data
    static class TestEntity {
        final int id;
        final String name;
    }

    @Data
    static class TestDto {
        final int id;
        final String value;
    }
}
