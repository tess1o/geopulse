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
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.service.ExportDataGenerator;
import org.github.tess1o.geopulse.export.service.ExportJobManager;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.importdata.service.ImportService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for OwnTracks export/import functionality with duplicate handling.
 * Tests the complete lifecycle:
 * 1) Insert test GPS data
 * 2) Export as OwnTracks JSON format
 * 3) Import the same data back (should handle duplicates)
 * 4) Import modified data (should update existing points)
 * 5) Verify duplicate detection and data integrity
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class OwnTracksExportImportIntegrationTest {

    @Inject
    ExportJobManager exportJobManager;

    @Inject
    ExportDataGenerator exportDataGenerator;

    @Inject
    ImportService importService;

    @Inject
    ImportDataService importDataService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    CleanupHelper cleanupHelper;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private UserEntity testUser;
    private List<GpsPointEntity> testGpsPoints;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        cleanupTestData();

        // Create test user - find existing or create new
        testUser = userRepository.find("email", "test-owntracks@geopulse.app").firstResult();
        if (testUser == null) {
            testUser = new UserEntity();
            testUser.setEmail("test-owntracks@geopulse.app");
            testUser.setFullName("OwnTracks Test User");
            testUser.setPasswordHash("test-hash");
            testUser.setCreatedAt(Instant.now());
            userRepository.persist(testUser);
        }

        // Create test GPS points with various scenarios
        createTestGpsData();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Transactional
    void cleanupTestData() {
        cleanupHelper.cleanupAll();
        gpsPointRepository.delete("user.email = ?1", "test-owntracks@geopulse.app");
        userRepository.delete("email = ?1", "test-owntracks@geopulse.app");
    }

    @Transactional
    void createTestGpsData() {
        // Use whole-second timestamps to ensure proper duplicate detection
        // OwnTracks format uses epoch seconds (no subsecond precision), so we must use whole seconds
        // to make export/import cycles produce exact duplicates
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        testGpsPoints = Arrays.asList(
                // Point 1: Basic GPS point
                createGpsPoint(
                        now.minus(3, ChronoUnit.HOURS),
                        37.7749, -122.4194, // San Francisco
                        5.0, 100.0, 0.0, 85.0, "device-1"
                ),
                // Point 2: GPS point with higher accuracy
                createGpsPoint(
                        now.minus(2, ChronoUnit.HOURS),
                        37.7849, -122.4094, // Slightly different location
                        3.0, 110.0, 2.5, 80.0, "device-1"
                ),
                // Point 3: GPS point with missing optional data
                createGpsPoint(
                        now.minus(1, ChronoUnit.HOURS),
                        37.7949, -122.3994, // Another location
                        null, null, null, null, "device-2"
                ),
                // Point 4: Recent GPS point
                createGpsPoint(
                        now.minus(30, ChronoUnit.MINUTES),
                        37.8049, -122.3894, // Most recent location
                        2.0, 120.0, 5.0, 75.0, "device-1"
                )
        );

        testGpsPoints.forEach(gpsPointRepository::persist);
        log.info("Created {} test GPS points", testGpsPoints.size());
    }

    private GpsPointEntity createGpsPoint(Instant timestamp, double lat, double lon,
                                          Double accuracy, Double altitude, Double velocity,
                                          Double battery, String deviceId) {
        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(testUser);
        gpsPoint.setTimestamp(timestamp);
        gpsPoint.setCoordinates(GeoUtils.createPoint(lon, lat));
        gpsPoint.setAccuracy(accuracy);
        gpsPoint.setAltitude(altitude);
        gpsPoint.setVelocity(velocity);
        gpsPoint.setBattery(battery);
        gpsPoint.setDeviceId(deviceId);
        gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPoint.setCreatedAt(Instant.now());
        return gpsPoint;
    }

    @Test
    @Transactional
    void testOwnTracksExportImportCycle() throws Exception {
        log.info("=== Starting OwnTracks Export/Import Integration Test ===");

        // Step 1: Export GPS data as OwnTracks format
        log.info("Step 1: Exporting GPS data as OwnTracks JSON");

        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
        dateRange.setEndDate(Instant.now().plus(1, ChronoUnit.HOURS));

        ExportJob exportJob = exportJobManager.createOwnTracksExportJob(testUser.getId(), dateRange);

        // Process the export job
        byte[] exportedJsonData = exportDataGenerator.generateOwnTracksExport(exportJob);
        assertNotNull(exportedJsonData);
        assertTrue(exportedJsonData.length > 0);

        // Verify it's valid JSON array
        String jsonContent = new String(exportedJsonData);
        OwnTracksLocationMessage[] exportedMessages = objectMapper.readValue(jsonContent, OwnTracksLocationMessage[].class);
        assertEquals(testGpsPoints.size(), exportedMessages.length, "Exported message count should match GPS points");

        log.info("Export completed: {} bytes, {} OwnTracks messages", exportedJsonData.length, exportedMessages.length);

        // Step 2: Verify exported data structure
        log.info("Step 2: Verifying exported OwnTracks data structure");

        for (OwnTracksLocationMessage message : exportedMessages) {
            assertNotNull(message.getLat(), "Latitude should not be null");
            assertNotNull(message.getLon(), "Longitude should not be null");
            assertNotNull(message.getTst(), "Timestamp should not be null");
            assertTrue(message.getLat() > 37.7 && message.getLat() < 37.9, "Latitude should be in expected range");
            assertTrue(message.getLon() > -122.5 && message.getLon() < -122.3, "Longitude should be in expected range");
        }

        // Step 3: Import the same data back (duplicate scenario)
        log.info("Step 3: Importing the same OwnTracks data back (testing duplicate handling)");

        long originalGpsCount = gpsPointRepository.count("user = ?1", testUser);
        log.info("Original GPS point count: {}", originalGpsCount);

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("owntracks");

        ImportJob importJob = importService.createOwnTracksImportJob(
                testUser.getId(), importOptions, "test-owntracks.json", exportedJsonData);

        // Validate the import data
        List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(importJob);
        assertEquals(1, detectedDataTypes.size());
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS));

        // Process the import
        importDataService.processImportData(importJob);

        // Verify no duplicates were created
        long afterImportGpsCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(originalGpsCount, afterImportGpsCount,
                "GPS point count should remain the same after importing duplicates");

        log.info("Duplicate import completed: {} GPS points (no duplicates created)", afterImportGpsCount);

        // Step 4: Verify that re-importing with modified metadata doesn't create duplicates or update points
        // The system uses ON CONFLICT DO NOTHING - exact duplicates are skipped (not updated)
        log.info("Step 4: Verify duplicate detection with modified metadata (should still skip)");

        // Modify the exported data - same timestamp/coordinates, but better metadata
        OwnTracksLocationMessage[] modifiedMessages = Arrays.copyOf(exportedMessages, exportedMessages.length);
        for (int i = 0; i < modifiedMessages.length; i++) {
            OwnTracksLocationMessage message = modifiedMessages[i];
            // Improve accuracy for some points
            if (message.getAcc() == null || message.getAcc() > 3.0) {
                message.setAcc(1.0); // Better accuracy
            }
            // Add battery info if missing
            if (message.getBatt() == null) {
                message.setBatt(90.0);
            }
            // Add altitude if missing
            if (message.getAlt() == null) {
                message.setAlt(50.0);
            }
        }

        String modifiedJsonContent = objectMapper.writeValueAsString(modifiedMessages);
        byte[] modifiedJsonData = modifiedJsonContent.getBytes();

        ImportJob modifiedImportJob = importService.createOwnTracksImportJob(
                testUser.getId(), importOptions, "test-owntracks-modified.json", modifiedJsonData);

        importDataService.processImportData(modifiedImportJob);

        // Verify duplicates were skipped (not updated) - count should remain the same
        long afterModifiedImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(originalGpsCount, afterModifiedImportCount,
                "GPS point count should remain the same - duplicates skipped via ON CONFLICT DO NOTHING");

        log.info("Modified data import completed: {} GPS points (duplicates correctly skipped)", afterModifiedImportCount);

        log.info("=== OwnTracks Export/Import Integration Test Completed Successfully ===");
    }

    @Test
    @Transactional
    void testOwnTracksImportWithDateRangeFilter() throws Exception {
        log.info("=== Testing OwnTracks Import with Date Range Filter ===");

        // Export all data first
        ExportDateRange fullDateRange = new ExportDateRange();
        fullDateRange.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
        fullDateRange.setEndDate(Instant.now().plus(1, ChronoUnit.HOURS));

        ExportJob exportJob = exportJobManager.createOwnTracksExportJob(testUser.getId(), fullDateRange);
        byte[] exportedJsonData = exportDataGenerator.generateOwnTracksExport(exportJob);

        // Clear existing GPS data
        gpsPointRepository.delete("user = ?1", testUser);

        long clearedCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(0, clearedCount, "All GPS points should be cleared");

        // Import with date range filter (only last 2 hours)
        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("owntracks");

        ExportDateRange filterDateRange = new ExportDateRange();
        filterDateRange.setStartDate(Instant.now().minus(2, ChronoUnit.HOURS));
        filterDateRange.setEndDate(Instant.now().plus(1, ChronoUnit.HOURS));
        importOptions.setDateRangeFilter(filterDateRange);

        ImportJob importJob = importService.createOwnTracksImportJob(
                testUser.getId(), importOptions, "test-owntracks-filtered.json", exportedJsonData);

        importDataService.processImportData(importJob);

        // Verify only recent points were imported
        long importedCount = gpsPointRepository.count("user = ?1", testUser);
        assertTrue(importedCount < testGpsPoints.size(),
                "Filtered import should import fewer points than original");
        assertTrue(importedCount >= 2, "At least 2 recent points should be imported");

        // Verify imported points are within the date range
        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                filterDateRange.getStartDate(),
                filterDateRange.getEndDate()
        );

        assertEquals(importedCount, importedPoints.size(),
                "All imported points should be within the filter date range");

        log.info("Date range filter test completed: imported {} out of {} points",
                importedCount, testGpsPoints.size());
    }

    @Test
    @Transactional
    void testOwnTracksImportInvalidData() throws Exception {
        log.info("=== Testing OwnTracks Import with Invalid Data ===");

        // Test 1: Invalid JSON format
        byte[] invalidJsonData = "{ invalid json }".getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("owntracks");

        ImportJob invalidJsonJob = importService.createOwnTracksImportJob(
                testUser.getId(), importOptions, "invalid.json", invalidJsonData);

        assertThrows(IllegalArgumentException.class, () -> {
            importDataService.validateAndDetectDataTypes(invalidJsonJob);
        }, "Invalid JSON should throw validation exception");

        // Test 2: Empty JSON array
        byte[] emptyArrayData = "[]".getBytes();

        ImportJob emptyArrayJob = importService.createOwnTracksImportJob(
                testUser.getId(), importOptions, "empty.json", emptyArrayData);

        assertThrows(IllegalArgumentException.class, () -> {
            importDataService.validateAndDetectDataTypes(emptyArrayJob);
        }, "Empty array should throw validation exception");

        // Test 3: JSON array with invalid GPS data
        String invalidGpsJson = "[{\"_type\":\"location\"}, {\"lat\":null,\"lon\":null,\"tst\":123}]";
        byte[] invalidGpsData = invalidGpsJson.getBytes();

        ImportJob invalidGpsJob = importService.createOwnTracksImportJob(
                testUser.getId(), importOptions, "invalid-gps.json", invalidGpsData);

        assertThrows(IllegalArgumentException.class, () -> {
            importDataService.validateAndDetectDataTypes(invalidGpsJob);
        }, "Invalid GPS coordinates should throw validation exception");

        log.info("Invalid data test completed successfully");
    }

    @Test
    @Transactional
    void testSpatialDuplicateDetection() throws Exception {
        log.info("=== Testing Exact Duplicate Detection ===");

        // Create OwnTracks data with EXACT same timestamp and coordinates (exact duplicates)
        // Duplicate detection uses unique constraint: (user_id, timestamp, coordinates)
        // Only EXACT matches are considered duplicates
        OwnTracksLocationMessage[] exactDuplicateMessages = testGpsPoints.stream()
                .map(gpsPoint -> {
                    OwnTracksLocationMessage message = new OwnTracksLocationMessage();
                    message.setLat(gpsPoint.getLatitude()); // EXACT same latitude
                    message.setLon(gpsPoint.getLongitude()); // EXACT same longitude
                    message.setTst(gpsPoint.getTimestamp().getEpochSecond()); // EXACT same timestamp
                    message.setAcc(2.0); // Better accuracy
                    message.setType("location");
                    return message;
                }).toArray(OwnTracksLocationMessage[]::new);

        String exactDuplicateJson = objectMapper.writeValueAsString(exactDuplicateMessages);
        byte[] exactDuplicateData = exactDuplicateJson.getBytes();

        long originalCount = gpsPointRepository.count("user = ?1", testUser);

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("owntracks");

        ImportJob exactDuplicateJob = importService.createOwnTracksImportJob(
                testUser.getId(), importOptions, "exact-duplicates.json", exactDuplicateData);

        importDataService.processImportData(exactDuplicateJob);

        // Should detect as exact duplicates and skip them (ON CONFLICT DO NOTHING)
        long afterDuplicateCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(originalCount, afterDuplicateCount,
                "Exact duplicate points (same timestamp and coordinates) should be skipped");

        log.info("Exact duplicate detection test completed: {} points remain (duplicates skipped)", afterDuplicateCount);
    }

    @Test
    @Transactional
    void testBatchProcessingLargeDataset() throws Exception {
        log.info("=== Testing Batch Processing with Large Dataset ===");

        // Create a large dataset (2500 points to test batch processing)
        OwnTracksLocationMessage[] largeDataset = new OwnTracksLocationMessage[2500];
        Instant baseTime = Instant.now().minus(1, ChronoUnit.DAYS);

        for (int i = 0; i < largeDataset.length; i++) {
            OwnTracksLocationMessage message = new OwnTracksLocationMessage();
            // Use different coordinates far from setup test data (which is around 37.77-37.80)
            message.setLat(40.0000 + (i * 0.001)); // Far from SF test data
            message.setLon(-120.0000 + (i * 0.001));
            message.setTst(baseTime.plusSeconds(i * 10).getEpochSecond()); // 10 seconds apart
            message.setAcc(5.0);
            message.setBatt(85.0);
            message.setTid("large-dataset-device");
            message.setType("location");
            largeDataset[i] = message;
        }

        String largeDatasetJson = objectMapper.writeValueAsString(largeDataset);
        byte[] largeDatasetData = largeDatasetJson.getBytes();

        // Clear ALL GPS points to ensure clean test environment
        long allGpsPointsBefore = gpsPointRepository.count();
        log.info("Total GPS points in database before cleanup: {}", allGpsPointsBefore);

        long deletedCount = gpsPointRepository.delete("user.email = ?1", "test-owntracks@geopulse.app");
        log.info("Deleted {} GPS points before large dataset test", deletedCount);

        // Verify cleanup worked
        long allGpsPointsAfter = gpsPointRepository.count();
        assertEquals(0, allGpsPointsAfter, "Should have no GPS points before large dataset test");

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("owntracks");

        ImportJob largeDatasetJob = importService.createOwnTracksImportJob(
                testUser.getId(), importOptions, "large-dataset.json", largeDatasetData);


        // Validate large dataset
        List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(largeDatasetJob);
        assertEquals(1, detectedDataTypes.size());

        // Process large dataset (should use batch processing)
        long startTime = System.currentTimeMillis();
        importDataService.processImportData(largeDatasetJob);
        long endTime = System.currentTimeMillis();

        // Verify all points were imported (count only this user's points)
        long importedCount = gpsPointRepository.findAll().stream()
                .filter(p -> p.getDeviceId().equals("large-dataset-device"))
                .count();
        assertEquals(largeDataset.length, importedCount,
                "All points from large dataset should be imported");

        log.info("Large dataset processing completed: {} points in {} ms ({} points/sec)",
                importedCount, endTime - startTime,
                Math.round((double) importedCount / (endTime - startTime) * 1000));

        // Verify batch processing didn't create duplicates by importing again
        importDataService.processImportData(largeDatasetJob);

        long afterDuplicateImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(importedCount, afterDuplicateImportCount,
                "Re-importing large dataset should not create duplicates");

        log.info("Batch processing duplicate detection verified");
    }
}