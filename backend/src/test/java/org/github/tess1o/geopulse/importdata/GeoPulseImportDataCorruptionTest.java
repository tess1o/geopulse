package org.github.tess1o.geopulse.importdata;

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
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to ensure the GeoPulse import strategy fix prevents data corruption
 * when multiple users import the same export file.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
public class GeoPulseImportDataCorruptionTest {

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    ExportDataGenerator exportDataGenerator;

    @Inject
    ImportDataService importDataService;
    @Inject
    CleanupHelper cleanupHelper;

    private UserEntity testUserA;
    private UserEntity testUserB;
    private GpsPointEntity testGpsPoint;
    private byte[] exportData;

    @BeforeEach
    @Transactional
    void setUp() throws Exception {
        // Create two test users
        testUserA = new UserEntity();
        testUserA.setFullName("Test User A");
        testUserA.setEmail("testa@example.com");
        userRepository.persist(testUserA);

        testUserB = new UserEntity();
        testUserB.setFullName("Test User B");
        testUserB.setEmail("testb@example.com");
        userRepository.persist(testUserB);

        // Create test GPS data for User A
        testGpsPoint = new GpsPointEntity();
        testGpsPoint.setUser(testUserA);
        testGpsPoint.setDeviceId("test-device");
        testGpsPoint.setCoordinates(GeoUtils.createPoint(-122.4194, 37.7749)); // San Francisco
        testGpsPoint.setTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        testGpsPoint.setSourceType(GpsSourceType.GPX);
        testGpsPoint.setCreatedAt(Instant.now());
        testGpsPoint.setAccuracy(10.0);
        gpsPointRepository.persist(testGpsPoint);

        log.info("Created test GPS point for User A with ID: {}", testGpsPoint.getId());

        // Export User A's data to create test import file
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
        dateRange.setEndDate(Instant.now().plus(1, ChronoUnit.DAYS));

        ExportJob exportJob = new ExportJob(
                testUserA.getId(),
                List.of(ExportImportConstants.DataTypes.RAW_GPS),
                dateRange,
                ExportImportConstants.Formats.JSON
        );

        exportData = exportDataGenerator.generateExportZip(exportJob);
        assertNotNull(exportData);
        assertTrue(exportData.length > 0);

        log.info("Generated export data: {} bytes", exportData.length);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Clean up test data
        cleanupHelper.cleanupTimeline();
        if (testUserA != null) {
            gpsPointRepository.deleteByUserId(testUserA.getId());
            userRepository.deleteById(testUserA.getId());
        }
        if (testUserB != null) {
            gpsPointRepository.deleteByUserId(testUserB.getId());
            userRepository.deleteById(testUserB.getId());
        }
    }

