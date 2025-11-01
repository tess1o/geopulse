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
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
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
 * Comprehensive unit tests for OwnTracks streaming export service.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class OwnTracksExportServiceTest {

    @Inject
    OwnTracksExportService ownTracksExportService;

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

        testUser = new UserEntity();
        testUser.setEmail("owntracks-export-test-" + UUID.randomUUID() + "@test.com");
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
    void testGenerateOwnTracksExport_EmptyData() throws Exception {
        ExportJob job = createExportJob();
        byte[] result = ownTracksExportService.generateOwnTracksExport(job);

        assertNotNull(result);
        JsonNode root = objectMapper.readTree(result);
        assertTrue(root.isArray());
        assertEquals(0, root.size());
    }

    @Test
    @Transactional
    void testGenerateOwnTracksExport_SinglePoint() throws Exception {
        createGpsPoint(testStartDate, 37.7749, -122.4194, 100.0, 15.0, 95.0);
        ExportJob job = createExportJob();

        byte[] result = ownTracksExportService.generateOwnTracksExport(job);

        JsonNode root = objectMapper.readTree(result);
        assertEquals(1, root.size());

        JsonNode message = root.get(0);
        assertEquals(37.7749, message.get("lat").asDouble(), 0.0001);
        assertEquals(-122.4194, message.get("lon").asDouble(), 0.0001);
        assertEquals(100.0, message.get("alt").asDouble(), 0.1);
        assertEquals(15.0, message.get("vel").asDouble(), 0.1);
        assertEquals(95, message.get("batt").asInt());
    }

    @Test
    @Transactional
    void testGenerateOwnTracksExport_StreamingLargeDataset() throws Exception {
        log.info("Creating 2000 GPS points for streaming test...");
        int pointCount = 2000;

        for (int i = 0; i < pointCount; i++) {
            createGpsPoint(
                testStartDate.plus(i, ChronoUnit.SECONDS),
                37.7749 + (i * 0.0001),
                -122.4194 + (i * 0.0001),
                100.0, 15.0, 95.0
            );
            if (i % 500 == 0) gpsPointRepository.flush();
        }
        gpsPointRepository.flush();

        ExportJob job = createExportJob();

        long startTime = System.currentTimeMillis();
        byte[] result = ownTracksExportService.generateOwnTracksExport(job);
        long exportTime = System.currentTimeMillis() - startTime;

        log.info("Streamed {} points in {} ms", pointCount, exportTime);

        JsonNode root = objectMapper.readTree(result);
        assertEquals(pointCount, root.size());

        log.info("✅ Streaming export validated: {} messages", root.size());
    }

    @Test
    @Transactional
    void testGenerateOwnTracksExport_ProgressTracking() throws Exception {
        for (int i = 0; i < 100; i++) {
            createGpsPoint(
                testStartDate.plus(i, ChronoUnit.SECONDS),
                37.7749, -122.4194, 100.0, 15.0, 95.0
            );
        }
        gpsPointRepository.flush();

        ExportJob job = createExportJob();
        ownTracksExportService.generateOwnTracksExport(job);

        assertTrue(job.getProgress() >= 5);
        assertNotNull(job.getProgressMessage());
    }

    @Test
    @Transactional
    void testGenerateOwnTracksExport_ValidJsonArrayFormat() throws Exception {
        for (int i = 0; i < 10; i++) {
            createGpsPoint(
                testStartDate.plus(i, ChronoUnit.MINUTES),
                37.7749, -122.4194, 100.0, 15.0, 95.0
            );
        }

        ExportJob job = createExportJob();
        byte[] result = ownTracksExportService.generateOwnTracksExport(job);

        String json = new String(result);
        assertDoesNotThrow(() -> objectMapper.readValue(json, OwnTracksLocationMessage[].class));
    }

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
        point.setCreatedAt(timestamp); // Required for OwnTracks export
        gpsPointRepository.persist(point);
    }
}
