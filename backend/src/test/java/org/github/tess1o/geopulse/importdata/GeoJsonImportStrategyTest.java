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
import org.github.tess1o.geopulse.importdata.service.GeoJsonImportStrategy;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
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
 * Integration test for GeoJSON streaming import functionality.
 * Tests memory-efficient parsing and import of large GeoJSON files.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class GeoJsonImportStrategyTest {

    @Inject
    GeoJsonImportStrategy geoJsonImportStrategy;

    @Inject
    ImportJobService importJobService;

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
        cleanupTestData();

        // Create test user
        testUser = userRepository.find("email", "test-geojson@geopulse.app").firstResult();
        if (testUser == null) {
            testUser = new UserEntity();
            testUser.setEmail("test-geojson@geopulse.app");
            testUser.setFullName("GeoJSON Test User");
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
        gpsPointRepository.delete("user.email = ?1", "test-geojson@geopulse.app");
        userRepository.delete("email = ?1", "test-geojson@geopulse.app");
    }

    @Test
    @Transactional
    void testGeoJsonImportWithPoints() throws Exception {
        log.info("=== Testing GeoJSON Streaming Import with Points ===");

        String geoJsonContent = createTestGeoJsonWithPoints();
        byte[] geoJsonData = geoJsonContent.getBytes();

        log.info("Created test GeoJSON data: {} bytes", geoJsonData.length);

        // Validate the data
        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("geojson");

        ImportJob importJob = importJobService.createImportJob(
                testUser.getId(), importOptions, "test-points.geojson", geoJsonData);

        List<String> detectedDataTypes = geoJsonImportStrategy.validateAndDetectDataTypes(importJob);
        assertEquals(1, detectedDataTypes.size());
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.RAW_GPS));

        log.info("Validation successful using streaming parser");

        // Process the import
        long beforeImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(0, beforeImportCount, "Should start with no GPS points");

        geoJsonImportStrategy.processImportData(importJob);

        // Verify import results
        long afterImportCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(3, afterImportCount, "Should have imported 3 GPS points");

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        log.info("Import completed: {} GPS points imported", afterImportCount);

        // Verify imported data
        for (GpsPointEntity point : importedPoints) {
            assertEquals(GpsSourceType.GEOJSON, point.getSourceType());
            assertNotNull(point.getTimestamp());
            assertNotNull(point.getCoordinates());
        }
    }

    @Test
    @Transactional
    void testGeoJsonImportWithLineString() throws Exception {
        log.info("=== Testing GeoJSON Streaming Import with LineString ===");

        String geoJsonContent = createTestGeoJsonWithLineString();
        byte[] geoJsonData = geoJsonContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("geojson");

        ImportJob importJob = importJobService.createImportJob(
                testUser.getId(), importOptions, "test-linestring.geojson", geoJsonData);

        geoJsonImportStrategy.processImportData(importJob);

        // Clear entity manager to force fresh query
        entityManager.clear();

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // LineString with 3 coordinates should create 3 GPS points
        assertEquals(3, importedPoints.size(), "Should import 3 GPS points from LineString");

        for (GpsPointEntity point : importedPoints) {
            assertEquals(GpsSourceType.GEOJSON, point.getSourceType());
            assertNotNull(point.getCoordinates());
        }

        log.info("LineString import verified: {} points", importedPoints.size());
    }

    @Test
    @Transactional
    void testGeoJsonImportWithMixedGeometries() throws Exception {
        log.info("=== Testing GeoJSON Streaming Import with Mixed Geometries ===");

        String geoJsonContent = createTestGeoJsonWithMixedGeometries();
        byte[] geoJsonData = geoJsonContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("geojson");

        ImportJob importJob = importJobService.createImportJob(
                testUser.getId(), importOptions, "test-mixed.geojson", geoJsonData);

        geoJsonImportStrategy.processImportData(importJob);

        entityManager.clear();

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // 2 Points + 3 from LineString = 5 total
        assertEquals(5, importedPoints.size(), "Should import 5 GPS points from mixed geometries");

        log.info("Mixed geometries import verified: {} points", importedPoints.size());
    }

    @Test
    @Transactional
    void testLargeGeoJsonFileStreaming() throws Exception {
        log.info("=== Testing Large GeoJSON File Streaming (Memory Efficiency Test) ===");

        // Create a large GeoJSON file with 10,000 features
        int featureCount = 10000;
        String largeGeoJson = createLargeTestGeoJson(featureCount);
        byte[] geoJsonData = largeGeoJson.getBytes();

        log.info("Created large GeoJSON file: {} bytes, {} features",
                geoJsonData.length, featureCount);

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("geojson");

        ImportJob importJob = importJobService.createImportJob(
                testUser.getId(), importOptions, "test-large.geojson", geoJsonData);

        // Monitor memory before import
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        log.info("Memory before import: {} MB", memoryBefore / (1024 * 1024));

        // Validate using streaming parser
        long validationStart = System.currentTimeMillis();
        List<String> detectedDataTypes = geoJsonImportStrategy.validateAndDetectDataTypes(importJob);
        long validationDuration = System.currentTimeMillis() - validationStart;

        log.info("Streaming validation completed in {} ms", validationDuration);

        // Process import using streaming
        long importStart = System.currentTimeMillis();
        geoJsonImportStrategy.processImportData(importJob);
        long importDuration = System.currentTimeMillis() - importStart;

        // Monitor memory after import
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = (memoryAfter - memoryBefore) / (1024 * 1024);
        log.info("Memory after import: {} MB (increase: {} MB)",
                memoryAfter / (1024 * 1024), memoryIncrease);

        log.info("Streaming import completed in {} ms", importDuration);
        log.info("Throughput: {} features/sec",
                (featureCount * 1000L) / Math.max(importDuration, 1));

        entityManager.clear();

        // Verify all points were imported
        long importedCount = gpsPointRepository.count("user = ?1", testUser);
        assertEquals(featureCount, importedCount,
                "Should have imported all features from large file");

        // Memory should not grow excessively (streaming keeps it low)
        // This is a rough check - memory increase should be well under file size
        long fileSizeMB = geoJsonData.length / (1024 * 1024);
        assertTrue(memoryIncrease < fileSizeMB * 2,
                "Memory increase should be less than 2x file size (streaming benefit)");

        log.info("Large file streaming test passed: {} features imported, memory efficient",
                importedCount);
    }

    @Test
    @Transactional
    void testInvalidGeoJsonHandling() throws Exception {
        log.info("=== Testing Invalid GeoJSON Handling ===");

        // Test 1: Invalid JSON
        byte[] invalidJson = "{invalid json".getBytes();
        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("geojson");

        ImportJob invalidJsonJob = importJobService.createImportJob(
                testUser.getId(), importOptions, "invalid.geojson", invalidJson);

        assertThrows(Exception.class, () -> {
            geoJsonImportStrategy.validateAndDetectDataTypes(invalidJsonJob);
        }, "Invalid JSON should throw validation exception");

        // Test 2: Empty FeatureCollection
        String emptyGeoJson = """
            {
              "type": "FeatureCollection",
              "features": []
            }
            """;
        byte[] emptyGeoJsonData = emptyGeoJson.getBytes();
        ImportJob emptyJob = importJobService.createImportJob(
                testUser.getId(), importOptions, "empty.geojson", emptyGeoJsonData);

        assertThrows(IllegalArgumentException.class, () -> {
            geoJsonImportStrategy.validateAndDetectDataTypes(emptyJob);
        }, "Empty GeoJSON should throw validation exception");

        // Test 3: Not a FeatureCollection
        String notFeatureCollection = """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [-122.4194, 37.7749]
              }
            }
            """;
        byte[] notFCData = notFeatureCollection.getBytes();
        ImportJob notFCJob = importJobService.createImportJob(
                testUser.getId(), importOptions, "notfc.geojson", notFCData);

        assertThrows(IllegalArgumentException.class, () -> {
            geoJsonImportStrategy.validateAndDetectDataTypes(notFCJob);
        }, "Non-FeatureCollection should throw validation exception");

        log.info("Invalid GeoJSON handling verified");
    }

    @Test
    @Transactional
    void testSourceTypeAssignment() throws Exception {
        log.info("=== Testing Source Type Assignment ===");

        String geoJsonContent = createTestGeoJsonWithPoints();
        byte[] geoJsonData = geoJsonContent.getBytes();

        ImportOptions importOptions = new ImportOptions();
        importOptions.setImportFormat("geojson");

        ImportJob importJob = importJobService.createImportJob(
                testUser.getId(), importOptions, "test-source.geojson", geoJsonData);

        geoJsonImportStrategy.processImportData(importJob);

        List<GpsPointEntity> importedPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                testUser.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS)
        );

        // Verify all imported points have GEOJSON source type
        for (GpsPointEntity point : importedPoints) {
            assertEquals(GpsSourceType.GEOJSON, point.getSourceType(),
                    "All imported points should have GEOJSON source type");
        }

        log.info("Source type assignment verified: {} points with GEOJSON source type",
                importedPoints.size());
    }

    private String createTestGeoJsonWithPoints() {
        Instant now = Instant.now();
        return String.format("""
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [25.5965, 49.5473]
                  },
                  "properties": {
                    "timestamp": "%s",
                    "altitude": 300,
                    "velocity": 5.0,
                    "accuracy": 10
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [25.5968, 49.5476]
                  },
                  "properties": {
                    "timestamp": "%s",
                    "altitude": 302,
                    "velocity": 4.5
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [25.5970, 49.5480]
                  },
                  "properties": {
                    "timestamp": "%s"
                  }
                }
              ]
            }
            """, now.toString(), now.plusSeconds(300).toString(), now.plusSeconds(600).toString());
    }

    private String createTestGeoJsonWithLineString() {
        Instant now = Instant.now();
        return String.format("""
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [25.5965, 49.5473],
                      [25.5968, 49.5476],
                      [25.5970, 49.5480]
                    ]
                  },
                  "properties": {
                    "timestamp": "%s"
                  }
                }
              ]
            }
            """, now.toString());
    }

    private String createTestGeoJsonWithMixedGeometries() {
        Instant now = Instant.now();
        return String.format("""
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [25.5965, 49.5473]
                  },
                  "properties": {
                    "timestamp": "%s"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [25.5968, 49.5476]
                  },
                  "properties": {
                    "timestamp": "%s"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [25.5970, 49.5480],
                      [25.5972, 49.5482],
                      [25.5975, 49.5485]
                    ]
                  },
                  "properties": {
                    "timestamp": "%s"
                  }
                }
              ]
            }
            """, now.toString(), now.plusSeconds(300).toString(), now.plusSeconds(600).toString());
    }

    private String createLargeTestGeoJson(int featureCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");

        Instant baseTime = Instant.now();
        for (int i = 0; i < featureCount; i++) {
            if (i > 0) {
                sb.append(",");
            }

            double lon = 25.5965 + (i * 0.0001);
            double lat = 49.5473 + (i * 0.0001);
            Instant timestamp = baseTime.plusSeconds(i * 60L); // 1 minute intervals

            sb.append(String.format("""
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [%.6f, %.6f]
                  },
                  "properties": {
                    "timestamp": "%s",
                    "altitude": %d,
                    "velocity": %.1f
                  }
                }
                """, lon, lat, timestamp.toString(), 300 + (i % 100), 5.0 + (i % 10)));
        }

        sb.append("]}");
        return sb.toString();
    }
}
