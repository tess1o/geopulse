package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Merge rules: two different favourite places that happen to share a name are not one place, and stays split by a GPS
 * glitch are one stay even when the user turned the preference merge off (nothing was recorded between them).
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class StayMergingIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    UserService userService;

    @Inject
    FavoriteLocationService favoriteLocationService;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void twoFavoritesWithTheSameNameAreNotMerged() {
        Track track = new Track();
        UUID userId = newUser("merge-same-name", track, true);
        double[] first = track.place(49.5470, 25.5960);
        double[] second = track.place(49.5510, 25.6010);
        favoriteLocationService.addFavorite(userId, new AddPointToFavoritesDto("Home", first[0], first[1]));
        favoriteLocationService.addFavorite(userId, new AddPointToFavoritesDto("Home", second[0], second[1]));

        harness.ingest(userId, track
                .stay(first, at("06:00"), at("08:00"), 10)
                .drive(first, second, at("08:01"), at("08:05"))
                .stay(second, at("08:06"), at("10:00"), 10)
                .messages());
        harness.fullRebuild(userId);

        assertThat(harness.snapshot(userId).stays())
                .as("two favourite places that only share a name")
                .hasSize(2);
    }

    @Test
    void aRecordedTripIsNotMergedWhenTheMergePreferenceIsOff() {
        Track track = new Track();
        UUID userId = newUser("merge-integrity", track, false);
        double[] home = track.place(49.54715989, 25.59589803);
        favoriteLocationService.addFavorite(userId, new AddPointToFavoritesDto("Home", home[0], home[1]));

        harness.ingest(userId, homeWithGlitch(track));
        harness.fullRebuild(userId);

        // The glitch is recorded as an 83 m trip, so this is the user-preference merge (turned off), not the
        // nothing-recorded-between-the-stays integrity merge. Leaving the two stays apart is the documented intent.
        TimelineReplayHarness.TimelineSnapshot timeline = harness.snapshot(userId);
        String described = String.join("\n  ", timeline.content());
        assertThat(timeline.stays()).as(described).hasSize(2);
        assertThat(timeline.trips()).as(described).hasSize(1);
    }

    private UUID newUser(String prefix, Track track, boolean mergeEnabled) {
        UUID userId = harness.createUser(prefix);
        userService.updateTimelinePreferences(userId, UpdateTimelinePreferencesRequest.builder()
                .tripDetectionAlgorithm("multiple")
                .staypointRadiusMeters(50)
                .staypointMinDurationMinutes(5)
                .isMergeEnabled(mergeEnabled)
                .mergeMaxDistanceMeters(600)
                .mergeMaxTimeGapMinutes(10)
                .tripArrivalMinPoints(2)
                .tripArrivalDetectionMinDurationSeconds(60)
                .tripSustainedStopMinDurationSeconds(70)
                .build());
        return userId;
    }

    /** A stay with one point 71 m away at the same second as the next point back home. */
    private static List<OwnTracksLocationMessage> homeWithGlitch(Track track) {
        double[] home = track.place(49.54715989, 25.59589803);
        List<OwnTracksLocationMessage> messages = new ArrayList<>();
        Instant time = Instant.parse("2026-10-24T04:00:05Z");
        for (; time.isBefore(Instant.parse("2026-10-24T07:00:00Z")); time = time.plusSeconds(150)) {
            messages.add(Track.message(home[0], home[1], time, 8.0, 0.0));
        }
        double[] glitch = track.place(49.546539, 25.595244);
        messages.add(Track.message(glitch[0], glitch[1], Instant.parse("2026-10-24T05:57:07Z"), 42.0, 0.0));
        messages.add(Track.message(home[0], home[1], Instant.parse("2026-10-24T05:57:07Z"), 17.0, 0.0));
        return messages;
    }

    private static Instant at(String time) {
        return Instant.parse("2026-10-24T" + time + ":00Z");
    }
}
