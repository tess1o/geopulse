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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for GeoJSON streaming export service.
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

    @Inject
    ExportTempFileService tempFileService;

    private UserEntity testUser;
    private Instant testStartDate;
    private Instant testEndDate;

    @BeforeEach
    @Transactional
    void setUp() {
        testStartDate = Instant.now().minus(2, ChronoUnit.HOURS);
        testEndDate = Instant.now();

        testUser = new UserEntity();
        testUser.setEmail("geojson-export-test-" + UUID.randomUUID() + "@test.com");
        testUser.setFullName("Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);
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
        ExportJob job = createExportJob();
        geoJsonExportService.generateGeoJsonExport(job);

        assertNotNull(job.getTempFilePath());
        byte[] result = Files.readAllBytes(Paths.get(job.getTempFilePath()));

        JsonNode root = objectMapper.readTree(result);
        assertEquals("FeatureCollection", root.get("type").asText());
        assertTrue(root.get("features").isArray());
        assertEquals(0, root.get("features").size());

        tempFileService.deleteTempFile(job.getTempFilePath());
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_SinglePoint() throws Exception {
        createGpsPoint(testStartDate, 37.7749, -122.4194, 100.0, 15.0, 95.0);
        ExportJob job = createExportJob();

        geoJsonExportService.generateGeoJsonExport(job);

        assertNotNull(job.getTempFilePath());
        byte[] result = Files.readAllBytes(Paths.get(job.getTempFilePath()));

        JsonNode root = objectMapper.readTree(result);
        assertEquals("FeatureCollection", root.get("type").asText());
        assertEquals(1, root.get("features").size());

        JsonNode feature = root.get("features").get(0);
        assertEquals("Feature", feature.get("type").asText());

        JsonNode geometry = feature.get("geometry");
        assertEquals("Point", geometry.get("type").asText());
        assertEquals(-122.4194, geometry.get("coordinates").get(0).asDouble(), 0.0001);
        assertEquals(37.7749, geometry.get("coordinates").get(1).asDouble(), 0.0001);

        JsonNode properties = feature.get("properties");
        assertEquals(100.0, properties.get("altitude").asDouble(), 0.1);
        assertEquals(15.0, properties.get("velocity").asDouble(), 0.1);

        tempFileService.deleteTempFile(job.getTempFilePath());
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_StreamingLargeDataset() throws Exception {
        log.info("Creating 2000 GPS points for streaming test...");
        int pointCount = 2000;

        for (int i = 0; i < pointCount; i++) {
            createGpsPoint(
                    testStartDate.plus(i, ChronoUnit.SECONDS),
                    37.7749 + (i * 0.0001),
                    -122.4194 + (i * 0.0001),
                    100.0, 15.0, 95.0);
            if (i % 500 == 0)
                gpsPointRepository.flush();
        }
        gpsPointRepository.flush();

        ExportJob job = createExportJob();

        long startTime = System.currentTimeMillis();
        geoJsonExportService.generateGeoJsonExport(job);
        long exportTime = System.currentTimeMillis() - startTime;

        log.info("Streamed {} points in {} ms", pointCount, exportTime);

        assertNotNull(job.getTempFilePath());
        byte[] result = Files.readAllBytes(Paths.get(job.getTempFilePath()));

        JsonNode root = objectMapper.readTree(result);
        assertEquals(pointCount, root.get("features").size());

        log.info("✅ Streaming export validated: {} features", root.get("features").size());

        tempFileService.deleteTempFile(job.getTempFilePath());
    }

    @Test
    @Transactional
    void testGenerateGeoJsonExport_ProgressTracking() throws Exception {
        for (int i = 0; i < 100; i++) {
            createGpsPoint(
                    testStartDate.plus(i, ChronoUnit.SECONDS),
                    37.7749, -122.4194, 100.0, 15.0, 95.0);
        }
        gpsPointRepository.flush();

        ExportJob job = createExportJob();
        geoJsonExportService.generateGeoJsonExport(job);

        assertTrue(job.getProgress() >= 5);
        assertNotNull(job.getProgressMessage());

        tempFileService.deleteTempFile(job.getTempFilePath());
    }

    private ExportJob createExportJob() {
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(testStartDate);
        dateRange.setEndDate(testEndDate);

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setJobId(UUID.randomUUID());
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
        point.setCreatedAt(timestamp);
        gpsPointRepository.persist(point);
    }
}
