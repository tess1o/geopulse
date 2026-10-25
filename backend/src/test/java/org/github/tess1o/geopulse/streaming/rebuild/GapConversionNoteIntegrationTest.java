package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideResponseDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A note awaiting its stay (a stay anchor without a row, as an imported or rewritten timeline can leave it) is
 * matched by time and place after a regeneration. Turning a data gap into a stay creates a new stay the same way,
 * so the note must be matched then too.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GapConversionNoteIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    EntityManager entityManager;

    private final Track track = new Track();
    private final double[] home = track.place(49.8420, 24.0310);
    private final double[] office = track.place(49.8350, 24.0750);
    private final double[] client = track.place(49.8505, 24.0205);
    private final double[] meeting = track.place(49.8200, 24.0100);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void aNoteWaitingForItsStayIsAttachedToTheStayMadeFromTheGap() {
        UUID userId = harness.createUser("gap-note");
        harness.ingest(userId, track
                .stay(home, at("06:00"), at("08:00"), 5)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(office, at("08:21"), at("10:00"), 10)
                .stay(client, at("14:00"), at("15:00"), 10)
                .messages());
        harness.fullRebuild(userId);
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).stream()
                        .filter(candidate -> candidate.getStartTime().equals(at("09:51")))
                        .findFirst()
                        .orElseThrow());

        Long noteId = createStayNoteAwaitingItsStay(userId, gap.getStartTime().plusSeconds(600),
                meeting[0], meeting[1]);

        DataGapStayOverrideRequest atTheMeeting = new DataGapStayOverrideRequest();
        atTheMeeting.setLocationStrategy("SELECTED_LOCATION");
        atTheMeeting.setLatitude(meeting[0]);
        atTheMeeting.setLongitude(meeting[1]);
        atTheMeeting.setLocationName("Meeting");
        DataGapStayOverrideResponseDTO conversion = QuarkusTransaction.requiringNew().call(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), atTheMeeting).orElseThrow());

        TimelineNoteEntity note = QuarkusTransaction.requiringNew().call(() ->
                entityManager.find(TimelineNoteEntity.class, noteId));
        assertThat(note.getAnchorType()).isEqualTo(NoteAnchorType.STAY);
        assertThat(note.getStay()).as("the note's stay").isNotNull();
        assertThat(note.getStay().getId()).as("the stay made from the gap").isEqualTo(conversion.stayId());
    }

    /** A stay anchor whose stay is not there (any more), as an imported or rewritten timeline can leave it. */
    private Long createStayNoteAwaitingItsStay(UUID userId, Instant eventTime, double latitude, double longitude) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TimelineNoteEntity note = new TimelineNoteEntity();
            note.setUser(entityManager.getReference(UserEntity.class, userId));
            note.setContentMarkdown("At the meeting");
            note.setEventTime(eventTime);
            note.setAnchorType(NoteAnchorType.STAY);
            note.setSourceItemStartTime(eventTime);
            note.setSourceItemDurationSeconds(3_600L);
            note.setSourceStartLatitude(latitude);
            note.setSourceStartLongitude(longitude);
            entityManager.persist(note);
            return note.getId();
        });
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-12T" + time + ":00Z");
    }
}
