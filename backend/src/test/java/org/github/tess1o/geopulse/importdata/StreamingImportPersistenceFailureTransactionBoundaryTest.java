package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportBatchPersistenceException;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class StreamingImportPersistenceFailureTransactionBoundaryTest {

    private static final String OWNTRACKS_BATCH_SIZE_SETTING = "import.owntracks-streaming-batch-size";
    private static final String CSV_BATCH_SIZE_SETTING = "import.csv-streaming-batch-size";
    private static final String GEOJSON_BATCH_SIZE_SETTING = "import.geojson-streaming-batch-size";

    @Inject
    ImportDataService importDataService;

    @Inject
    SystemSettingsService systemSettingsService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    ObjectMapper objectMapper;

    private UUID testUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail("streaming-boundary"))
                .passwordHash("test-hash")
                .timezone("UTC")
                .isActive(true)
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.persist(user);
        testUserId = user.getId();
    }

    @AfterEach
    @Transactional
    void resetSettings() {
        systemSettingsService.resetToDefault(OWNTRACKS_BATCH_SIZE_SETTING);
        systemSettingsService.resetToDefault(CSV_BATCH_SIZE_SETTING);
        systemSettingsService.resetToDefault(GEOJSON_BATCH_SIZE_SETTING);
    }

    @Test
    void ownTracksPersistenceFailureRollsBackEarlierFlushedBatches() throws Exception {
        systemSettingsService.setValue(OWNTRACKS_BATCH_SIZE_SETTING, "2", testUserId);

        Instant baseTime = Instant.parse("2026-01-01T00:00:00Z");
        String oversizedDeviceId = "x".repeat(300);
        List<OwnTracksLocationMessage> messages = List.of(
                ownTracksPoint("ok-1", 37.7749, -122.4194, baseTime),
                ownTracksPoint("ok-2", 37.7750, -122.4195, baseTime.plusSeconds(60)),
                ownTracksPoint(oversizedDeviceId, 37.7751, -122.4196, baseTime.plusSeconds(120))
        );

        ImportJob job = new ImportJob(
                testUserId,
                importOptions(),
                "owntracks-persistence-failure.json",
                objectMapper.writeValueAsBytes(messages));

        IOException failure = assertThrows(IOException.class, () -> importDataService.processImportData(job));

        assertTrue(hasCause(failure, ImportBatchPersistenceException.class),
                "Database persistence failures must abort the import instead of being treated as skipped records");
        assertEquals(0, countGpsRows(),
                "The first flushed streaming batch must roll back when a later streaming batch fails");
        assertNull(job.getTimelineJobId(),
                "Timeline generation should not start after GPS persistence fails");
    }

    @Test
    void csvPersistenceFailureRollsBackEarlierFlushedBatches() throws Exception {
        systemSettingsService.setValue(CSV_BATCH_SIZE_SETTING, "2", testUserId);

        ImportJob job = new ImportJob(
                testUserId,
                importOptions("csv"),
                "csv-persistence-failure.csv",
                csvBytes("csv-ok-1", "csv-ok-2", "x".repeat(300)));

        IOException failure = assertThrows(IOException.class, () -> importDataService.processImportData(job));

        assertTrue(hasCause(failure, ImportBatchPersistenceException.class),
                "CSV database flush failures should abort the import");
        assertEquals(0, countGpsRows(),
                "The first flushed CSV batch must roll back when a later batch fails");
        assertNull(job.getTimelineJobId(),
                "Timeline generation should not start after CSV persistence fails");
    }

    @Test
    void geoJsonPersistenceFailureRollsBackEarlierFlushedBatches() throws Exception {
        systemSettingsService.setValue(GEOJSON_BATCH_SIZE_SETTING, "2", testUserId);

        ImportJob job = new ImportJob(
                testUserId,
                importOptions("geojson"),
                "geojson-persistence-failure.geojson",
                geoJsonBytes("geojson-ok-1", "geojson-ok-2", "x".repeat(300)));

        IOException failure = assertThrows(IOException.class, () -> importDataService.processImportData(job));

        assertTrue(hasCause(failure, ImportBatchPersistenceException.class),
                "GeoJSON database flush failures should abort the import");
        assertEquals(0, countGpsRows(),
                "The first flushed GeoJSON batch must roll back when a later batch fails");
        assertNull(job.getTimelineJobId(),
                "Timeline generation should not start after GeoJSON persistence fails");
    }

    private OwnTracksLocationMessage ownTracksPoint(String deviceId, double lat, double lon, Instant timestamp) {
        return OwnTracksLocationMessage.builder()
                .type("location")
                .tid(deviceId)
                .lat(lat)
                .lon(lon)
                .tst(timestamp.getEpochSecond())
                .acc(5.0)
                .build();
    }

    private ImportOptions importOptions() {
        return importOptions("owntracks");
    }

    private ImportOptions importOptions(String format) {
        ImportOptions options = new ImportOptions();
        options.setImportFormat(format);
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        return options;
    }

    private byte[] csvBytes(String firstDeviceId, String secondDeviceId, String thirdDeviceId) {
        return """
                timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type
                2026-01-01T00:00:00Z,37.7749,-122.4194,5,,,,%s,CSV
                2026-01-01T00:01:00Z,37.7750,-122.4195,5,,,,%s,CSV
                2026-01-01T00:02:00Z,37.7751,-122.4196,5,,,,%s,CSV
                """.formatted(firstDeviceId, secondDeviceId, thirdDeviceId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] geoJsonBytes(String firstDeviceId, String secondDeviceId, String thirdDeviceId) {
        return """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {"type": "Point", "coordinates": [-122.4194, 37.7749]},
                      "properties": {"timestamp": "2026-01-01T00:00:00Z", "deviceId": "%s", "sourceType": "GEOJSON"}
                    },
                    {
                      "type": "Feature",
                      "geometry": {"type": "Point", "coordinates": [-122.4195, 37.7750]},
                      "properties": {"timestamp": "2026-01-01T00:01:00Z", "deviceId": "%s", "sourceType": "GEOJSON"}
                    },
                    {
                      "type": "Feature",
                      "geometry": {"type": "Point", "coordinates": [-122.4196, 37.7751]},
                      "properties": {"timestamp": "2026-01-01T00:02:00Z", "deviceId": "%s", "sourceType": "GEOJSON"}
                    }
                  ]
                }
                """.formatted(firstDeviceId, secondDeviceId, thirdDeviceId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private long countGpsRows() {
        return QuarkusTransaction.requiringNew().call(() ->
                gpsPointRepository.count("user.id = ?1", testUserId));
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> expectedType) {
        Throwable current = failure;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
