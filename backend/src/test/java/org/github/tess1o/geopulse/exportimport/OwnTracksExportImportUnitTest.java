package org.github.tess1o.geopulse.exportimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for OwnTracks export/import functionality using real database.
 * Tests the complete OwnTracks data flow including JSON parsing, validation,
 * import processing, and duplicate detection.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class OwnTracksExportImportUnitTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Inject
    ImportDataService importDataService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    CleanupHelper cleanupHelper;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        cleanupTestData();

        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("test-owntracks@geopulse.app");
        testUser.setFullName("OwnTracks Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Transactional
    void cleanupTestData() {
        cleanupHelper.cleanupTimeline();
        gpsPointRepository.delete("user.email = ?1", "test-owntracks@geopulse.app");
        userRepository.delete("email = ?1", "test-owntracks@geopulse.app");
    }

    @Test
    @Transactional
    void testOwnTracksJsonValidation_ValidData() throws Exception {
        // Create valid OwnTracks JSON data
        OwnTracksLocationMessage[] messages = {
            createOwnTracksMessage(37.7749, -122.4194, Instant.now().minus(1, ChronoUnit.HOURS)),
            createOwnTracksMessage(37.7849, -122.4094, Instant.now().minus(30, ChronoUnit.MINUTES)),
            createOwnTracksMessage(37.7949, -122.3994, Instant.now())
        };

        String jsonContent = objectMapper.writeValueAsString(messages);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "test.json", jsonData);

        // Test actual import processing
        assertDoesNotThrow(() -> {
            List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(job);
            assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS));
            
            // Process the import
            importDataService.processImportData(job);
            
            // Verify GPS points were imported
            List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserAndDateRange(
                testUser.getId(), 
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS),
                0, 10, "timestamp", "asc"
            );
            
            assertEquals(3, importedPoints.size());
            
            for (GpsPointEntity point : importedPoints) {
                assertNotNull(point.getLatitude());
                assertNotNull(point.getLongitude());
                assertNotNull(point.getTimestamp());
                assertTrue(point.getLatitude() > 37.0 && point.getLatitude() < 38.0);
                assertTrue(point.getLongitude() > -123.0 && point.getLongitude() < -122.0);
                assertEquals(GpsSourceType.OWNTRACKS, point.getSourceType());
            }
        });
    }

    @Test
    @Transactional
    void testOwnTracksJsonValidation_InvalidJson() {
        byte[] invalidJsonData = "{ invalid json }".getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "invalid.json", invalidJsonData);

        // Should throw exception during validation
        assertThrows(Exception.class, () -> {
            importDataService.validateAndDetectDataTypes(job);
        });
    }

    @Test
    @Transactional
    void testOwnTracksJsonValidation_EmptyArray() throws Exception {
        byte[] emptyArrayData = "[]".getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "empty.json", emptyArrayData);

        // Should throw exception for empty data
        assertThrows(IllegalArgumentException.class, () -> {
            importDataService.validateAndDetectDataTypes(job);
        }, "Empty OwnTracks file should throw IllegalArgumentException");
    }

    @Test
    @Transactional
    void testOwnTracksJsonValidation_MissingRequiredFields() throws Exception {
        // Create messages with missing required fields
        String jsonWithMissingFields = "[" +
            "{\"_type\":\"location\", \"lat\":37.7749, \"lon\":null, \"tst\":null}," +
            "{\"_type\":\"location\", \"lat\":null, \"lon\":-122.4194, \"tst\":1234567890}," +
            "{\"_type\":\"location\", \"lat\":37.7749, \"lon\":-122.4194, \"tst\":null}" +
            "]";

        byte[] jsonData = jsonWithMissingFields.getBytes();
        
        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "invalid-fields.json", jsonData);

        // Should throw exception for invalid data with missing required fields
        assertThrows(IllegalArgumentException.class, () -> {
            importDataService.validateAndDetectDataTypes(job);
        }, "OwnTracks file with invalid coordinates should throw IllegalArgumentException");
    }

    @Test
    @Transactional
    void testDateRangeFiltering() throws Exception {
        Instant now = Instant.now();
        
        // Create messages with different timestamps
        OwnTracksLocationMessage[] messages = {
            createOwnTracksMessage(37.7749, -122.4194, now.minus(3, ChronoUnit.HOURS)), // Outside range
            createOwnTracksMessage(37.7849, -122.4094, now.minus(1, ChronoUnit.HOURS)), // Inside range
            createOwnTracksMessage(37.7949, -122.3994, now.minus(30, ChronoUnit.MINUTES)), // Inside range
            createOwnTracksMessage(37.8049, -122.3894, now.plus(1, ChronoUnit.HOURS))  // Outside range
        };

        String jsonContent = objectMapper.writeValueAsString(messages);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        
        // Set date range filter (last 2 hours)
        ExportDateRange dateFilter = new ExportDateRange();
        dateFilter.setStartDate(now.minus(2, ChronoUnit.HOURS));
        dateFilter.setEndDate(now.plus(30, ChronoUnit.MINUTES));
        options.setDateRangeFilter(dateFilter);

        ImportJob job = new ImportJob(testUser.getId(), options, "daterange.json", jsonData);

        // Import with date filtering
        importDataService.processImportData(job);
        
        // Verify only messages within date range were imported
        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserAndDateRange(
            testUser.getId(), 
            now.minus(4, ChronoUnit.HOURS),
            now.plus(2, ChronoUnit.HOURS),
            0, 10, "timestamp", "asc"
        );
        
        assertEquals(2, importedPoints.size(), "Only 2 messages should be imported within the date range");
        
        // Verify the imported points are within the expected time range
        for (GpsPointEntity point : importedPoints) {
            assertTrue(!point.getTimestamp().isBefore(dateFilter.getStartDate()) && 
                      !point.getTimestamp().isAfter(dateFilter.getEndDate()),
                      "Imported point timestamp should be within date range");
        }
    }

    @Test
    @Transactional
    void testGpsPointMapping() throws Exception {
        // Test the actual mapping by importing OwnTracks data
        OwnTracksLocationMessage message = createOwnTracksMessage(
            37.7749, -122.4194, Instant.now());

        String jsonContent = objectMapper.writeValueAsString(new OwnTracksLocationMessage[]{message});
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "mapping.json", jsonData);

        // Import and verify mapping
        importDataService.processImportData(job);
        
        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserAndDateRange(
            testUser.getId(), 
            Instant.now().minus(1, ChronoUnit.HOURS),
            Instant.now().plus(1, ChronoUnit.HOURS),
            0, 10, "timestamp", "asc"
        );
        
        assertEquals(1, importedPoints.size());
        GpsPointEntity mapped = importedPoints.get(0);
        
        assertNotNull(mapped);
        assertEquals(testUser.getId(), mapped.getUser().getId());
        assertEquals(message.getLat(), mapped.getLatitude(), 0.000001);
        assertEquals(message.getLon(), mapped.getLongitude(), 0.000001);
        assertEquals(message.getAcc(), mapped.getAccuracy());
        assertEquals(message.getBatt(), mapped.getBattery());
        assertEquals(message.getVel(), mapped.getVelocity());
        assertEquals(message.getAlt(), mapped.getAltitude());
        assertEquals(message.getTid(), mapped.getDeviceId());
        assertEquals(GpsSourceType.OWNTRACKS, mapped.getSourceType());
        assertNotNull(mapped.getCreatedAt());
    }

    @Test
    @Transactional
    void testDuplicateDetectionCriteria() throws Exception {
        Instant baseTime = Instant.now();
        
        // Create original GPS point by importing first
        OwnTracksLocationMessage originalMessage = createOwnTracksMessage(37.7749, -122.4194, baseTime);
        String jsonContent = objectMapper.writeValueAsString(new OwnTracksLocationMessage[]{originalMessage});
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "original.json", jsonData);
        importDataService.processImportData(job);
        
        // Test duplicate scenarios by importing additional data
        testDuplicateScenario(baseTime.plusSeconds(1), -122.4194, 37.7749, true, 
            "Points 1 second apart at same location should be duplicates");
        
        testDuplicateScenario(baseTime.plusSeconds(10), -122.4194, 37.7749, false, 
            "Points 10 seconds apart should not be duplicates");
        
        testDuplicateScenario(baseTime.plusSeconds(1), -122.4200, 37.7749, false, 
            "Points at different locations should not be duplicates");
        
        testDuplicateScenario(baseTime.plusSeconds(1), -122.4194001, 37.7749001, true, 
            "Points very close together should be duplicates");
    }

    private void testDuplicateScenario(Instant newTime, double newLon, double newLat, 
                                     boolean shouldBeDuplicate, String description) throws Exception {
        // Get current count of GPS points
        List<GpsPointEntity> beforeImport = gpsPointRepository.findByUserAndDateRange(
            testUser.getId(), 
            Instant.now().minus(1, ChronoUnit.HOURS),
            Instant.now().plus(1, ChronoUnit.HOURS),
            0, 100, "timestamp", "asc"
        );
        int countBefore = beforeImport.size();
        
        // Try to import potentially duplicate point
        OwnTracksLocationMessage duplicateMessage = createOwnTracksMessage(newLat, newLon, newTime);
        String jsonContent = objectMapper.writeValueAsString(new OwnTracksLocationMessage[]{duplicateMessage});
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "duplicate-test.json", jsonData);
        importDataService.processImportData(job);
        
        // Check if point was added or detected as duplicate
        List<GpsPointEntity> afterImport = gpsPointRepository.findByUserAndDateRange(
            testUser.getId(), 
            Instant.now().minus(1, ChronoUnit.HOURS),
            Instant.now().plus(1, ChronoUnit.HOURS),
            0, 100, "timestamp", "asc"
        );
        int countAfter = afterImport.size();
        
        if (shouldBeDuplicate) {
            assertEquals(countBefore, countAfter, description + " - Point should be detected as duplicate");
        } else {
            assertEquals(countBefore + 1, countAfter, description + " - Point should be imported as new");
        }
    }

    @Test
    @Transactional
    void testDataTypeDetection() throws Exception {
        // Create valid OwnTracks data
        OwnTracksLocationMessage[] messages = {
            createOwnTracksMessage(37.7749, -122.4194, Instant.now())
        };

        String jsonContent = objectMapper.writeValueAsString(messages);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "detection.json", jsonData);

        // Test data type detection
        List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(job);
        
        assertEquals(1, detectedDataTypes.size());
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS));
        assertFalse(detectedDataTypes.contains(ExportImportConstants.DataTypes.TIMELINE));
        assertFalse(detectedDataTypes.contains(ExportImportConstants.DataTypes.FAVORITES));
    }

    @Test
    @Transactional
    void testBatchProcessing() throws Exception {
        // Create a larger set of OwnTracks messages to test batch processing
        int totalMessages = 150; // Smaller number for test performance
        OwnTracksLocationMessage[] messages = new OwnTracksLocationMessage[totalMessages];
        
        Instant baseTime = Instant.now().minus(totalMessages, ChronoUnit.MINUTES);
        
        for (int i = 0; i < totalMessages; i++) {
            // Create messages with slight variations to avoid duplicates
            double lat = 37.7749 + (i * 0.0001);
            double lon = -122.4194 + (i * 0.0001);
            Instant timestamp = baseTime.plus(i, ChronoUnit.MINUTES);
            messages[i] = createOwnTracksMessage(lat, lon, timestamp);
        }

        String jsonContent = objectMapper.writeValueAsString(messages);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "batch-test.json", jsonData);

        // Test batch processing
        assertDoesNotThrow(() -> {
            importDataService.processImportData(job);
            
            // Verify all messages were imported
            List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserAndDateRange(
                testUser.getId(), 
                baseTime.minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS),
                0, 200, "timestamp", "asc"
            );
            
            assertEquals(totalMessages, importedPoints.size(), "All messages should be imported through batch processing");
        });
    }

    @Test
    @Transactional
    void testLargeDatasetImport() throws Exception {
        // Test importing a moderately large dataset to verify performance
        int totalMessages = 50; // Reasonable size for integration test
        OwnTracksLocationMessage[] messages = new OwnTracksLocationMessage[totalMessages];
        
        Instant baseTime = Instant.now().minus(totalMessages, ChronoUnit.MINUTES);
        
        for (int i = 0; i < totalMessages; i++) {
            // Create messages with geographical progression to simulate real movement
            double lat = 37.7749 + (i * 0.001);  // Move north
            double lon = -122.4194 + (i * 0.001); // Move east
            Instant timestamp = baseTime.plus(i, ChronoUnit.MINUTES);
            messages[i] = createOwnTracksMessage(lat, lon, timestamp);
        }

        String jsonContent = objectMapper.writeValueAsString(messages);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = new ImportJob(testUser.getId(), options, "large-dataset.json", jsonData);

        // Measure import time and verify completion
        long startTime = System.currentTimeMillis();
        importDataService.processImportData(job);
        long importTime = System.currentTimeMillis() - startTime;
        
        log.info("Imported {} messages in {} ms", totalMessages, importTime);
        
        // Verify all data was imported correctly
        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserAndDateRange(
            testUser.getId(), 
            baseTime.minus(1, ChronoUnit.HOURS),
            Instant.now().plus(1, ChronoUnit.HOURS),
            0, 100, "timestamp", "asc"
        );
        
        assertEquals(totalMessages, importedPoints.size(), "All messages should be imported");
        
        // Verify geographical progression
        importedPoints.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        assertTrue(importedPoints.get(0).getLatitude() < importedPoints.get(totalMessages - 1).getLatitude(), 
                  "Latitude should increase over time");
        assertTrue(importedPoints.get(0).getLongitude() < importedPoints.get(totalMessages - 1).getLongitude(), 
                  "Longitude should increase over time");
    }

    private OwnTracksLocationMessage createOwnTracksMessage(double lat, double lon, Instant timestamp) {
        OwnTracksLocationMessage message = new OwnTracksLocationMessage();
        message.setLat(lat);
        message.setLon(lon);
        message.setTst(timestamp.getEpochSecond());
        message.setAcc(5.0);
        message.setBatt(85.0);
        message.setVel(0.0);
        message.setAlt(100.0);
        message.setTid("test-device");
        message.setType("location");
        return message;
    }
}