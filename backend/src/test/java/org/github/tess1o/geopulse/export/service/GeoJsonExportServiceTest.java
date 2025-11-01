package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for GeoJSON streaming export service.
 * Tests memory-efficient streaming, progress tracking, and GeoJSON format compliance.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class GeoJsonExportServiceTest {

    @Inject
    GeoJsonExportService geoJsonExportService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    ObjectMapper objectMapper;

    private UserEntity testUser;
    private Instant testStartDate;
    private Instant testEndDate;

    @BeforeEach
    @Transactional
    void setUp() {
        testStartDate = Instant.now().minus(2, ChronoUnit.HOURS);
        testEndDate = Instant.now();

        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("geojson-test-" + UUID.randomUUID() + "@test.com");
        testUser.setFullName("GeoJSON Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);

        log.info("Test setup completed: user {}", testUser.getId());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            gpsPointRepository.delete("user.id", testUser.getId());
            userRepository.delete(testUser);
        }
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_EmptyData() throws Exception {
        // Arrange
        ExportJob job = createExportJob();

        // Act
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);

        // Assert
        assertNotNull(geoJsonBytes);
        String geoJson = new String(geoJsonBytes);

        // Parse and validate
        JsonNode root = objectMapper.readTree(geoJson);
        assertEquals("FeatureCollection", root.get("type").asText());
        assertTrue(root.has("features"));
        assertEquals(0, root.get("features").size());

        log.info("Empty GeoJSON export: {}", geoJson);
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_SinglePoint() throws Exception {
        // Arrange
        createGpsPoint(testStartDate, 37.7749, -122.4194, 100.0, 15.0, 95.0);
        ExportJob job = createExportJob();

        // Act
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);

        // Assert
        assertNotNull(geoJsonBytes);
        String geoJson = new String(geoJsonBytes);
        log.info("GeoJSON output: {}", geoJson);

        // Parse and validate structure
        JsonNode root = objectMapper.readTree(geoJson);
        assertEquals("FeatureCollection", root.get("type").asText());

        JsonNode features = root.get("features");
        assertEquals(1, features.size());

        // Validate feature
        JsonNode feature = features.get(0);
        assertEquals("Feature", feature.get("type").asText());

        // Validate geometry
        JsonNode geometry = feature.get("geometry");
        assertEquals("Point", geometry.get("type").asText());

        JsonNode coordinates = geometry.get("coordinates");
        assertEquals(-122.4194, coordinates.get(0).asDouble(), 0.0001); // longitude
        assertEquals(37.7749, coordinates.get(1).asDouble(), 0.0001);   // latitude
        assertEquals(100.0, coordinates.get(2).asDouble(), 0.1);        // altitude

        // Validate properties
        JsonNode properties = feature.get("properties");
        assertNotNull(properties.get("timestamp"));
        assertEquals(100.0, properties.get("altitude").asDouble(), 0.1);
        assertEquals(15.0, properties.get("velocity").asDouble(), 0.1);
        assertEquals(95.0, properties.get("battery").asDouble(), 0.1);
        assertEquals("OWNTRACKS", properties.get("sourceType").asText());
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_MultiplePoints() throws Exception {
        // Arrange - Create 5 GPS points
        for (int i = 0; i < 5; i++) {
            createGpsPoint(
                testStartDate.plus(i * 10L, ChronoUnit.MINUTES),
                37.7749 + (i * 0.001),
                -122.4194 + (i * 0.001),
                100.0 + i,
                15.0 + i,
                95.0 - i
            );
        }

        ExportJob job = createExportJob();

        // Act
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);

        // Assert
        String geoJson = new String(geoJsonBytes);
        JsonNode root = objectMapper.readTree(geoJson);

        JsonNode features = root.get("features");
        assertEquals(5, features.size());

        // Verify all features are valid Points
        for (int i = 0; i < 5; i++) {
            JsonNode feature = features.get(i);
            assertEquals("Feature", feature.get("type").asText());
            assertEquals("Point", feature.get("geometry").get("type").asText());

            // Verify coordinates
            JsonNode coordinates = feature.get("geometry").get("coordinates");
            assertEquals(3, coordinates.size(), "Should have lon, lat, alt");
        }
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_StreamingLargeDataset() throws Exception {
        // Arrange - Create 3000 GPS points to test streaming
        log.info("Creating 3000 GPS points for streaming test...");
        int pointCount = 3000;

        for (int i = 0; i < pointCount; i++) {
            createGpsPoint(
                testStartDate.plus(i, ChronoUnit.SECONDS),
                37.7749 + (i * 0.0001),
                -122.4194 + (i * 0.0001),
                100.0 + (i % 100),
                15.0 + (i % 50),
                95.0 - (i % 10)
            );

            if (i % 500 == 0) {
                gpsPointRepository.flush();
                log.info("Created {} points...", i);
            }
        }
        gpsPointRepository.flush();

        ExportJob job = createExportJob();

        // Act
        long startTime = System.currentTimeMillis();
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);
        long exportTime = System.currentTimeMillis() - startTime;

        log.info("Streamed {} points in {} ms, generated {} bytes",
            pointCount, exportTime, geoJsonBytes.length);

        // Assert
        String geoJson = new String(geoJsonBytes);
        JsonNode root = objectMapper.readTree(geoJson);

        assertEquals("FeatureCollection", root.get("type").asText());

        JsonNode features = root.get("features");
        assertEquals(pointCount, features.size(),
            "Should have streamed all " + pointCount + " points");

        // Verify first and last features
        JsonNode firstFeature = features.get(0);
        JsonNode firstCoords = firstFeature.get("geometry").get("coordinates");
        assertEquals(-122.4194, firstCoords.get(0).asDouble(), 0.0001);
        assertEquals(37.7749, firstCoords.get(1).asDouble(), 0.0001);

        JsonNode lastFeature = features.get(pointCount - 1);
        JsonNode lastCoords = lastFeature.get("geometry").get("coordinates");
        double expectedLastLon = -122.4194 + ((pointCount - 1) * 0.0001);
        double expectedLastLat = 37.7749 + ((pointCount - 1) * 0.0001);
        assertEquals(expectedLastLon, lastCoords.get(0).asDouble(), 0.0001);
        assertEquals(expectedLastLat, lastCoords.get(1).asDouble(), 0.0001);

        log.info("✅ Streaming export validated: {} features", features.size());
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_ProgressTracking() throws Exception {
        // Arrange
        for (int i = 0; i < 100; i++) {
            createGpsPoint(
                testStartDate.plus(i, ChronoUnit.SECONDS),
                37.7749 + (i * 0.0001),
                -122.4194 + (i * 0.0001),
                100.0, 15.0, 95.0
            );
        }
        gpsPointRepository.flush();

        ExportJob job = createExportJob();
        assertEquals(0, job.getProgress());

        // Act
        geoJsonExportService.generateGeoJsonExport(job);

        // Assert
        assertTrue(job.getProgress() >= 5, "Progress should have started (>= 5%)");
        assertNotNull(job.getProgressMessage());
        log.info("Final progress: {}% - {}", job.getProgress(), job.getProgressMessage());
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_CoordinateOrder() throws Exception {
        // Arrange - GeoJSON uses [longitude, latitude, altitude] order
        createGpsPoint(testStartDate, 37.7749, -122.4194, 100.0, 15.0, 95.0);
        ExportJob job = createExportJob();

        // Act
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);

        // Assert
        String geoJson = new String(geoJsonBytes);
        JsonNode root = objectMapper.readTree(geoJson);
        JsonNode coordinates = root.get("features").get(0).get("geometry").get("coordinates");

        assertEquals(-122.4194, coordinates.get(0).asDouble(), 0.0001,
            "First coordinate should be longitude");
        assertEquals(37.7749, coordinates.get(1).asDouble(), 0.0001,
            "Second coordinate should be latitude");
        assertEquals(100.0, coordinates.get(2).asDouble(), 0.1,
            "Third coordinate should be altitude");
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_OptionalFields() throws Exception {
        // Arrange - Create point with minimal data (no altitude, no velocity, no battery)
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(testUser);
        point.setTimestamp(testStartDate);
        point.setCoordinates(GeoUtils.createPoint(-122.4194, 37.7749));
        // No altitude, velocity, battery
        point.setSourceType(GpsSourceType.HOME_ASSISTANT);
        gpsPointRepository.persist(point);

        ExportJob job = createExportJob();

        // Act
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);

        // Assert
        String geoJson = new String(geoJsonBytes);
        JsonNode root = objectMapper.readTree(geoJson);
        JsonNode feature = root.get("features").get(0);

        // Should only have 2 coordinates (lon, lat) if no altitude
        JsonNode coordinates = feature.get("geometry").get("coordinates");
        assertEquals(2, coordinates.size(), "Should only have lon/lat without altitude");

        // Properties should not include null optional fields
        JsonNode properties = feature.get("properties");
        assertTrue(properties.has("timestamp"));
        assertTrue(properties.has("sourceType"));
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_DateRangeFiltering() throws Exception {
        // Arrange - Create points in and out of range
        Instant rangeStart = testStartDate.plus(30, ChronoUnit.MINUTES);
        Instant rangeEnd = testStartDate.plus(60, ChronoUnit.MINUTES);

        createGpsPoint(testStartDate.plus(10, ChronoUnit.MINUTES), 37.7749, -122.4194, 100.0, 15.0, 95.0); // Before
        createGpsPoint(testStartDate.plus(40, ChronoUnit.MINUTES), 37.7750, -122.4195, 100.0, 15.0, 95.0); // Inside
        createGpsPoint(testStartDate.plus(50, ChronoUnit.MINUTES), 37.7751, -122.4196, 100.0, 15.0, 95.0); // Inside
        createGpsPoint(testStartDate.plus(90, ChronoUnit.MINUTES), 37.7752, -122.4197, 100.0, 15.0, 95.0); // After

        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(rangeStart);
        dateRange.setEndDate(rangeEnd);

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setDateRange(dateRange);

        // Act
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);

        // Assert
        String geoJson = new String(geoJsonBytes);
        JsonNode root = objectMapper.readTree(geoJson);
        JsonNode features = root.get("features");

        assertEquals(2, features.size(), "Should only include points within date range");
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_ValidJsonFormat() throws Exception {
        // Arrange
        for (int i = 0; i < 10; i++) {
            createGpsPoint(
                testStartDate.plus(i, ChronoUnit.MINUTES),
                37.7749 + (i * 0.001),
                -122.4194 + (i * 0.001),
                100.0, 15.0, 95.0
            );
        }

        ExportJob job = createExportJob();

        // Act
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);

        // Assert - Validate JSON is well-formed
        String geoJson = new String(geoJsonBytes);
        assertDoesNotThrow(() -> objectMapper.readTree(geoJson),
            "Generated GeoJSON should be valid JSON");

        // Validate GeoJSON structure
        JsonNode root = objectMapper.readTree(geoJson);
        assertTrue(root.isObject());
        assertTrue(root.has("type"));
        assertTrue(root.has("features"));
        assertTrue(root.get("features").isArray());

        // Validate each feature
        for (JsonNode feature : root.get("features")) {
            assertTrue(feature.has("type"));
            assertTrue(feature.has("geometry"));
            assertTrue(feature.has("properties"));

            JsonNode geometry = feature.get("geometry");
            assertTrue(geometry.has("type"));
            assertTrue(geometry.has("coordinates"));
            assertTrue(geometry.get("coordinates").isArray());
        }

        log.info("✅ GeoJSON format validation passed");
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_MemoryEfficiency() throws Exception {
        // Arrange - Create large dataset
        log.info("Testing memory efficiency with 5000 points...");
        int pointCount = 5000;

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        for (int i = 0; i < pointCount; i++) {
            createGpsPoint(
                testStartDate.plus(i, ChronoUnit.SECONDS),
                37.7749 + (i * 0.00001),
                -122.4194 + (i * 0.00001),
                100.0, 15.0, 95.0
            );

            if (i % 1000 == 0 && i > 0) {
                gpsPointRepository.flush();
            }
        }
        gpsPointRepository.flush();

        ExportJob job = createExportJob();

        runtime.gc();
        long memoryAfterInsert = runtime.totalMemory() - runtime.freeMemory();

        // Act
        long startTime = System.currentTimeMillis();
        byte[] geoJsonBytes = geoJsonExportService.generateGeoJsonExport(job);
        long exportTime = System.currentTimeMillis() - startTime;

        runtime.gc();
        long memoryAfterExport = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfterExport - memoryAfterInsert;

        log.info("Memory before: {} MB", memoryBefore / (1024 * 1024));
        log.info("Memory after insert: {} MB", memoryAfterInsert / (1024 * 1024));
        log.info("Memory after export: {} MB", memoryAfterExport / (1024 * 1024));
        log.info("Memory used for export: {} MB", memoryUsed / (1024 * 1024));
        log.info("Export time: {} ms", exportTime);

        // Assert
        JsonNode root = objectMapper.readTree(geoJsonBytes);
        assertEquals(pointCount, root.get("features").size());

        // Memory should not increase dramatically with streaming
        long maxAllowedMemoryMB = 200; // Generous limit
        assertTrue(memoryUsed / (1024 * 1024) < maxAllowedMemoryMB,
            String.format("Export used %d MB, expected < %d MB with streaming",
                memoryUsed / (1024 * 1024), maxAllowedMemoryMB));

        log.info("✅ Memory efficiency verified: {} points exported with {} MB overhead",
            pointCount, memoryUsed / (1024 * 1024));
    }

    // ========================================
    // Helper Methods
    // ========================================

    private ExportJob createExportJob() {
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(testStartDate);
        dateRange.setEndDate(testEndDate);

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setDateRange(dateRange);
        return job;
    }

    private void createGpsPoint(Instant timestamp, double lat, double lon,
                                double altitude, double velocity, double battery) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(testUser);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(lon, lat));
        point.setAltitude(altitude);
        point.setVelocity(velocity);
        point.setAccuracy(5.0);
        point.setBattery(battery);
        point.setDeviceId("test-device");
        point.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPointRepository.persist(point);
    }
}
