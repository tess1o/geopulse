package org.github.tess1o.geopulse.streaming.rebuild;

import java.time.Instant;
import java.util.UUID;

/**
 * Home, a drive to the office with two short stops on the way (a bakery at 08:16–08:20 and a pharmacy at
 * 08:27–08:31, both too short to be stays), and the office from 08:46. The drive is one trip; tests split the stops
 * out by hand.
 */
final class CommuteWithStops {

    static final String DAY = "2026-09-15";

    final Track track = new Track();
    final double[] home = track.place(49.8420, 24.0310);
    final double[] bakery = track.place(49.8385, 24.0530);
    final double[] pharmacy = track.place(49.8366, 24.0640);
    final double[] office = track.place(49.8350, 24.0750);

    /** A new user with the whole day stored and a full rebuild done. */
    UUID ingest(TimelineReplayHarness harness, String prefix) {
        return ingest(harness, harness.createUser(prefix));
    }

    /** The whole day stored for the user, and a full rebuild done. */
    UUID ingest(TimelineReplayHarness harness, UUID userId) {
        harness.ingest(userId, track
                .stay(home, at("06:00"), at("08:00"), 5)
                .drive(home, bakery, at("08:01"), at("08:15"))
                .stay(bakery, at("08:16"), at("08:20"), 1)
                .drive(bakery, pharmacy, at("08:21"), at("08:26"))
                .stay(pharmacy, at("08:27"), at("08:31"), 1)
                .drive(pharmacy, office, at("08:32"), at("08:45"))
                .stay(office, at("08:46"), at("12:00"), 10)
                .messages());
        harness.fullRebuild(userId);
        return userId;
    }

    static Instant at(String time) {
        return Instant.parse(DAY + "T" + time + ":00Z");
    }
}
