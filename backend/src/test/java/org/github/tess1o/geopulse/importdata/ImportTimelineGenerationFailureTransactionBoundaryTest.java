package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ImportTimelineGenerationFailureTransactionBoundaryTest {

    @Inject
    ImportDataService importDataService;

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

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity testUser = new UserEntity();
        testUser.setEmail(TestIds.uniqueEmail("timeline-generation-failure"));
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
    void timelineGenerationFailureFailsTimelineJobAndRollsBackGpsImport() throws Exception {
        StreamingTimelineGenerationService failingTimelineService =
                Mockito.mock(StreamingTimelineGenerationService.class);
        doThrow(new RuntimeException("simulated timeline failure"))
                .when(failingTimelineService)
                .generateTimelineFromTimestamp(eq(testUserId), any(Instant.class), any(UUID.class));
        QuarkusMock.installMockForType(failingTimelineService, StreamingTimelineGenerationService.class);

        ImportJob job = new ImportJob(
                testUserId,
                importOptions(),
                "timeline-generation-failure.json",
                ownTracksBytes());

        IOException failure = assertThrows(IOException.class, () -> importDataService.processImportData(job));

        assertTrue(containsMessage(failure, "Timeline generation failed after import"));
        assertNotNull(job.getTimelineJobId(), "Timeline job should be created before timeline generation starts");

        TimelineJobProgress timelineJob = timelineJobProgressService
                .getJobProgress(job.getTimelineJobId())
                .orElseThrow();
        assertEquals(TimelineJobProgress.JobStatus.FAILED, timelineJob.getStatus());
        assertNotNull(timelineJob.getErrorMessage());
        assertTrue(timelineJob.getErrorMessage().contains("Import failed")
                        || timelineJob.getErrorMessage().contains("Timeline generation failed"),
                "Timeline job should keep a failure reason after the import transaction aborts");
        assertEquals(0, countGpsRows(),
                "GPS rows must roll back when timeline generation aborts the import transaction");
    }

    private byte[] ownTracksBytes() throws IOException {
        Instant baseTime = Instant.parse("2026-01-01T00:00:00Z");
        List<OwnTracksLocationMessage> messages = List.of(
                ownTracksPoint(baseTime),
                ownTracksPoint(baseTime.plusSeconds(60)),
                ownTracksPoint(baseTime.plusSeconds(120))
        );
        return objectMapper.writeValueAsBytes(messages);
    }

    private OwnTracksLocationMessage ownTracksPoint(Instant timestamp) {
        return OwnTracksLocationMessage.builder()
                .type("location")
                .tid("timeline-failure-device")
                .lat(37.7749)
                .lon(-122.4194)
                .tst(timestamp.getEpochSecond())
                .acc(5.0)
                .build();
    }

    private ImportOptions importOptions() {
        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));
        return options;
    }

    private long countGpsRows() {
        return QuarkusTransaction.requiringNew().call(() ->
                gpsPointRepository.count("user.id = ?1", testUserId));
    }

    private boolean containsMessage(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