    @Test
    @Transactional
    void testMultipleUsersImportSameFile_NoDataCorruption() throws Exception {
        log.info("=== Testing Multiple Users Import Same File - No Data Corruption ===");

        // Step 1: Verify initial state - User A has 1 GPS point, User B has 0
        List<GpsPointEntity> userAInitialPoints = gpsPointRepository.findByUserId(testUserA.getId());
        List<GpsPointEntity> userBInitialPoints = gpsPointRepository.findByUserId(testUserB.getId());

        assertEquals(1, userAInitialPoints.size(), "User A should have 1 GPS point initially");
        assertEquals(0, userBInitialPoints.size(), "User B should have 0 GPS points initially");

        Long originalGpsPointId = userAInitialPoints.get(0).getId();
        log.info("User A initial GPS point ID: {}", originalGpsPointId);

        // Step 2: User B imports the same file (this would cause data corruption in old system)
        ImportOptions importOptionsB = new ImportOptions();
        importOptionsB.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        importOptionsB.setImportFormat(ExportImportConstants.Formats.GEOPULSE);

        ImportJob importJobB = new ImportJob(testUserB.getId(), importOptionsB, "test-export.zip", exportData);

        importDataService.processImportData(importJobB);
        log.info("User B import completed");

        // Step 3: Verify User A's data is NOT affected (no data theft)
        List<GpsPointEntity> userAAfterBImport = gpsPointRepository.findByUserId(testUserA.getId());
        List<GpsPointEntity> userBAfterBImport = gpsPointRepository.findByUserId(testUserB.getId());

        assertEquals(1, userAAfterBImport.size(), "User A should still have 1 GPS point after User B import");
        assertEquals(1, userBAfterBImport.size(), "User B should have 1 GPS point after import");

        // Verify User A's original GPS point still exists and belongs to User A
        GpsPointEntity userAPoint = userAAfterBImport.get(0);
        assertEquals(originalGpsPointId, userAPoint.getId(), "User A's original GPS point ID should be preserved");
        assertEquals(testUserA.getId(), userAPoint.getUser().getId(), "User A's GPS point should still belong to User A");

        // Verify User B's GPS point is separate with new ID
        GpsPointEntity userBPoint = userBAfterBImport.get(0);
        assertNotEquals(originalGpsPointId, userBPoint.getId(), "User B's GPS point should have different ID");
        assertEquals(testUserB.getId(), userBPoint.getUser().getId(), "User B's GPS point should belong to User B");

        log.info("User A GPS point ID: {} (unchanged)", userAPoint.getId());
        log.info("User B GPS point ID: {} (new)", userBPoint.getId());

        // Step 4: User A imports the same file again (should skip duplicates)
        ImportOptions importOptionsA = new ImportOptions();
        importOptionsA.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        importOptionsA.setImportFormat(ExportImportConstants.Formats.GEOPULSE);

        ImportJob importJobA = new ImportJob(testUserA.getId(), importOptionsA, "test-export.zip", exportData);

        importDataService.processImportData(importJobA);
        log.info("User A re-import completed");

        // Step 5: Verify final state - no additional duplicates created
        List<GpsPointEntity> userAFinalPoints = gpsPointRepository.findByUserId(testUserA.getId());
        List<GpsPointEntity> userBFinalPoints = gpsPointRepository.findByUserId(testUserB.getId());

        assertEquals(1, userAFinalPoints.size(), "User A should still have only 1 GPS point after re-import");
        assertEquals(1, userBFinalPoints.size(), "User B should still have only 1 GPS point");

        // Verify coordinates match (same location data)
        assertEquals(userAFinalPoints.get(0).getCoordinates().getX(), 
                    userBFinalPoints.get(0).getCoordinates().getX(), 0.000001,
                    "Both users should have GPS points at same longitude");
        assertEquals(userAFinalPoints.get(0).getCoordinates().getY(), 
                    userBFinalPoints.get(0).getCoordinates().getY(), 0.000001,
                    "Both users should have GPS points at same latitude");

        log.info("✅ Data corruption test PASSED - Multiple users can safely import same file");
    }

    @Test
    @Transactional 
    void testClearModeImport_UserSpecificClearing() throws Exception {
        log.info("=== Testing Clear Mode Import - User Specific Clearing ===");

        // Step 1: Both users import the same file
        ImportOptions importOptionsA = new ImportOptions();
        importOptionsA.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        importOptionsA.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        importOptionsA.setClearDataBeforeImport(false); // Merge mode

        ImportOptions importOptionsB = new ImportOptions();
        importOptionsB.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        importOptionsB.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        importOptionsB.setClearDataBeforeImport(false); // Merge mode

        ImportJob importJobA = new ImportJob(testUserA.getId(), importOptionsA, "test-export.zip", exportData);
        ImportJob importJobB = new ImportJob(testUserB.getId(), importOptionsB, "test-export.zip", exportData);

        importDataService.processImportData(importJobA);
        importDataService.processImportData(importJobB);

        // Verify both users have data
        assertEquals(1, gpsPointRepository.findByUserId(testUserA.getId()).size());
        assertEquals(1, gpsPointRepository.findByUserId(testUserB.getId()).size());

        // Step 2: User A re-imports with clear mode - should only clear User A's data
        ImportOptions clearImportOptions = new ImportOptions();
        clearImportOptions.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        clearImportOptions.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        clearImportOptions.setClearDataBeforeImport(true); // Clear mode

        ImportJob clearImportJob = new ImportJob(testUserA.getId(), clearImportOptions, "test-export.zip", exportData);
        importDataService.processImportData(clearImportJob);

        // Verify User A still has data (cleared then re-imported)
        // Verify User B's data is untouched
        assertEquals(1, gpsPointRepository.findByUserId(testUserA.getId()).size(), 
                    "User A should have data after clear+import");
        assertEquals(1, gpsPointRepository.findByUserId(testUserB.getId()).size(), 
                    "User B's data should be unaffected by User A's clear import");

        log.info("✅ Clear mode test PASSED - Only target user's data is cleared");
    }
}