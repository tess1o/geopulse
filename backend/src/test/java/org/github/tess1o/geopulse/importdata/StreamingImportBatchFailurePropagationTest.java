package org.github.tess1o.geopulse.importdata;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.BatchProcessor;
import org.github.tess1o.geopulse.importdata.service.ImportBatchPersistenceException;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class StreamingImportBatchFailurePropagationTest {

    @Inject
    ImportDataService importDataService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UUID testUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity testUser = new UserEntity();
        testUser.setEmail(TestIds.uniqueEmail("streaming-batch-failure"));
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
    void gpxBatchFailureAbortsImport() throws Exception {
        assertBatchFailureAbortsImport("gpx", "batch-failure.gpx", gpxBytes());
    }

    @Test
    void gpxZipBatchFailureAbortsImport() throws Exception {
        assertBatchFailureAbortsImport("gpx-zip", "batch-failure.zip", gpxZipBytes());
    }

    @Test
    void googleTimelineBatchFailureAbortsImport() throws Exception {
        assertBatchFailureAbortsImport("google-timeline", "batch-failure.json", googleTimelineBytes());
    }

    private void assertBatchFailureAbortsImport(String format, String fileName, byte[] data) throws IOException {
        installFailingBatchProcessor();

        ImportJob job = new ImportJob(
                testUserId,
                importOptions(format),
                fileName,
                data);

        IOException failure = assertThrows(IOException.class, () -> importDataService.processImportData(job));

        assertTrue(hasCause(failure, ImportBatchPersistenceException.class),
                format + " batch persistence failures should abort the import");
        assertEquals(0, countGpsRows(), "No GPS rows should persist after a batch failure");
        assertNull(job.getTimelineJobId(), "Timeline generation should not start after a batch failure");
    }

    private void installFailingBatchProcessor() {
        BatchProcessor failingBatchProcessor = Mockito.mock(BatchProcessor.class);
        doThrow(new ImportBatchPersistenceException(
                "simulated batch persistence failure",
                new RuntimeException("simulated database failure")))
                .when(failingBatchProcessor)
                .processBatch(anyList(), anyBoolean(), anyInt(), anyInt());
        QuarkusMock.installMockForType(failingBatchProcessor, BatchProcessor.class);
    }

    private ImportOptions importOptions(String format) {
        ImportOptions options = new ImportOptions();
        options.setImportFormat(format);
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        return options;
    }

    private byte[] gpxBytes() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="GeoPulse test">
                  <trk>
                    <name>Batch Failure</name>
                    <trkseg>
                      <trkpt lat="37.7749" lon="-122.4194">
                        <time>2026-01-01T00:00:00Z</time>
                      </trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] gpxZipBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            zipOutput.putNextEntry(new ZipEntry("batch-failure.gpx"));
            zipOutput.write(gpxBytes());
            zipOutput.closeEntry();
        }
        return output.toByteArray();
    }

    private byte[] googleTimelineBytes() {
        return """
                {
                  "locations": [
                    {
                      "latitudeE7": 377749000,
                      "longitudeE7": -1224194000,
                      "accuracy": 5,
                      "timestamp": "2026-01-01T00:00:00Z"
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
    }

    private long countGpsRows() {
        return QuarkusTransaction.requiringNew().call(() ->
                gpsPointRepository.count("user.id = ?1", testUserId));
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
