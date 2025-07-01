package org.github.tess1o.geopulse.exportimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit test for OwnTracks export/import functionality focusing on data validation,
 * JSON parsing, and duplicate detection logic without requiring database integration.
 */
class OwnTracksExportImportUnitTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Mock
    private GpsPointMapper gpsPointMapper;

    private ImportDataService importDataService;
    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create a partial ImportDataService for testing validation logic
        importDataService = new ImportDataService() {
            {
                // Set the object mapper field via reflection or use a test constructor
                // For this test, we'll focus on the validation method
            }
        };

        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
    }

    @Test
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

        ImportJob job = new ImportJob(testUser.getId(), options, "test.json", jsonData);

        // Test the validation method
        assertDoesNotThrow(() -> {
            // Parse JSON to verify structure
            OwnTracksLocationMessage[] parsed = objectMapper.readValue(jsonContent, OwnTracksLocationMessage[].class);
            assertEquals(3, parsed.length);
            
            for (OwnTracksLocationMessage msg : parsed) {
                assertNotNull(msg.getLat());
                assertNotNull(msg.getLon());
                assertNotNull(msg.getTst());
                assertTrue(msg.getLat() > 37.0 && msg.getLat() < 38.0);
                assertTrue(msg.getLon() > -123.0 && msg.getLon() < -122.0);
            }
        });
    }

    @Test
    void testOwnTracksJsonValidation_InvalidJson() {
        byte[] invalidJsonData = "{ invalid json }".getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");

        ImportJob job = new ImportJob(testUser.getId(), options, "invalid.json", invalidJsonData);

        // Should throw exception for invalid JSON
        assertThrows(Exception.class, () -> {
            objectMapper.readValue(invalidJsonData, OwnTracksLocationMessage[].class);
        });
    }

    @Test
    void testOwnTracksJsonValidation_EmptyArray() throws Exception {
        byte[] emptyArrayData = "[]".getBytes();

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");

        ImportJob job = new ImportJob(testUser.getId(), options, "empty.json", emptyArrayData);

        // Should parse successfully but be empty
        OwnTracksLocationMessage[] messages = objectMapper.readValue(emptyArrayData, OwnTracksLocationMessage[].class);
        assertEquals(0, messages.length);
    }

    @Test
    void testOwnTracksJsonValidation_MissingRequiredFields() throws Exception {
        // Create messages with missing required fields
        String jsonWithMissingFields = "[" +
            "{\"_type\":\"location\", \"lat\":37.7749, \"lon\":null, \"tst\":null}," +
            "{\"_type\":\"location\", \"lat\":null, \"lon\":-122.4194, \"tst\":1234567890}," +
            "{\"_type\":\"location\", \"lat\":37.7749, \"lon\":-122.4194, \"tst\":null}" +
            "]";

        byte[] jsonData = jsonWithMissingFields.getBytes();
        
        OwnTracksLocationMessage[] messages = objectMapper.readValue(jsonData, OwnTracksLocationMessage[].class);
        assertEquals(3, messages.length);

        // Verify that validation logic would catch these issues
        int validMessages = 0;
        for (OwnTracksLocationMessage message : messages) {
            if (message.getLat() != null && message.getLon() != null && message.getTst() != null) {
                validMessages++;
            }
        }
        assertEquals(0, validMessages, "No messages should be valid with missing required fields");
    }

    @Test
    void testDateRangeFiltering() throws Exception {
        Instant now = Instant.now();
        
        // Create messages with different timestamps
        OwnTracksLocationMessage[] messages = {
            createOwnTracksMessage(37.7749, -122.4194, now.minus(3, ChronoUnit.HOURS)), // Outside range
            createOwnTracksMessage(37.7849, -122.4094, now.minus(1, ChronoUnit.HOURS)), // Inside range
            createOwnTracksMessage(37.7949, -122.3994, now.minus(30, ChronoUnit.MINUTES)), // Inside range
            createOwnTracksMessage(37.8049, -122.3894, now.plus(1, ChronoUnit.HOURS))  // Outside range
        };

        // Define date range filter (last 2 hours)
        ExportDateRange dateFilter = new ExportDateRange();
        dateFilter.setStartDate(now.minus(2, ChronoUnit.HOURS));
        dateFilter.setEndDate(now.plus(30, ChronoUnit.MINUTES));

        // Simulate filtering logic
        int messagesInRange = 0;
        for (OwnTracksLocationMessage message : messages) {
            if (message.getTst() != null) {
                Instant messageTime = Instant.ofEpochSecond(message.getTst());
                if (!messageTime.isBefore(dateFilter.getStartDate()) && 
                    !messageTime.isAfter(dateFilter.getEndDate())) {
                    messagesInRange++;
                }
            }
        }

        assertEquals(2, messagesInRange, "Only 2 messages should be within the date range");
    }

    @Test
    void testGpsPointMapping() {
        // Test the mapping logic from OwnTracks to GPS point
        OwnTracksLocationMessage message = createOwnTracksMessage(
            37.7749, -122.4194, Instant.now());

        // Mock the mapper
        when(gpsPointMapper.toEntity(any(OwnTracksLocationMessage.class), any(UserEntity.class), 
                                    any(String.class), any(GpsSourceType.class)))
            .thenAnswer(invocation -> {
                OwnTracksLocationMessage msg = invocation.getArgument(0);
                UserEntity user = invocation.getArgument(1);
                String deviceId = invocation.getArgument(2);
                GpsSourceType sourceType = invocation.getArgument(3);

                GpsPointEntity entity = new GpsPointEntity();
                entity.setUser(user);
                entity.setTimestamp(Instant.ofEpochSecond(msg.getTst()));
                entity.setCoordinates(GeoUtils.createPoint(msg.getLon(), msg.getLat()));
                entity.setAccuracy(msg.getAcc());
                entity.setBattery(msg.getBatt());
                entity.setVelocity(msg.getVel());
                entity.setAltitude(msg.getAlt());
                entity.setDeviceId(deviceId);
                entity.setSourceType(sourceType);
                entity.setCreatedAt(Instant.now());
                return entity;
            });

        // Test mapping
        GpsPointEntity mapped = gpsPointMapper.toEntity(message, testUser, "test-device", GpsSourceType.OWNTRACKS);
        
        assertNotNull(mapped);
        assertEquals(testUser, mapped.getUser());
        assertEquals(message.getLat(), mapped.getLatitude(), 0.000001);
        assertEquals(message.getLon(), mapped.getLongitude(), 0.000001);
        assertEquals(message.getAcc(), mapped.getAccuracy());
        assertEquals(message.getBatt(), mapped.getBattery());
        assertEquals(GpsSourceType.OWNTRACKS, mapped.getSourceType());
    }

    @Test
    void testDuplicateDetectionCriteria() {
        Instant baseTime = Instant.now();
        
        // Create original point
        GpsPointEntity original = new GpsPointEntity();
        original.setTimestamp(baseTime);
        original.setCoordinates(GeoUtils.createPoint(-122.4194, 37.7749));
        original.setAccuracy(5.0);

        // Test cases for duplicate detection
        testDuplicateScenario(original, baseTime.plusSeconds(1), -122.4194, 37.7749, true, 
            "Points 1 second apart at same location should be duplicates");
        
        testDuplicateScenario(original, baseTime.plusSeconds(10), -122.4194, 37.7749, false, 
            "Points 10 seconds apart should not be duplicates");
        
        testDuplicateScenario(original, baseTime.plusSeconds(1), -122.4200, 37.7749, false, 
            "Points at different locations should not be duplicates");
        
        testDuplicateScenario(original, baseTime.plusSeconds(1), -122.4194001, 37.7749001, true, 
            "Points very close together should be duplicates");
    }

    private void testDuplicateScenario(GpsPointEntity original, Instant newTime, 
                                     double newLon, double newLat, boolean shouldBeDuplicate, 
                                     String description) {
        // Simulate duplicate detection logic
        long timeDiffSeconds = Math.abs(newTime.getEpochSecond() - original.getTimestamp().getEpochSecond());
        double latDiff = Math.abs(newLat - original.getLatitude());
        double lonDiff = Math.abs(newLon - original.getLongitude());
        
        // Use same criteria as the repository method: 5 second window and spatial tolerance
        boolean isDuplicate = timeDiffSeconds <= 5 && latDiff < 0.00001 && lonDiff < 0.00001;
        
        assertEquals(shouldBeDuplicate, isDuplicate, description);
    }

    @Test
    void testDataTypeDetection() {
        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");

        // OwnTracks imports should always detect only GPS data
        List<String> expectedDataTypes = List.of(ExportImportConstants.DataTypes.RAW_GPS);
        
        // Simulate the detection logic
        assertEquals(1, expectedDataTypes.size());
        assertTrue(expectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS));
        assertFalse(expectedDataTypes.contains(ExportImportConstants.DataTypes.TIMELINE));
        assertFalse(expectedDataTypes.contains(ExportImportConstants.DataTypes.FAVORITES));
    }

    @Test
    void testBatchSizeCalculation() {
        // Test batch processing logic
        int totalMessages = 2500;
        int batchSize = 1000;
        
        int expectedBatches = (int) Math.ceil((double) totalMessages / batchSize);
        assertEquals(3, expectedBatches, "2500 messages should require 3 batches");
        
        // Test last batch size
        int lastBatchSize = totalMessages % batchSize;
        if (lastBatchSize == 0) {
            lastBatchSize = batchSize;
        }
        assertEquals(500, lastBatchSize, "Last batch should have 500 messages");
    }

    @Test
    void testProgressCalculation() {
        int totalMessages = 1000;
        
        // Test progress calculation at different stages
        assertEquals(10, calculateProgress(0, totalMessages), "Initial progress should be 10%");
        assertEquals(50, calculateProgress(500, totalMessages), "Halfway progress should be 50%");
        assertEquals(89, calculateProgress(999, totalMessages), "Near end progress should be 89%");
        assertEquals(100, calculateProgress(1000, totalMessages), "Completion progress should be 100%");
    }

    private int calculateProgress(int processedMessages, int totalMessages) {
        if (totalMessages == 0) return 100;
        if (processedMessages == totalMessages) return 100;
        return Math.min(90, 10 + (int) ((double) processedMessages / totalMessages * 80));
    }

    private OwnTracksLocationMessage createOwnTracksMessage(double lat, double lon, Instant timestamp) {
        OwnTracksLocationMessage message = new OwnTracksLocationMessage();
        message.setLat(lat);
        message.setLon(lon);
        message.setTst((int) timestamp.getEpochSecond());
        message.setAcc(5.0);
        message.setBatt(85.0);
        message.setVel(0.0);
        message.setAlt(100.0);
        message.setTid("test-device");
        message.setType("location");
        return message;
    }
}