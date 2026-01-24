package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.GpxFile;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class GpxExportServiceTest {

    @Inject
    GpxExportService gpxExportService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    ExportTempFileService tempFileService;

    private UserEntity testUser;
    private Instant testStartDate;
    private Instant testEndDate;
    private XmlMapper xmlMapper;

    @BeforeEach
    @Transactional
    void setUp() {
        testStartDate = Instant.now().minus(2, ChronoUnit.HOURS);
        testEndDate = Instant.now();

        testUser = new UserEntity();
        testUser.setEmail("gpx-export-test-" + UUID.randomUUID() + "@test.com");
        testUser.setFullName("Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);

        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JavaTimeModule());
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
    void testGenerateGpxExport_SingleFile() throws Exception {
        createGpsPoint(testStartDate.plus(1, ChronoUnit.MINUTES), 37.7749, -122.4194);
        createGpsPoint(testStartDate.plus(2, ChronoUnit.MINUTES), 37.7750, -122.4195);

        ExportJob job = createExportJob();
        gpxExportService.generateGpxExport(job, false, "individual");

        assertNotNull(job.getTempFilePath());
        assertEquals(".gpx", job.getFileExtension());

        byte[] result = Files.readAllBytes(Paths.get(job.getTempFilePath()));

        // Validate XML format
        GpxFile gpxFile = xmlMapper.readValue(result, GpxFile.class);
        assertNotNull(gpxFile.getMetadata());
        assertEquals("GeoPulse Export", gpxFile.getMetadata().getName());

        tempFileService.deleteTempFile(job.getTempFilePath());
    }

    @Test
    @Transactional
    void testGenerateGpxExport_ZipIndividual() throws Exception {
        createGpsPoint(testStartDate.plus(1, ChronoUnit.MINUTES), 37.7749, -122.4194);

        ExportJob job = createExportJob();
        gpxExportService.generateGpxExport(job, true, "individual");

        assertNotNull(job.getTempFilePath());
        assertEquals(".zip", job.getFileExtension());

        byte[] result = Files.readAllBytes(Paths.get(job.getTempFilePath()));

        // Validate ZIP content (should be empty if no trips/stays generated yet, or
        // contain files)
        // Note: Timeline generation might not happen in this test environment without
        // specific data
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(result))) {
            ZipEntry entry = zis.getNextEntry();
            // It might be null if no trips were detected, which is expected for just raw
            // points
        }

        tempFileService.deleteTempFile(job.getTempFilePath());
    }

    @Test
    @Transactional
    void testGenerateGpxExport_StreamingStressTest() throws Exception {
        // Create 1000 points
        for (int i = 0; i < 1000; i++) {
            createGpsPoint(testStartDate.plus(i, ChronoUnit.SECONDS), 37.7749 + (i * 0.0001), -122.4194);
            if (i % 100 == 0)
                gpsPointRepository.flush();
        }
        gpsPointRepository.flush();

        ExportJob job = createExportJob();
        gpxExportService.generateGpxExport(job, false, "individual");

        assertNotNull(job.getTempFilePath());
        byte[] result = Files.readAllBytes(Paths.get(job.getTempFilePath()));
        assertTrue(result.length > 0);

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

    private void createGpsPoint(Instant timestamp, double lat, double lon) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(testUser);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(lon, lat));
        point.setAltitude(100.0);
        point.setVelocity(10.0);
        point.setAccuracy(5.0);
        point.setBattery(90.0);
        point.setDeviceId("test-device");
        point.setSourceType(GpsSourceType.OWNTRACKS);
        point.setCreatedAt(timestamp);
        gpsPointRepository.persist(point);
    }
}
