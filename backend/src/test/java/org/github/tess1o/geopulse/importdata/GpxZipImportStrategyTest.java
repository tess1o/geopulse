package org.github.tess1o.geopulse.importdata;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.GpxZipImportStrategy;
import org.github.tess1o.geopulse.importdata.service.ImportService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for GPX ZIP import functionality.
 * Tests importing multiple GPX files from a ZIP archive.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class GpxZipImportStrategyTest {

    @Inject
    GpxZipImportStrategy gpxZipImportStrategy;

    @Inject
    ImportService importService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    CleanupHelper cleanupHelper;

    @Inject
    EntityManager entityManager;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        cleanupTestData();

        // Create test user
        testUser = userRepository.find("email", "test-gpx-zip@geopulse.app").firstResult();
        if (testUser == null) {
            testUser = new UserEntity();
            testUser.setEmail("test-gpx-zip@geopulse.app");
            testUser.setFullName("GPX ZIP Test User");
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
        cleanupHelper.cleanupTimeline();
        gpsPointRepository.delete("user.email = ?1", "test-gpx-zip@geopulse.app");
        userRepository.delete("email = ?1", "test-gpx-zip@geopulse.app");
    }

    @Test
    @Transactional
    void testGpxZipImportWithMultipleFiles() throws Exception {
        log.info("=== Testing GPX ZIP Import with Multiple Files ===");

        // Create ZIP with multiple GPX files
        byte[] zipData = createTestZipWithMultipleGpxFiles();

        log.info("Created test ZIP data: {} bytes", zipData.length);

        // Validate the data
        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx-zip");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-tracks.zip", zipData);

        List<String> detectedDataTypes = gpxZipImportStrategy.validateAndDetectDataTypes(importJob);
        assertEquals(1, detectedDataTypes.size());
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS));

        log.info("Validation successful, detected data types: {}", detectedDataTypes);

        // Process the import
        long beforeImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(0, beforeImportCount, "Should start with no GPS points");

        gpxZipImportStrategy.processImportData(importJob);

        // Verify import results
        long afterImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertTrue(afterImportCount > 0, "Should have imported GPS points");

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        log.info("Import completed: {} GPS points imported from ZIP", afterImportCount);

        // Verify we got points from all files (3 files with 3 track points each = 9 points total)
        assertEquals(9, importedPoints.size(), "Should import 9 GPS points from 3 GPX files");

        // Verify imported data
        verifyImportedData(importedPoints);
    }

    @Test
    @Transactional
    void testGpxZipImportWithMixedContent() throws Exception {
        log.info("=== Testing GPX ZIP Import with Mixed Content ===");

        // Create ZIP with GPX files, non-GPX files, and directories
        byte[] zipData = createTestZipWithMixedContent();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx-zip");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "mixed-content.zip", zipData);

        // Validate - should find only GPX files
        List<String> detectedDataTypes = gpxZipImportStrategy.validateAndDetectDataTypes(importJob);
        assertEquals(1, detectedDataTypes.size());

        // Process the import
        gpxZipImportStrategy.processImportData(importJob);

        entityManager.clear();

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Should import only from GPX files, ignoring other files
        assertEquals(6, importedPoints.size(),
                "Should import 6 GPS points (2 GPX files with 3 points each)");

        log.info("Mixed content import verified: {} points from GPX files only", importedPoints.size());
    }

    @Test
    @Transactional
    void testGpxZipImportWithEmptyZip() throws Exception {
        log.info("=== Testing GPX ZIP Import with Empty ZIP ===");

        // Create empty ZIP file
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Don't add any entries
        }
        byte[] emptyZipData = baos.toByteArray();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx-zip");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "empty.zip", emptyZipData);

        // Should throw validation exception for empty ZIP
        assertThrows(IllegalArgumentException.class, () -> {
            gpxZipImportStrategy.validateAndDetectDataTypes(importJob);
        }, "Empty ZIP should throw validation exception");

        log.info("Empty ZIP handling verified");
    }

    @Test
    @Transactional
    void testGpxZipImportWithInvalidGpxFiles() throws Exception {
        log.info("=== Testing GPX ZIP Import with One Invalid GPX File ===");

        // Create ZIP with one valid and one invalid GPX file
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add valid GPX file
            ZipEntry validEntry = new ZipEntry("valid.gpx");
            zos.putNextEntry(validEntry);
            zos.write(createTestGpxContent("Valid Track", Instant.now()).getBytes());
            zos.closeEntry();

            // Add invalid GPX file
            ZipEntry invalidEntry = new ZipEntry("invalid.gpx");
            zos.putNextEntry(invalidEntry);
            zos.write("<invalid xml".getBytes());
            zos.closeEntry();
        }
        byte[] zipData = baos.toByteArray();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx-zip");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "mixed-validity.zip", zipData);

        // Validation should continue despite invalid file (logs warning)
        gpxZipImportStrategy.validateAndDetectDataTypes(importJob);

        // Process the import - should import the valid file and skip the invalid one
        gpxZipImportStrategy.processImportData(importJob);

        entityManager.clear();

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Should only import from valid GPX file
        assertEquals(3, importedPoints.size(),
                "Should import 3 GPS points from the valid file, skipping invalid file");

        log.info("Invalid file handling verified: {} points from valid file", importedPoints.size());
    }

    @Test
    @Transactional
    void testSourceTypeAssignment() throws Exception {
        log.info("=== Testing Source Type Assignment for ZIP Import ===");

        byte[] zipData = createTestZipWithMultipleGpxFiles();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("gpx-zip");

        ImportJob importJob = importService.createImportJob(
                testUser.getId(), importOptions, "test-source-type.zip", zipData);

        gpxZipImportStrategy.processImportData(importJob);

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

    private byte[] createTestZipWithMultipleGpxFiles() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add first GPX file
            ZipEntry entry1 = new ZipEntry("track1.gpx");
            zos.putNextEntry(entry1);
            zos.write(createTestGpxContent("Track 1", Instant.now()).getBytes());
            zos.closeEntry();

            // Add second GPX file
            ZipEntry entry2 = new ZipEntry("track2.gpx");
            zos.putNextEntry(entry2);
            zos.write(createTestGpxContent("Track 2", Instant.now().plusSeconds(3600)).getBytes());
            zos.closeEntry();

            // Add third GPX file in a subdirectory
            ZipEntry entry3 = new ZipEntry("subfolder/track3.gpx");
            zos.putNextEntry(entry3);
            zos.write(createTestGpxContent("Track 3", Instant.now().plusSeconds(7200)).getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createTestZipWithMixedContent() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add GPX files
            ZipEntry entry1 = new ZipEntry("track1.gpx");
            zos.putNextEntry(entry1);
            zos.write(createTestGpxContent("Track 1", Instant.now()).getBytes());
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("track2.gpx");
            zos.putNextEntry(entry2);
            zos.write(createTestGpxContent("Track 2", Instant.now().plusSeconds(3600)).getBytes());
            zos.closeEntry();

            // Add non-GPX file (should be ignored)
            ZipEntry textEntry = new ZipEntry("readme.txt");
            zos.putNextEntry(textEntry);
            zos.write("This is a readme file".getBytes());
            zos.closeEntry();

            // Add directory entry (should be ignored)
            ZipEntry dirEntry = new ZipEntry("subfolder/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();

            // Add JSON file (should be ignored)
            ZipEntry jsonEntry = new ZipEntry("data.json");
            zos.putNextEntry(jsonEntry);
            zos.write("{\"key\": \"value\"}".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String createTestGpxContent(String trackName, Instant baseTime) {
        return String.format("""
            <gpx version="1.1" creator="GeoPulseTest">
                <metadata>
                    <name>%s</name>
                    <time>%s</time>
                </metadata>

                <trk>
                    <name>%s</name>
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
        """, trackName, baseTime.toString(), trackName,
                baseTime.toString(),
                baseTime.plusSeconds(300).toString(),
                baseTime.plusSeconds(600).toString());
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
