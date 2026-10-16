package org.github.tess1o.geopulse.streaming.rebuild;

import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds small, exact OwnTracks recordings for scenario tests. Coordinates are moved into a scope of their own, so no
 * other test's cached place names or favorites apply.
 */
final class Track {

    private final TestCoordinates.Scope scope = TestCoordinates.newScope();
    private final List<OwnTracksLocationMessage> messages = new ArrayList<>();

    /** A place in this track's scope, given in the usual (lat, lon) order. */
    double[] place(double lat, double lon) {
        Point point = scope.point(lon, lat);
        return new double[]{point.getY(), point.getX()};
    }

    /** Points every {@code everyMinutes} at one place, from {@code from} to {@code to} inclusive. */
    Track stay(double[] place, Instant from, Instant to, int everyMinutes) {
        for (Instant time = from; !time.isAfter(to); time = time.plus(Duration.ofMinutes(everyMinutes))) {
            messages.add(message(place[0], place[1], time, 10.0, 0.0));
        }
        return this;
    }

    /** A drive from one place to another with a point every minute. */
    Track drive(double[] from, double[] to, Instant start, Instant end) {
        long seconds = Duration.between(start, end).toSeconds();
        for (long elapsed = 0; elapsed <= seconds; elapsed += 60) {
            double progress = (double) elapsed / seconds;
            messages.add(message(from[0] + (to[0] - from[0]) * progress, from[1] + (to[1] - from[1]) * progress,
                    start.plusSeconds(elapsed), 8.0, 45.0));
        }
        return this;
    }

    List<OwnTracksLocationMessage> messages() {
        return List.copyOf(messages);
    }

    static OwnTracksLocationMessage message(double lat, double lon, Instant time, double accuracy, double speedKmh) {
        return OwnTracksLocationMessage.builder()
                .type("location")
                .tid("track")
                .tst(time.getEpochSecond())
                .createdAt(time.getEpochSecond())
                .lat(lat)
                .lon(lon)
                .acc(accuracy)
                .vel(speedKmh)
                .alt(280.0)
                .batt(80.0)
                .build();
    }
}
