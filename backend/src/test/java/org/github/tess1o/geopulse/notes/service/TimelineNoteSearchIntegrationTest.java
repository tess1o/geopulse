package org.github.tess1o.geopulse.notes.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.NoteSearchResponse;
import org.github.tess1o.geopulse.notes.model.NoteSource;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Note search over seeded GeoPulse notes (Memos is not configured, so only local notes are returned).
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class TimelineNoteSearchIntegrationTest {

    private static final double KYIV_LAT = 50.4501;
    private static final double KYIV_LON = 30.5234;
    private static final Instant ALL_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ALL_TO = Instant.parse("2026-12-31T23:59:59Z");

    @Inject
    TimelineNoteService noteService;

    @Inject
    TimelineSeeder seed;

    private UUID ownerId;
    private long stayId;

    /*
     * Owner's notes:
     *   "Breakfast" Mar 1 08:00, in Kyiv
     *   "Lunch"     Mar 1 12:00, ~300 m from Kyiv centre
     *   "Museum"    Mar 2 15:00, in Lviv
     *   "Thoughts"  Mar 3 10:00, no location, during a stay at Home in Kyiv
     *   "Old"       Feb 1 09:00, in Kyiv
     */
    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity owner = seed.user("notes-owner", "UTC");
        ownerId = owner.getId();
        seed.note(owner, "Breakfast", Instant.parse("2026-03-01T08:00:00Z"), KYIV_LAT, KYIV_LON);
        seed.note(owner, "Lunch", Instant.parse("2026-03-01T12:00:00Z"), KYIV_LAT + 0.0027, KYIV_LON);
        seed.note(owner, "Museum", Instant.parse("2026-03-02T15:00:00Z"), 49.8397, 24.0297);
        seed.note(owner, "Thoughts", Instant.parse("2026-03-03T10:00:00Z"), null, null);
        seed.note(owner, "Old", Instant.parse("2026-02-01T09:00:00Z"), KYIV_LAT, KYIV_LON);
        FavoritesEntity home = seed.favorite(owner, "Home", null, null, KYIV_LAT, KYIV_LON);
        stayId = seed.stay(owner, home, Instant.parse("2026-03-03T09:00:00Z"), 7200).getId();

        UserEntity other = seed.user("notes-other", "UTC");
        seed.note(other, "Not yours", Instant.parse("2026-03-01T09:00:00Z"), KYIV_LAT, KYIV_LON);
    }

    @Test
    void search_returnsTheUsersNotesInTheRangeOldestFirst() {
        NoteSearchResponse response = search(Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T23:59:59Z"),
                null, null, null, null);

        assertThat(response.getNotes()).extracting(NoteDto::getTitle)
                .containsExactly("Breakfast", "Lunch", "Museum", "Thoughts");
        assertThat(response.getNotes()).extracting(NoteDto::getSource).containsOnly(NoteSource.GEOPULSE);
        assertThat(response.getTotalCount()).isEqualTo(4);
        assertThat(response.getGeopulseCount()).isEqualTo(4);
        assertThat(response.getMemosCount()).isZero();
    }

    @Test
    void search_rangeIsInclusiveAtBothEnds() {
        NoteSearchResponse response = search(Instant.parse("2026-03-01T12:00:00Z"), Instant.parse("2026-03-02T15:00:00Z"),
                null, null, null, null);

        assertThat(response.getNotes()).extracting(NoteDto::getTitle).containsExactly("Lunch", "Museum");
    }

    @Test
    void noteWithoutLocation_takesTheLocationOfTheStayItFallsIn() {
        NoteDto thoughts = search(ALL_FROM, ALL_TO, null, null, null, null).getNotes().stream()
                .filter(note -> note.getTitle().equals("Thoughts"))
                .findFirst().orElseThrow();

        assertThat(thoughts.getLatitude()).isEqualTo(KYIV_LAT);
        assertThat(thoughts.getLongitude()).isEqualTo(KYIV_LON);
        assertThat(thoughts.getLocationSource()).isEqualTo(NoteLocationSource.DERIVED_STAY);
        assertThat(thoughts.getAnchorType()).isEqualTo(NoteAnchorType.STAY);
        assertThat(thoughts.getAnchorId()).isEqualTo(stayId);
    }

    @Test
    void search_radiusKeepsOnlyNearbyNotes() {
        NoteSearchResponse within100m = search(ALL_FROM, ALL_TO, KYIV_LAT, KYIV_LON, 100.0, null);
        NoteSearchResponse within1km = search(ALL_FROM, ALL_TO, KYIV_LAT, KYIV_LON, 1000.0, null);

        assertThat(within100m.getNotes()).extracting(NoteDto::getTitle)
                .containsExactly("Old", "Breakfast", "Thoughts");
        assertThat(within1km.getNotes()).extracting(NoteDto::getTitle)
                .containsExactly("Old", "Breakfast", "Lunch", "Thoughts");
    }

    @Test
    void search_limitKeepsTheOldest() {
        NoteSearchResponse response = search(ALL_FROM, ALL_TO, null, null, null, 2);

        assertThat(response.getNotes()).extracting(NoteDto::getTitle).containsExactly("Old", "Breakfast");
        assertThat(response.getTotalCount()).isEqualTo(2);
    }

    private NoteSearchResponse search(Instant from, Instant to, Double lat, Double lon, Double radius, Integer limit) {
        return noteService.searchNotes(ownerId, from, to, true, limit, lat, lon, radius).join();
    }
}
