package org.github.tess1o.geopulse.export.service;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.testsupport.ExportTestFixtures;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class CsvExportServiceTest {
    @Inject
    CsvExportService csvExportService;
    @Inject
    UserRepository userRepository;
    @Inject
    GpsPointRepository gpsPointRepository;
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
        testUser.setEmail(TestIds.uniqueEmail("csv-export-test"));
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
    void testGenerateCsvExport_IncludesTelemetryColumnAndSerializedTelemetry() throws Exception {
        GpsPointEntity firstPoint = ExportTestFixtures.gpsPoint(
                testUser,
                testStartDate.plus(1, ChronoUnit.MINUTES),
                37.7749,
                -122.4194
        );
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("ignition", 1);
        telemetry.put("batt_v", 12.6);
        firstPoint.setTelemetry(new LinkedHashMap<>(telemetry));
        gpsPointRepository.persist(firstPoint);
        GpsPointEntity secondPoint = ExportTestFixtures.gpsPoint(
                testUser,
                testStartDate.plus(2, ChronoUnit.MINUTES),
                37.7750,
                -122.4195
        );
        gpsPointRepository.persist(secondPoint);
        gpsPointRepository.flush();
        ExportJob job = ExportTestFixtures.exportJob(testUser.getId(), testStartDate, testEndDate);
        csvExportService.generateCsvExport(job);
        assertNotNull(job.getTempFilePath());
        String csv = Files.readString(Paths.get(job.getTempFilePath()), StandardCharsets.UTF_8);
        String[] lines = csv.split("\\R");
        assertTrue(lines.length >= 2);
        assertTrue(lines[0].contains("source_type,telemetry"));
        assertTrue(csv.contains("\"\"ignition\"\":1"));
        assertTrue(csv.contains("\"\"batt_v\"\":12.6"));
        tempFileService.deleteTempFile(job.getTempFilePath());
    }
}
