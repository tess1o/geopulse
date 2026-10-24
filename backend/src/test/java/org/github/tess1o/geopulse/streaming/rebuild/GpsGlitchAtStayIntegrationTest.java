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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.uploadBatches;

/**
 * A phone at home reported two points with the same timestamp: one 71 m away with 42 m accuracy, the next one back at
 * home. The first ended the stay and the second ended the trip at once, leaving a trip of 0 seconds between two stays
 * at home. The merge pass joined the two stays, so the trip must go with them instead of staying inside the merged
 * stay.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsGlitchAtStayIntegrationTest {

    private static final Instant GLITCH = Instant.parse("2026-10-24T05:57:07Z");

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
    void glitchWithinAStayLeavesNoTripInsideTheMergedStay() {
        Track track = new Track();
        UUID userId = newUser("gps-glitch", track);

        harness.ingest(userId, homeWithGlitch(track));
        harness.fullRebuild(userId);

        TimelineReplayHarness.TimelineSnapshot timeline = harness.snapshot(userId);
        String described = String.join("\n  ", timeline.content());
        assertThat(timeline.stays()).as(described).singleElement().asString().contains("'Home'");
        assertThat(timeline.trips()).as(described).isEmpty();
    }

    @ParameterizedTest(name = "uploads every {0} minutes")
    @ValueSource(ints = {1, 5, 15})
    void liveUploadsMatchFullRebuild(int minutes) {
        Track track = new Track();
        UUID userId = newUser("gps-glitch-live-" + minutes, track);

        harness.replayLive(userId, uploadBatches(homeWithGlitch(track), minutes));

        harness.assertMatchesFullRebuild(userId, "uploads every " + minutes + " minutes");
    }

    /** A user with the reporting user's timeline settings and a Home favorite. */
    private UUID newUser(String prefix, Track track) {
        UUID userId = harness.createUser(prefix);
        userService.updateTimelinePreferences(userId, UpdateTimelinePreferencesRequest.builder()
                .tripDetectionAlgorithm("multiple")
                .staypointRadiusMeters(50)
                .staypointMinDurationMinutes(5)
                .isMergeEnabled(true)
                .mergeMaxDistanceMeters(600)
                .mergeMaxTimeGapMinutes(10)
                .tripArrivalMinPoints(2)
                .tripArrivalDetectionMinDurationSeconds(60)
                .tripSustainedStopMinDurationSeconds(70)
                .build());
        double[] home = track.place(49.54715989, 25.59589803);
        favoriteLocationService.addFavorite(userId, new AddPointToFavoritesDto("Home", home[0], home[1]));
        return userId;
    }

    /** The points recorded around the glitch, at home from 04:00 to 07:00, about every 2.5 minutes. */
    private static List<OwnTracksLocationMessage> homeWithGlitch(Track track) {
        List<OwnTracksLocationMessage> messages = new ArrayList<>();
        Instant time = Instant.parse("2026-10-24T04:00:05Z");
        for (; time.isBefore(Instant.parse("2026-10-24T05:52:00Z")); time = time.plusSeconds(150)) {
            messages.add(point(track, 49.547084, 25.595885, time, 5.0, 0.0));
        }
        messages.add(point(track, 49.547036, 25.595853, Instant.parse("2026-10-24T05:52:05Z"), 23.0, 0.0));
        messages.add(point(track, 49.547036, 25.595821, Instant.parse("2026-10-24T05:54:35Z"), 10.0, 9.0));
        messages.add(point(track, 49.547147, 25.596000, Instant.parse("2026-10-24T05:57:00Z"), 22.0, null));
        // The glitch: 71 m away, then back home, both at the same second
        messages.add(point(track, 49.546539, 25.595244, GLITCH, 42.0, null));
        messages.add(point(track, 49.547054, 25.595884, GLITCH, 17.0, null));
        for (time = Instant.parse("2026-10-24T05:59:35Z"); time.isBefore(Instant.parse("2026-10-24T07:00:00Z"));
             time = time.plusSeconds(150)) {
            messages.add(point(track, 49.547094, 25.595934, time, 8.0, null));
        }
        return messages;
    }

    private static OwnTracksLocationMessage point(Track track, double lat, double lon, Instant time, double accuracy,
                                                  Double speedKmh) {
        double[] place = track.place(lat, lon);
        OwnTracksLocationMessage message = Track.message(place[0], place[1], time, accuracy, 0.0);
        message.setVel(speedKmh);
        return message;
    }
}
