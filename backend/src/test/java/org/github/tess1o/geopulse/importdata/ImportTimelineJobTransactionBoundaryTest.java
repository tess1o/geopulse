package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.dto.ExportMetadataDto;
import org.github.tess1o.geopulse.export.dto.RawGpsDataDto;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(ImportTimelineJobTransactionBoundaryTest.SchedulerEnabledProfile.class)
@SerializedDatabaseTest
class ImportTimelineJobTransactionBoundaryTest {

    private static final Instant EXPORT_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant GPS_TIME = Instant.parse("2026-01-02T12:00:00Z");

    @Inject
    ImportJobService importJobService;

    @Inject
    TimelineJobProgressService timelineJobProgressService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper objectMapper;

    private UUID testUserId;
    private TestCoordinates.Scope coordinateScope;

    @BeforeEach
    @Transactional
    void setUp() {
        coordinateScope = TestCoordinates.newScope();

        UserEntity testUser = new UserEntity();
        testUser.setEmail(TestIds.uniqueEmail("timeline-boundary"));
        testUser.setPasswordHash("test-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole(Role.USER);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();

        testUserId = testUser.getId();
    }

    @Test
    void schedulerImportCompletesTimelineJobAfterSuccessfulImport() throws Exception {
        ImportJob job = importJobService.createImportJob(
                testUserId,
                ownTracksOptions(),
                "timeline-success.json",
                ownTracksBytes());

        importJobService.processImportJobs();

        assertEquals(ImportStatus.COMPLETED, job.getStatus());
        assertTrue(job.isDataProcessingCompleted());
        assertNotNull(job.getTimelineJobId(), "GPS imports should create a timeline job");

        TimelineJobProgress timelineJob = timelineJobProgressService
                .getJobProgress(job.getTimelineJobId())
                .orElseThrow();
        assertEquals(TimelineJobProgress.JobStatus.COMPLETED, timelineJob.getStatus());
        assertEquals(100, timelineJob.getProgressPercentage());
    }

    @Test
    void schedulerImportFailureAfterTimelineJobRollsBackGpsAndFailsTimelineJob() throws Exception {
        Point firstPoint = coordinateScope.point(-0.1276, 51.5074);
        ImportJob job = importJobService.createImportJob(
                testUserId,
                geoPulseOptions(),
                "timeline-failure.zip",
                geoPulseZipWithGpsAndMalformedUserInfo(firstPoint));

        importJobService.processImportJobs();

        assertEquals(ImportStatus.FAILED, job.getStatus());
        assertTrue(job.isDataProcessingCompleted(),
                "Failed imports should not be retried after data processing failed");
        assertNotNull(job.getTimelineJobId(), "The failure should happen after timeline job creation");

        TimelineJobProgress timelineJob = timelineJobProgressService
                .getJobProgress(job.getTimelineJobId())
                .orElseThrow();
        assertEquals(TimelineJobProgress.JobStatus.FAILED, timelineJob.getStatus());
        assertTrue(timelineJob.getErrorMessage().contains("Import failed"));
        assertEquals(0, countGpsRows("timeline-failure-device"),
                "GPS rows imported before the later failure must roll back");

        importJobService.processImportJobs();

        assertEquals(ImportStatus.FAILED, job.getStatus());
        assertEquals(0, countGpsRows("timeline-failure-device"),
                "The failed import must not be reprocessed on a later scheduler pass");
    }

    private byte[] ownTracksBytes() throws IOException {
        List<OwnTracksLocationMessage> messages = List.of(
                ownTracksPoint(37.7749, -122.4194, GPS_TIME),
                ownTracksPoint(37.7751, -122.4196, GPS_TIME.plusSeconds(60)),
                ownTracksPoint(37.7753, -122.4198, GPS_TIME.plusSeconds(120))
        );
        return objectMapper.writeValueAsBytes(messages);
    }

    private OwnTracksLocationMessage ownTracksPoint(double lat, double lon, Instant timestamp) {
        return OwnTracksLocationMessage.builder()
                .type("location")
                .tid("timeline-success-device")
                .lat(lat)
                .lon(lon)
                .tst(timestamp.getEpochSecond())
                .acc(5.0)
                .build();
    }

    private byte[] geoPulseZipWithGpsAndMalformedUserInfo(Point firstPoint) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(ExportImportConstants.FileNames.METADATA, metadataBytes(List.of(
                ExportImportConstants.DataTypes.RAW_GPS,
                ExportImportConstants.DataTypes.USER_INFO)));
        entries.put(ExportImportConstants.FileNames.RAW_GPS_DATA, rawGpsBytes(firstPoint));
        entries.put(ExportImportConstants.FileNames.USER_INFO,
                "{ malformed user info".getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    private byte[] metadataBytes(List<String> dataTypes) throws IOException {
        ExportMetadataDto metadata = ExportMetadataDto.builder()
                .exportJobId(UUID.randomUUID())
                .userId(testUserId)
                .exportDate(EXPORT_TIME)
                .dataTypes(dataTypes)
                .format(ExportImportConstants.Formats.JSON)
                .version(ExportImportConstants.Versions.CURRENT)
                .build();
        return objectMapper.writeValueAsBytes(metadata);
    }

    private byte[] rawGpsBytes(Point firstPoint) throws IOException {
        RawGpsDataDto rawGpsData = RawGpsDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.RAW_GPS)
                .exportDate(EXPORT_TIME)
                .startDate(GPS_TIME)
                .endDate(GPS_TIME.plusSeconds(120))
                .points(List.of(
                        rawGpsPoint(1L, firstPoint, GPS_TIME),
                        rawGpsPoint(2L, coordinateScope.point(firstPoint.getX() + 0.0002, firstPoint.getY() + 0.0002),
                                GPS_TIME.plusSeconds(60)),
                        rawGpsPoint(3L, coordinateScope.point(firstPoint.getX() + 0.0004, firstPoint.getY() + 0.0004),
                                GPS_TIME.plusSeconds(120))
                ))
                .build();
        return objectMapper.writeValueAsBytes(rawGpsData);
    }

    private RawGpsDataDto.GpsPointDto rawGpsPoint(Long id, Point point, Instant timestamp) {
        return RawGpsDataDto.GpsPointDto.builder()
                .id(id)
                .timestamp(timestamp)
                .latitude(point.getY())
                .longitude(point.getX())
                .accuracy(5.0)
                .source(GpsSourceType.GPX.name())
                .deviceId("timeline-failure-device")
                .build();
    }

    private byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutput.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutput.write(entry.getValue());
                zipOutput.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private ImportOptions ownTracksOptions() {
        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        return options;
    }

    private ImportOptions geoPulseOptions() {
        ImportOptions options = new ImportOptions();
        options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        options.setDataTypes(List.of(
                ExportImportConstants.DataTypes.RAW_GPS,
                ExportImportConstants.DataTypes.USER_INFO));
        return options;
    }

    private long countGpsRows(String deviceId) {
        return QuarkusTransaction.requiringNew().call(() ->
                gpsPointRepository.count("user.id = ?1 and deviceId = ?2", testUserId, deviceId));
    }

    public static class SchedulerEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "geopulse.import.scheduler.enabled", "true",
                    "quarkus.scheduler.enabled", "false"
            );
        }
    }
}
