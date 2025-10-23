package org.github.tess1o.geopulse.exportimport;

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
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
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
 * Comprehensive integration test for export/import functionality with dependency handling.
 * Tests the complete lifecycle:
 * 1) Insert test data with foreign key relationships
 * 2) Export data and validate dependencies are included
 * 3) Delete test data (completely or partially)
 * 4) Import data from exported file
 * 5) Verify that data before deletion and after import is identical
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class ExportImportIntegrationTest {

    @Inject
    ExportDataGenerator exportDataGenerator;

    @Inject
    ImportDataService importDataService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    ReverseGeocodingLocationRepository reverseGeocodingLocationRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;
    @Inject
    CleanupHelper cleanupHelper;

    private UserEntity testUser;
    private FavoritesEntity testFavorite;
    private ReverseGeocodingLocationEntity testGeocodingLocation;
    private TimelineStayEntity testStay;
    private TimelineTripEntity testTrip;
    private TimelineDataGapEntity testDataGap;
    private GpsPointEntity testGpsPoint;
    private GpsSourceConfigEntity testGpsSource;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        cleanupTestData();

        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("test-export-import@geopulse.app");
        testUser.setFullName("Export Import Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);

        // Create test reverse geocoding location (no dependencies)
        testGeocodingLocation = new ReverseGeocodingLocationEntity();
        testGeocodingLocation.setRequestCoordinates(GeoUtils.createPoint(-122.4194, 37.7749)); // San Francisco
        testGeocodingLocation.setResultCoordinates(GeoUtils.createPoint(-122.4194, 37.7749));
        testGeocodingLocation.setDisplayName("San Francisco, CA, USA");
        testGeocodingLocation.setProviderName("test-provider");
        testGeocodingLocation.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        testGeocodingLocation.setLastAccessedAt(Instant.now());
        testGeocodingLocation.setCity("San Francisco");
        testGeocodingLocation.setCountry("USA");
        reverseGeocodingLocationRepository.persist(testGeocodingLocation);

        // Create test favorite (no dependencies)
        testFavorite = new FavoritesEntity();
        testFavorite.setUser(testUser);
        testFavorite.setName("Home");
        testFavorite.setCity("San Francisco");
        testFavorite.setCountry("USA");
        testFavorite.setType(FavoriteLocationType.POINT);
        testFavorite.setGeometry(GeoUtils.createPoint(-122.4194, 37.7749));
        favoritesRepository.persist(testFavorite);

        // Create test timeline stay (depends on favorite and geocoding)
        testStay = TimelineStayEntity.builder()
                .user(testUser)
                .timestamp(Instant.now().minus(2, ChronoUnit.HOURS))
                .location(GeoUtils.createPoint(-122.4194, 37.7749))
                .stayDuration(60) // 60 minutes
                .locationName("Home Location")
                .locationSource(LocationSource.HISTORICAL)
                .favoriteLocation(testFavorite) // Foreign key reference
                .geocodingLocation(testGeocodingLocation) // Foreign key reference
                .build();
        timelineStayRepository.persist(testStay);

        // Create test timeline trip with LineString path
        Coordinate[] pathCoordinates = new Coordinate[] {
            new Coordinate(-122.4194, 37.7749), // Start: San Francisco
            new Coordinate(-122.4150, 37.7770), // Intermediate point 1
            new Coordinate(-122.4120, 37.7800), // Intermediate point 2
            new Coordinate(-122.4094, 37.7849)  // End point
        };
        LineString tripPath = geometryFactory.createLineString(pathCoordinates);

        testTrip = TimelineTripEntity.builder()
                .user(testUser)
                .timestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .startPoint(GeoUtils.createPoint(-122.4194, 37.7749))
                .endPoint(GeoUtils.createPoint(-122.4094, 37.7849))
                .distanceMeters(1500) // 1.5km in meters
                .tripDuration(1800) // 30 minutes in seconds
                .movementType("WALKING")
                .path(tripPath) // Add the LineString path
                .build();
        timelineTripRepository.persist(testTrip);

        // Create test data gap
        testDataGap = TimelineDataGapEntity.builder()
                .user(testUser)
                .startTime(Instant.now().minus(4, ChronoUnit.HOURS))
                .endTime(Instant.now().minus(3, ChronoUnit.HOURS).minus(30, ChronoUnit.MINUTES))
                .durationSeconds(1800) // 30 minutes gap
                .createdAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();
        timelineDataGapRepository.persist(testDataGap);

        // Create test GPS point
        testGpsPoint = new GpsPointEntity();
        testGpsPoint.setUser(testUser);
        testGpsPoint.setTimestamp(Instant.now().minus(3, ChronoUnit.HOURS));
        testGpsPoint.setCoordinates(GeoUtils.createPoint(-122.4194, 37.7749));
        testGpsPoint.setAccuracy(5.0);
        testGpsPoint.setAltitude(100.0);
        testGpsPoint.setVelocity(0.0);
        testGpsPoint.setBattery(85.0);
        testGpsPoint.setDeviceId("test-device");
        testGpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        testGpsPoint.setCreatedAt(Instant.now());
        gpsPointRepository.persist(testGpsPoint);

        // Create test GPS source
        testGpsSource = new GpsSourceConfigEntity();
        testGpsSource.setUser(testUser);
        testGpsSource.setUsername("test-user");
        testGpsSource.setSourceType(GpsSourceType.OWNTRACKS);
        testGpsSource.setActive(true);
        gpsSourceRepository.persist(testGpsSource);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Transactional
    void cleanupTestData() {
        // Clean up in dependency order (reverse of creation)
        cleanupHelper.cleanupTimeline();
        gpsSourceRepository.delete("user.email = ?1", "test-export-import@geopulse.app");
        gpsPointRepository.delete("user.email = ?1", "test-export-import@geopulse.app");
        timelineTripRepository.delete("user.email = ?1", "test-export-import@geopulse.app");
        timelineStayRepository.delete("user.email = ?1", "test-export-import@geopulse.app");
        timelineDataGapRepository.delete("user.email = ?1", "test-export-import@geopulse.app");
        favoritesRepository.delete("user.email = ?1", "test-export-import@geopulse.app");
        reverseGeocodingLocationRepository.delete("providerName = ?1", "test-provider");
        userRepository.delete("email = ?1", "test-export-import@geopulse.app");
    }

    @Test
    @Transactional
    void testCompleteExportImportCycle() throws Exception {
        log.info("=== Starting Complete Export/Import Integration Test ===");

        // Step 1: Capture original data for comparison
        log.info("Step 1: Capturing original data state");
        var originalStay = timelineStayRepository.findById(testStay.getId());
        var originalTrip = timelineTripRepository.findById(testTrip.getId());
        var originalDataGap = timelineDataGapRepository.findById(testDataGap.getId());
        var originalFavorite = favoritesRepository.findById(testFavorite.getId());
        var originalGeocodingLocation = reverseGeocodingLocationRepository.findById(testGeocodingLocation.getId());
        var originalGpsPoint = gpsPointRepository.findById(testGpsPoint.getId());
        var originalGpsSource = gpsSourceRepository.findById(testGpsSource.getId());
        var originalUser = userRepository.findById(testUser.getId());

        assertNotNull(originalStay);
        assertNotNull(originalTrip);
        assertNotNull(originalDataGap);
        assertNotNull(originalFavorite);
        assertNotNull(originalGeocodingLocation);
        assertNotNull(originalGpsPoint);
        assertNotNull(originalGpsSource);
        assertNotNull(originalUser);

        // Verify foreign key relationships exist
        assertNotNull(originalStay.getFavoriteLocation());
        assertNotNull(originalStay.getGeocodingLocation());
        assertEquals(testFavorite.getId(), originalStay.getFavoriteLocation().getId());
        assertEquals(testGeocodingLocation.getId(), originalStay.getGeocodingLocation().getId());

        // Verify trip has path data
        assertNotNull(originalTrip.getPath(), "Original trip should have path data");
        assertEquals(4, originalTrip.getPath().getNumPoints(), "Path should have 4 coordinate points");
        // Verify start and end coordinates match the path
        Coordinate[] originalPathCoords = originalTrip.getPath().getCoordinates();
        assertEquals(-122.4194, originalPathCoords[0].x, 0.000001, "Path start longitude should match");
        assertEquals(37.7749, originalPathCoords[0].y, 0.000001, "Path start latitude should match");
        assertEquals(-122.4094, originalPathCoords[3].x, 0.000001, "Path end longitude should match");
        assertEquals(37.7849, originalPathCoords[3].y, 0.000001, "Path end latitude should match");

        // Step 2: Export all data including timeline (should auto-include dependencies)
        log.info("Step 2: Exporting data");
        // Set date range to include all test data
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
        dateRange.setEndDate(Instant.now().plus(1, ChronoUnit.DAYS));
        
        ExportJob exportJob = new ExportJob(
            testUser.getId(), 
            List.of(ExportImportConstants.DataTypes.TIMELINE, ExportImportConstants.DataTypes.RAW_GPS, 
                   ExportImportConstants.DataTypes.USER_INFO, ExportImportConstants.DataTypes.LOCATION_SOURCES), // Note: NOT including favorites or reverse geocoding explicitly
            dateRange,
            ExportImportConstants.Formats.JSON
        );

        byte[] exportedData = exportDataGenerator.generateExportZip(exportJob);
        assertNotNull(exportedData);
        assertTrue(exportedData.length > 0);
        log.info("Export completed, generated {} bytes", exportedData.length);

        // Step 3: Validate export contents (should include auto-collected dependencies)
        log.info("Step 3: Validating export includes dependencies");
        ImportOptions validateOptions = new ImportOptions();
        // Note: Including TIMELINE for validation to ensure it's in export, but won't be imported 
        validateOptions.setDataTypes(List.of(ExportImportConstants.DataTypes.TIMELINE, ExportImportConstants.DataTypes.RAW_GPS, 
                                             ExportImportConstants.DataTypes.USER_INFO, ExportImportConstants.DataTypes.LOCATION_SOURCES, 
                                             ExportImportConstants.DataTypes.FAVORITES, ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION));
        validateOptions.setImportFormat(ExportImportConstants.Formats.GEOPULSE);

        ImportJob validateJob = new ImportJob(testUser.getId(), validateOptions, "test-export.zip", exportedData);

        List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(validateJob);
        log.info("Detected data types in export: {}", detectedDataTypes);

        // Should include auto-collected dependencies (note: timeline will be regenerated from GPS, not imported)
        assertFalse(detectedDataTypes.contains(ExportImportConstants.DataTypes.TIMELINE), "Timeline data should not be imported - will be regenerated from GPS");
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.FAVORITES), "Favorites should be auto-included due to timeline dependency");
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION), "Reverse geocoding should be auto-included due to timeline dependency");
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS), "Raw GPS data should be included");
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.USER_INFO), "User info should be included");
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.LOCATION_SOURCES), "Location sources should be included");

        // Step 4: Delete test data (simulating data loss or migration scenario)
        log.info("Step 4: Deleting test data");

        Long originalGeocodingId = originalGeocodingLocation.getId();
        // Clear the entire transaction context first to avoid stale references
        timelineStayRepository.getEntityManager().clear();
        
        // Delete in dependency order (children first)
        timelineStayRepository.deleteById(testStay.getId());
        timelineTripRepository.deleteById(testTrip.getId());
        timelineDataGapRepository.deleteById(testDataGap.getId());
        gpsPointRepository.deleteById(testGpsPoint.getId());
        gpsSourceRepository.deleteById(testGpsSource.getId());
        favoritesRepository.deleteById(testFavorite.getId());
        reverseGeocodingLocationRepository.deleteById(testGeocodingLocation.getId());
        
        // Flush and clear the entity manager to ensure deletions are committed
        timelineStayRepository.getEntityManager().flush();
        timelineStayRepository.getEntityManager().clear();

        // Verify data is deleted
        assertNull(timelineStayRepository.findByIdOptional(testStay.getId()).orElse(null));
        assertNull(timelineTripRepository.findByIdOptional(testTrip.getId()).orElse(null));
        assertNull(timelineDataGapRepository.findByIdOptional(testDataGap.getId()).orElse(null));
        assertNull(gpsPointRepository.findByIdOptional(testGpsPoint.getId()).orElse(null));
        assertNull(gpsSourceRepository.findByIdOptional(testGpsSource.getId()).orElse(null));
        assertNull(favoritesRepository.findByIdOptional(testFavorite.getId()).orElse(null));
        assertNull(reverseGeocodingLocationRepository.findByIdOptional(testGeocodingLocation.getId()).orElse(null));
        log.info("Test data successfully deleted");

        // Step 5: Import data from exported file
        log.info("Step 5: Importing data");
        ImportOptions importOptions = new ImportOptions();
        // Note: Excluding TIMELINE from import - it will be regenerated from GPS data
        importOptions.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS, 
                                           ExportImportConstants.DataTypes.USER_INFO, ExportImportConstants.DataTypes.LOCATION_SOURCES, 
                                           ExportImportConstants.DataTypes.FAVORITES, ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION));
        importOptions.setImportFormat(ExportImportConstants.Formats.GEOPULSE);

        ImportJob importJob = new ImportJob(testUser.getId(), importOptions, "test-export.zip", exportedData);

        importDataService.processImportData(importJob);
        log.info("Import completed");

        // Step 6: Verify imported data matches original data
        log.info("Step 6: Verifying imported data matches original");

        // Verify reverse geocoding location (ID should be preserved exactly)
        var importedGeocodingLocation = reverseGeocodingLocationRepository.findById(originalGeocodingId);
        assertNotNull(importedGeocodingLocation, "Reverse geocoding location should be imported");
        assertEquals(originalGeocodingId, importedGeocodingLocation.getId(), "Reverse geocoding location ID should be preserved exactly");
        assertEquals(originalGeocodingLocation.getDisplayName(), importedGeocodingLocation.getDisplayName(), "Display name should match");
        assertEquals(originalGeocodingLocation.getCity(), importedGeocodingLocation.getCity(), "City should match");
        assertEquals(originalGeocodingLocation.getCountry(), importedGeocodingLocation.getCountry(), "Country should match");

        // Verify GPS point (GPS points don't preserve IDs since export format doesn't include them)
        var importedGpsPointsList = gpsPointRepository.findByUserAndDateRange(
            testUser.getId(),
            originalGpsPoint.getTimestamp().minusSeconds(1),
            originalGpsPoint.getTimestamp().plusSeconds(1),
            0, 10, "timestamp", "asc"
        );
        assertFalse(importedGpsPointsList.isEmpty(), "At least one GPS point should be imported");
        var importedGpsPoint = importedGpsPointsList.get(0);
        assertNotNull(importedGpsPoint, "GPS point should be imported");
        assertEquals(originalGpsPoint.getLatitude(), importedGpsPoint.getLatitude(), 0.000001, "GPS latitude should match");
        assertEquals(originalGpsPoint.getAccuracy(), importedGpsPoint.getAccuracy(), 0.001, "GPS accuracy should match");

        // Verify GPS source (ID should be preserved exactly)
        var importedGpsSource = gpsSourceRepository.findAll().firstResult();
        assertNotNull(importedGpsSource, "GPS source should be imported with exact ID preservation");
        assertEquals(originalGpsSource.getUsername(), importedGpsSource.getUsername(), "GPS source username should match");

        log.info("=== Export/Import Integration Test Completed Successfully ===");
    }

    @Test
    void testExportWithoutTimelineDependencies() throws Exception {
        log.info("=== Testing Export Without Timeline Dependencies ===");

        // Export only favorites (no timeline) - should not auto-include dependencies
        ExportJob exportJob = new ExportJob();
        exportJob.setUserId(testUser.getId());
        exportJob.setDataTypes(List.of(ExportImportConstants.DataTypes.FAVORITES, ExportImportConstants.DataTypes.USER_INFO)); // No timeline
        exportJob.setFormat(ExportImportConstants.Formats.JSON);

        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
        dateRange.setEndDate(Instant.now().plus(1, ChronoUnit.DAYS));
        exportJob.setDateRange(dateRange);

        byte[] exportedData = exportDataGenerator.generateExportZip(exportJob);

        // Validate that reverse geocoding is NOT auto-included
        ImportOptions validateOptions = new ImportOptions();
        validateOptions.setDataTypes(List.of(ExportImportConstants.DataTypes.FAVORITES, ExportImportConstants.DataTypes.USER_INFO, ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION));
        validateOptions.setImportFormat(ExportImportConstants.Formats.GEOPULSE);

        ImportJob validateJob = new ImportJob(testUser.getId(), validateOptions, "test-export.zip", exportedData);

        List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(validateJob);

        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.FAVORITES), "Favorites should be included");
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.USER_INFO), "User info should be included");
        assertFalse(detectedDataTypes.contains(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION), "Reverse geocoding should NOT be auto-included without timeline");

        log.info("=== Export Without Timeline Dependencies Test Completed Successfully ===");
    }
}