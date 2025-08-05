package org.github.tess1o.geopulse.importdata;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.GpxImportStrategy;
import org.github.tess1o.geopulse.importdata.service.ImportService;
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
 * Integration test for GPX import functionality.
 * Tests the complete parsing and import cycle using real database operations.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class GpxImportStrategyTest {

    @Inject
    GpxImportStrategy gpxImportStrategy;

    @Inject
    ImportService importService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    org.github.tess1o.geopulse.timeline.repository.TimelineRegenerationTaskRepository taskRepository;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        cleanupTestData();

        // Create test user
        testUser = userRepository.find("email", "test-gpx@geopulse.app").firstResult();
        if (testUser == null) {
            testUser = new UserEntity();
            testUser.setEmail("test-gpx@geopulse.app");
            testUser.setFullName("GPX Test User");
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
        // Clean up in dependency order: timeline regeneration tasks -> timeline stays -> GPS points -> user
        taskRepository.delete("user.email = ?1", "test-gpx@geopulse.app");
        timelineStayRepository.delete("user.email = ?1", "test-gpx@geopulse.app");
        gpsPointRepository.delete("user.email = ?1", "test-gpx@geopulse.app");
        userRepository.delete("email = ?1", "test-gpx@geopulse.app");
    }

    @Test
    @Transactional
    void testGpxImportWithTracksAndWaypoints() throws Exception {
        log.info("=== Testing GPX Import with Tracks and Waypoints ===");

        // Create test GPX data with tracks and waypoints
        String gpxContent = createTestGpxContent();
        byte[] gpxData = gpxContent.getBytes();

        log.info("Created test GPX data: {} bytes", gpxData.length);

        // Validate the data
        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-track.gpx", gpxData);

        List<String> detectedDataTypes = gpxImportStrategy.validateAndDetectDataTypes(importJob);
        assertEquals(1, detectedDataTypes.size());
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS));

        log.info("Validation successful, detected data types: {}", detectedDataTypes);

        // Process the import
        long beforeImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(0, beforeImportCount, "Should start with no GPS points");

        gpxImportStrategy.processImportData(importJob);

        // Verify import results
        long afterImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertTrue(afterImportCount > 0, "Should have imported GPS points");

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        log.info("Import completed: {} GPS points imported", afterImportCount);

        // Verify imported data
        verifyImportedData(importedPoints);
    }

    @Test
    void testGpxImportWithTrackPointsOnly() throws Exception {
        log.info("=== Testing GPX Import with Track Points Only ===");

        // Create GPX with only track points
        String gpxContent = createTrackPointsOnlyGpxContent();
        byte[] gpxData = gpxContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-track-only.gpx", gpxData);

        gpxImportStrategy.processImportData(importJob);

        // Clear entity manager to force fresh query from database
        entityManager.clear();

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Should have 3 track points
        assertEquals(3, importedPoints.size(), "Should import exactly 3 track points");

        // Verify all points are from tracks (device ID should be "gpx-import")
        for (GpsPointEntity point : importedPoints) {
            assertEquals("gpx-import", point.getDeviceId(), "Track points should have gpx-import device ID");
            assertEquals(GpsSourceType.GPX, point.getSourceType(), "Should have GPX source type");
        }

        log.info("Track points only import verified: {} points", importedPoints.size());
    }

    @Test
    void testGpxImportWithWaypointsOnly() throws Exception {
        log.info("=== Testing GPX Import with Waypoints Only ===");

        // Create GPX with only waypoints (with timestamps)
        String gpxContent = createWaypointsOnlyGpxContent();
        byte[] gpxData = gpxContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-waypoints-only.gpx", gpxData);

        gpxImportStrategy.processImportData(importJob);

        // Clear entity manager to force fresh query from database
        entityManager.clear();

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Should have 2 waypoints (only those with timestamps)
        assertEquals(2, importedPoints.size(), "Should import exactly 2 waypoints with timestamps");

        // Verify all points are from waypoints (device ID should be "gpx-waypoint-import")
        for (GpsPointEntity point : importedPoints) {
            assertEquals("gpx-waypoint-import", point.getDeviceId(), "Waypoints should have gpx-waypoint-import device ID");
            assertEquals(GpsSourceType.GPX, point.getSourceType(), "Should have GPX source type");
            assertEquals(0.0, point.getVelocity(), 0.001, "Waypoints should have zero velocity");
        }

        log.info("Waypoints only import verified: {} points", importedPoints.size());
    }

    @Test
    @Transactional
    void testInvalidGpxHandling() throws Exception {
        log.info("=== Testing Invalid GPX Handling ===");

        // Test 1: Invalid XML
        byte[] invalidXml = "<invalid xml".getBytes();
        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx");

        ImportJob invalidXmlJob = importService.createImportJob(
                testUser.getId(), importOptions, "invalid.gpx", invalidXml);

        assertThrows(IllegalArgumentException.class, () -> {
            gpxImportStrategy.validateAndDetectDataTypes(invalidXmlJob);
        }, "Invalid XML should throw validation exception");

        // Test 2: Empty GPX file
        String emptyGpx = """
            <gpx version="1.1" creator="Test">
            </gpx>
        """;
        byte[] emptyGpxData = emptyGpx.getBytes();
        ImportJob emptyGpxJob = importService.createImportJob(
                testUser.getId(), importOptions, "empty.gpx", emptyGpxData);

        assertThrows(IllegalArgumentException.class, () -> {
            gpxImportStrategy.validateAndDetectDataTypes(emptyGpxJob);
        }, "Empty GPX should throw validation exception");

        log.info("Invalid GPX handling verified");
    }

    @Test
    @Transactional
    void testSourceTypeAssignment() throws Exception {
        log.info("=== Testing Source Type Assignment ===");

        String gpxContent = createTestGpxContent();
        byte[] gpxData = gpxContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-source-type.gpx", gpxData);

        gpxImportStrategy.processImportData(importJob);

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Verify all imported points have GPX source type
        for (GpsPointEntity point : importedPoints) {
            assertEquals(GpsSourceType.GPX, point.getSourceType(),
                    "All imported points should have GPX source type");
            assertTrue(point.getDeviceId().contains("gpx"),
                    "All imported points should have gpx-related device ID");
        }

        log.info("Source type assignment verified: {} points with GPX source type", 
                importedPoints.size());
    }

    private String createTestGpxContent() {
        return """
            <gpx version="1.1" creator="GeoPulseTest">
                <metadata>
                    <name>Test Track</name>
                    <desc>Test GPX file for import testing</desc>
                    <time>2025-08-04T12:00:00Z</time>
                </metadata>
                
                <!-- Waypoints with timestamps -->
                <wpt lat="49.5473" lon="25.5965">
                    <ele>300</ele>
                    <time>2025-08-04T12:00:00Z</time>
                    <name>Start Point</name>
                    <desc>Starting waypoint</desc>
                </wpt>
                <wpt lat="49.5480" lon="25.5970">
                    <ele>305</ele>
                    <time>2025-08-04T12:30:00Z</time>
                    <name>Mid Point</name>
                    <desc>Middle waypoint</desc>
                </wpt>
                
                <!-- Track with segments -->
                <trk>
                    <name>Test Track</name>
                    <desc>A test track for import</desc>
                    <trkseg>
                        <trkpt lat="49.5473" lon="25.5965">
                            <ele>300</ele>
                            <time>2025-08-04T12:00:00Z</time>
                            <speed>5.0</speed>
                        </trkpt>
                        <trkpt lat="49.5476" lon="25.5968">
                            <ele>302</ele>
                            <time>2025-08-04T12:05:00Z</time>
                            <speed>4.5</speed>
                        </trkpt>
                        <trkpt lat="49.5480" lon="25.5970">
                            <ele>305</ele>
                            <time>2025-08-04T12:10:00Z</time>
                            <speed>3.8</speed>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """;
    }

    private String createTrackPointsOnlyGpxContent() {
        // Use current time to ensure the test data falls within the query range
        Instant now = Instant.now();
        return String.format("""
            <gpx version="1.1" creator="GeoPulseTest">
                <trk>
                    <name>Track Only</name>
                    <trkseg>
                        <trkpt lat="49.5473" lon="25.5965">
                            <ele>300</ele>
                            <time>%s</time>
                            <speed>5.0</speed>
                        </trkpt>
                        <trkpt lat="49.5476" lon="25.5968">
                            <ele>302</ele>
                            <time>%s</time>
                            <speed>4.5</speed>
                        </trkpt>
                        <trkpt lat="49.5480" lon="25.5970">
                            <ele>305</ele>
                            <time>%s</time>
                            <speed>3.8</speed>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """, now.toString(), now.plusSeconds(300).toString(), now.plusSeconds(600).toString());
    }

    private String createWaypointsOnlyGpxContent() {
        // Use current time to ensure the test data falls within the query range
        Instant now = Instant.now();
        return String.format("""
            <gpx version="1.1" creator="GeoPulseTest">
                <!-- Waypoint with timestamp (should be imported) -->
                <wpt lat="49.5473" lon="25.5965">
                    <ele>300</ele>
                    <time>%s</time>
                    <name>Timed Waypoint 1</name>
                </wpt>
                
                <!-- Waypoint with timestamp (should be imported) -->
                <wpt lat="49.5480" lon="25.5970">
                    <ele>305</ele>
                    <time>%s</time>
                    <name>Timed Waypoint 2</name>
                </wpt>
                
                <!-- Waypoint without timestamp (should be skipped) -->
                <wpt lat="49.5485" lon="25.5975">
                    <ele>310</ele>
                    <name>Untimed Waypoint</name>
                </wpt>
            </gpx>
        """, now.toString(), now.plusSeconds(1800).toString());
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
            assertEquals(GpsSourceType.GPX, point.getSourceType(),
                    "Source type should be GPX");
            assertTrue(point.getDeviceId().contains("gpx"),
                    "Device ID should contain 'gpx'");
            
            // Verify optional fields
            if (point.getVelocity() != null) {
                assertTrue(point.getVelocity() >= 0, "Velocity should be non-negative");
                log.debug("Point with velocity: {} km/h", point.getVelocity());
            }
            
            if (point.getAltitude() != null) {
                assertTrue(point.getAltitude() >= 0, "Altitude should be reasonable");
                log.debug("Point with altitude: {} m", point.getAltitude());
            }
        }

        log.info("Data verification completed successfully");
    }
}