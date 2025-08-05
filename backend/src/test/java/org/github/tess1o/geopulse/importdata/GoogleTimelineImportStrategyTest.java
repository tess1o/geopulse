package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.*;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.GoogleTimelineImportStrategy;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.importdata.service.ImportService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Google Timeline import functionality.
 * Tests the complete parsing and import cycle using real database operations.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class GoogleTimelineImportStrategyTest {

    @Inject
    GoogleTimelineImportStrategy googleTimelineImportStrategy;

    @Inject
    ImportService importService;

    @Inject
    ImportDataService importDataService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    org.github.tess1o.geopulse.timeline.repository.TimelineRegenerationTaskRepository timelineRegenerationTaskRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        cleanupTestData();

        // Create test user
        testUser = userRepository.find("email", "test-googletimeline@geopulse.app").firstResult();
        if (testUser == null) {
            testUser = new UserEntity();
            testUser.setEmail("test-googletimeline@geopulse.app");
            testUser.setFullName("Google Timeline Test User");
            testUser.setPasswordHash("test-hash");
            testUser.setCreatedAt(Instant.now());
            userRepository.persist(testUser);
        }
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Transactional
    void cleanupTestData() {
        // Clean up timeline regeneration queue first to avoid foreign key constraint violations
        if (testUser != null) {
            timelineRegenerationTaskRepository.deleteByUserId(testUser.getId());
        }
        timelineRegenerationTaskRepository.delete("user.email = ?1", "test-googletimeline@geopulse.app");
        timelineStayRepository.delete("user.email = ?1", "test-googletimeline@geopulse.app");
        gpsPointRepository.delete("user.email = ?1", "test-googletimeline@geopulse.app");
        userRepository.delete("email = ?1", "test-googletimeline@geopulse.app");
    }

    @Test
    @Transactional
    void testGoogleTimelineImportWithAllRecordTypes() throws Exception {
        log.info("=== Testing Google Timeline Import with All Record Types ===");

        // Create test data matching the format from the user's example
        List<GoogleTimelineRecord> testRecords = createTestTimelineRecords();
        String jsonContent = objectMapper.writeValueAsString(testRecords);
        byte[] jsonData = jsonContent.getBytes();

        log.info("Created test data: {} records, {} bytes", testRecords.size(), jsonData.length);

        // Validate the data
        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("google-timeline");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-google-timeline.json", jsonData);

        List<String> detectedDataTypes = googleTimelineImportStrategy.validateAndDetectDataTypes(importJob);
        assertEquals(1, detectedDataTypes.size());
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS));

        log.info("Validation successful, detected data types: {}", detectedDataTypes);

        // Process the import
        long beforeImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(0, beforeImportCount, "Should start with no GPS points");

        googleTimelineImportStrategy.processImportData(importJob);

        // Verify import results
        long afterImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertTrue(afterImportCount > 0, "Should have imported GPS points");

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        log.info("Import completed: {} GPS points imported", afterImportCount);

        // Verify different source types and data integrity
        verifyImportedData(importedPoints);
    }

    @Test
    @Transactional
    void testActivityRecordProcessing() throws Exception {
        log.info("=== Testing Activity Record Processing ===");

        // Create a single activity record
        GoogleTimelineRecord activityRecord = createActivityRecord();
        List<GoogleTimelineRecord> records = List.of(activityRecord);

        String jsonContent = objectMapper.writeValueAsString(records);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("google-timeline");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-activity.json", jsonData);

        googleTimelineImportStrategy.processImportData(importJob);

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Should have 2 points: start and end of activity
        assertEquals(2, importedPoints.size(), "Activity should generate start and end points");

        // Verify velocity conversion from m/s to km/h
        GpsPointEntity pointWithVelocity = importedPoints.stream()
                .filter(p -> p.getVelocity() != null)
                .findFirst()
                .orElse(null);

        assertNotNull(pointWithVelocity, "Should have at least one point with velocity");
        
        // Original velocity in Python is calculated as distance/duration_seconds (m/s)
        // We should convert to km/h by multiplying by 3.6
        // Since we're using dynamic timestamps now, just verify the conversion was applied (> 0 and reasonable)
        assertTrue(pointWithVelocity.getVelocity() > 0, "Velocity should be positive");
        assertTrue(pointWithVelocity.getVelocity() < 100, "Velocity should be reasonable (< 100 km/h)");
        
        // For more precise test, calculate expected velocity from actual timestamps
        // Duration should be 30 minutes (1800 seconds) based on our test data
        double expectedDurationSeconds = 30 * 60; // 30 minutes
        double distanceMeters = 4340.700195;
        double expectedVelocityKmh = (distanceMeters / expectedDurationSeconds) * 3.6;
        assertTrue(Math.abs(pointWithVelocity.getVelocity() - expectedVelocityKmh) < 1.0,
                String.format("Velocity should be converted to km/h. Expected: %.2f, Got: %.2f", 
                expectedVelocityKmh, pointWithVelocity.getVelocity()));

        log.info("Activity processing verified: velocity properly converted to km/h");
    }

    @Test
    @Transactional
    void testVisitRecordProcessing() throws Exception {
        log.info("=== Testing Visit Record Processing ===");

        GoogleTimelineRecord visitRecord = createVisitRecord();
        List<GoogleTimelineRecord> records = List.of(visitRecord);

        String jsonContent = objectMapper.writeValueAsString(records);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("google-timeline");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-visit.json", jsonData);

        googleTimelineImportStrategy.processImportData(importJob);

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Should have 1 point: midpoint of visit
        assertEquals(1, importedPoints.size(), "Visit should generate one midpoint");

        GpsPointEntity visitPoint = importedPoints.get(0);
        assertEquals(49.547321, visitPoint.getLatitude(), 0.000001, "Visit latitude should match");
        assertEquals(25.596540, visitPoint.getLongitude(), 0.000001, "Visit longitude should match"); 
        assertEquals(0.0, visitPoint.getVelocity(), 0.001, "Visit velocity should be 0 (stationary)");

        log.info("Visit processing verified: midpoint calculated correctly");
    }

    @Test
    @Transactional
    void testTimelinePathRecordProcessing() throws Exception {
        log.info("=== Testing Timeline Path Record Processing ===");

        GoogleTimelineRecord pathRecord = createTimelinePathRecord();
        List<GoogleTimelineRecord> records = List.of(pathRecord);

        String jsonContent = objectMapper.writeValueAsString(records);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("google-timeline");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-timeline-path.json", jsonData);

        googleTimelineImportStrategy.processImportData(importJob);

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Should have 10 points from the path
        assertEquals(10, importedPoints.size(), "Timeline path should generate 10 points");

        // Verify time offsets were applied correctly
        GpsPointEntity firstPoint = importedPoints.stream()
                .min((p1, p2) -> p1.getTimestamp().compareTo(p2.getTimestamp()))
                .orElse(null);

        GpsPointEntity lastPoint = importedPoints.stream()
                .max((p1, p2) -> p1.getTimestamp().compareTo(p2.getTimestamp()))
                .orElse(null);

        assertNotNull(firstPoint);
        assertNotNull(lastPoint);

        // Time difference should be 97 - 79 = 18 minutes
        long timeDiffMinutes = ChronoUnit.MINUTES.between(firstPoint.getTimestamp(), lastPoint.getTimestamp());
        assertEquals(18, timeDiffMinutes, "Time difference should match offset difference");

        log.info("Timeline path processing verified: {} points with correct time offsets", importedPoints.size());
    }

    @Test
    @Transactional
    void testInvalidDataHandling() throws Exception {
        log.info("=== Testing Invalid Data Handling ===");

        // Test 1: Invalid JSON
        byte[] invalidJson = "{ invalid json }".getBytes();
        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("google-timeline");

        ImportJob invalidJsonJob = importService.createImportJob(
                testUser.getId(), importOptions, "invalid.json", invalidJson);

        assertThrows(IllegalArgumentException.class, () -> {
            googleTimelineImportStrategy.validateAndDetectDataTypes(invalidJsonJob);
        }, "Invalid JSON should throw validation exception");

        // Test 2: Empty array
        byte[] emptyArray = "[]".getBytes();
        ImportJob emptyArrayJob = importService.createImportJob(
                testUser.getId(), importOptions, "empty.json", emptyArray);

        assertThrows(IllegalArgumentException.class, () -> {
            googleTimelineImportStrategy.validateAndDetectDataTypes(emptyArrayJob);
        }, "Empty array should throw validation exception");

        // Test 3: Records with no valid GPS data
        GoogleTimelineRecord invalidRecord = new GoogleTimelineRecord();
        invalidRecord.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        invalidRecord.setEndTime(Instant.now());
        // No activity, visit, or timelinePath - should be ignored

        List<GoogleTimelineRecord> invalidRecords = List.of(invalidRecord);
        String invalidContent = objectMapper.writeValueAsString(invalidRecords);
        byte[] invalidData = invalidContent.getBytes();

        ImportJob invalidDataJob = importService.createImportJob(
                testUser.getId(), importOptions, "invalid-data.json", invalidData);

        assertThrows(IllegalArgumentException.class, () -> {
            googleTimelineImportStrategy.validateAndDetectDataTypes(invalidDataJob);
        }, "Records with no valid GPS data should throw validation exception");

        log.info("Invalid data handling verified");
    }

    @Test
    @Transactional
    void testSourceTypeAssignment() throws Exception {
        log.info("=== Testing Source Type Assignment ===");

        List<GoogleTimelineRecord> testRecords = createTestTimelineRecords();
        String jsonContent = objectMapper.writeValueAsString(testRecords);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("google-timeline");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-source-type.json", jsonData);

        googleTimelineImportStrategy.processImportData(importJob);

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Verify all imported points have GOOGLE_TIMELINE source type
        for (GpsPointEntity point : importedPoints) {
            assertEquals(GpsSourceType.GOOGLE_TIMELINE, point.getSourceType(),
                    "All imported points should have GOOGLE_TIMELINE source type");
            assertEquals("google-timeline-import", point.getDeviceId(),
                    "All imported points should have google-timeline-import device ID");
        }

        log.info("Source type assignment verified: {} points with GOOGLE_TIMELINE source type", 
                importedPoints.size());
    }

    @Test
    @Transactional
    void testTimelineGenerationTriggering() throws Exception {
        log.info("=== Testing Timeline Generation Triggering ===");

        List<GoogleTimelineRecord> testRecords = createTestTimelineRecords();
        String jsonContent = objectMapper.writeValueAsString(testRecords);
        byte[] jsonData = jsonContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("google-timeline");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-timeline-trigger.json", jsonData);

        // Before import - should have no timeline stays
        long beforeTimelineCount = timelineStayRepository.count("user = ?1", testUser);
        assertEquals(0, beforeTimelineCount, "Should start with no timeline stays");

        // Process the import
        googleTimelineImportStrategy.processImportData(importJob);

        // After import - should have queued timeline generation task in background service
        List<org.github.tess1o.geopulse.timeline.model.TimelineRegenerationTask> queuedTasks = 
                timelineRegenerationTaskRepository.find("user = ?1", testUser).list();
        
        assertFalse(queuedTasks.isEmpty(), "Should have queued timeline generation tasks after import");
        
        // Verify at least one task is for the correct user
        boolean hasTaskForUser = queuedTasks.stream()
                .anyMatch(task -> task.getUser().getId().equals(testUser.getId()));
        
        assertTrue(hasTaskForUser, "Should have queued timeline generation task for the correct user");

    }

    private List<GoogleTimelineRecord> createTestTimelineRecords() {
        List<GoogleTimelineRecord> records = new ArrayList<>();
        
        // Add activity record
        records.add(createActivityRecord());
        
        // Add visit record
        records.add(createVisitRecord());
        
        // Add timeline path record
        records.add(createTimelinePathRecord());
        
        return records;
    }

    private GoogleTimelineRecord createActivityRecord() {
        GoogleTimelineRecord record = new GoogleTimelineRecord();
        record.setStartTime(Instant.now().minus(3, ChronoUnit.HOURS));
        record.setEndTime(Instant.now().minus(2, ChronoUnit.HOURS).minus(30, ChronoUnit.MINUTES));
        
        GoogleTimelineActivity activity = new GoogleTimelineActivity();
        activity.setProbability("0.986925");
        activity.setStart("geo:49.547223,25.596034");
        activity.setEnd("geo:49.547197,25.596019");
        activity.setDistanceMeters("4340.700195");
        
        GoogleTimelineActivityCandidate candidate = new GoogleTimelineActivityCandidate();
        candidate.setType("in passenger vehicle");
        candidate.setProbability("0.786390");
        activity.setTopCandidate(candidate);
        
        record.setActivity(activity);
        return record;
    }

    private GoogleTimelineRecord createVisitRecord() {
        GoogleTimelineRecord record = new GoogleTimelineRecord();
        record.setStartTime(Instant.now().minus(2, ChronoUnit.HOURS).minus(30, ChronoUnit.MINUTES));
        record.setEndTime(Instant.now().minus(1, ChronoUnit.HOURS));
        
        GoogleTimelineVisit visit = new GoogleTimelineVisit();
        visit.setHierarchyLevel("0");
        visit.setProbability("0.836301");
        
        GoogleTimelineVisitCandidate candidate = new GoogleTimelineVisitCandidate();
        candidate.setProbability("0.616064");
        candidate.setSemanticType("Searched Address");
        candidate.setPlaceID("ChIJiyxE8rE2MEcRsPnfTWjOrTs");
        candidate.setPlaceLocation("geo:49.547321,25.596540");
        visit.setTopCandidate(candidate);
        
        record.setVisit(visit);
        return record;
    }

    private GoogleTimelineRecord createTimelinePathRecord() {
        GoogleTimelineRecord record = new GoogleTimelineRecord();
        record.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        record.setEndTime(Instant.now().minus(30, ChronoUnit.MINUTES));
        
        GoogleTimelinePath[] path = new GoogleTimelinePath[10];
        String[] geoPoints = {
            "geo:49.547060,25.595861", "geo:49.547696,25.595886", "geo:49.547253,25.596003",
            "geo:49.546880,25.598610", "geo:49.549219,25.597159", "geo:49.553510,25.599488",
            "geo:49.556324,25.598566", "geo:49.561016,25.594819", "geo:49.564105,25.592134",
            "geo:49.565281,25.587533"
        };
        String[] offsets = {"79", "82", "83", "90", "91", "92", "93", "94", "95", "97"};
        
        for (int i = 0; i < path.length; i++) {
            GoogleTimelinePath pathPoint = new GoogleTimelinePath();
            pathPoint.setPoint(geoPoints[i]);
            pathPoint.setDurationMinutesOffsetFromStartTime(offsets[i]);
            path[i] = pathPoint;
        }
        
        record.setTimelinePath(path);
        return record;
    }

    private void verifyImportedData(List<GpsPointEntity> importedPoints) {
        log.info("Verifying imported data: {} points", importedPoints.size());

        for (GpsPointEntity point : importedPoints) {
            // Verify basic data integrity
            assertNotNull(point.getTimestamp(), "Timestamp should not be null");
            assertNotNull(point.getCoordinates(), "Coordinates should not be null");
            assertTrue(point.getLatitude() > 49.0 && point.getLatitude() < 50.0, 
                    "Latitude should be in expected range (Ukraine)");
            assertTrue(point.getLongitude() > 25.0 && point.getLongitude() < 26.0, 
                    "Longitude should be in expected range (Ukraine)");
            
            // Verify source type
            assertEquals(GpsSourceType.GOOGLE_TIMELINE, point.getSourceType(),
                    "Source type should be GOOGLE_TIMELINE");
            assertEquals("google-timeline-import", point.getDeviceId(),
                    "Device ID should be google-timeline-import");
            
            // Verify optional fields
            if (point.getVelocity() != null) {
                assertTrue(point.getVelocity() >= 0, "Velocity should be non-negative");
                log.debug("Point with velocity: {} km/h", point.getVelocity());
            }
        }

        log.info("Data verification completed successfully");
    }
}