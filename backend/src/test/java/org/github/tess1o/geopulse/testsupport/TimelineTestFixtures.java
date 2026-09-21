package org.github.tess1o.geopulse.testsupport;

import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds GPS point batches that the streaming timeline engine turns into stays and trips.
 *
 * <p>Timeline-derived endpoints (timeline, location analytics, statistics, coverage, places) return
 * empty bodies unless the engine has run, so tests must seed points and then call
 * {@link #regenerateTimeline}. Use {@link #newScope()} per test so coordinates never collide
 * between concurrently running tests.
 *
 * <p>Regeneration is the synchronous {@code regenerateFullTimeline}, not the async job — the
 * scheduled/async processor is disabled in tests.
 */
public final class TimelineTestFixtures {

    /** Default dwell interval for a stationary batch, in minutes. */
    public static final int DEFAULT_STATIONARY_INTERVAL_MINUTES = 10;

    private final TestCoordinates.Scope scope;

    private TimelineTestFixtures(TestCoordinates.Scope scope) {
        this.scope = scope;
    }

    /** A fixture builder bound to collision-free coordinates. */
    public static TimelineTestFixtures newScope() {
        return new TimelineTestFixtures(TestCoordinates.newScope());
    }

    /** A single point at the given coordinate, offset into this scope. */
    public GpsPointEntity point(UserEntity user, double lat, double lon, Instant timestamp) {
        return point(user, lat, lon, timestamp, 0.0, 5.0);
    }

    /** A single point with explicit velocity (m/s) and accuracy (m). */
    public GpsPointEntity point(UserEntity user, double lat, double lon, Instant timestamp,
                                double velocity, double accuracy) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(user);
        point.setTimestamp(timestamp);
        point.setCoordinates(point(lon, lat));
        point.setAccuracy(accuracy);
        point.setVelocity(velocity);
        return point;
    }

    /** Raw JTS point in this scope, in PostGIS (lon, lat) order. */
    public Point point(double lon, double lat) {
        return scope.point(lon, lat);
    }

    /**
     * A dwell: points every {@code intervalMinutes} from {@code start} to {@code end} inclusive at
     * one coordinate, with zero velocity. Use {@link #DEFAULT_STATIONARY_INTERVAL_MINUTES} unless
     * the scenario needs otherwise.
     */
    public List<GpsPointEntity> stationaryPoints(UserEntity user, double lat, double lon,
                                                 Instant start, Instant end, int intervalMinutes) {
        List<GpsPointEntity> points = new ArrayList<>();
        Instant current = start;
        while (!current.isAfter(end)) {
            points.add(point(user, lat, lon, current, 0.0, 5.0));
            current = current.plusSeconds(intervalMinutes * 60L);
        }
        return points;
    }

    /**
     * A trip: points every ~2 minutes interpolated between two coordinates at 10 m/s. At least two
     * points are always produced.
     */
    public List<GpsPointEntity> movingPoints(UserEntity user,
                                             double startLat, double startLon,
                                             double endLat, double endLon,
                                             Instant start, Instant end) {
        List<GpsPointEntity> points = new ArrayList<>();
        long totalMinutes = Duration.between(start, end).toMinutes();
        int numPoints = Math.max(2, (int) (totalMinutes / 2));
        for (int i = 0; i < numPoints; i++) {
            double progress = (double) i / (numPoints - 1);
            double lat = startLat + (endLat - startLat) * progress;
            double lon = startLon + (endLon - startLon) * progress;
            Instant timestamp = start.plusSeconds((long) (totalMinutes * 60 * progress));
            points.add(point(user, lat, lon, timestamp, 10.0, 8.0));
        }
        return points;
    }

    /** A classic commute shape: a dwell, a trip, and a second dwell. */
    public List<GpsPointEntity> homeToOfficeDay(UserEntity user,
                                                double homeLat, double homeLon,
                                                double officeLat, double officeLon,
                                                Instant dayStart) {
        List<GpsPointEntity> all = new ArrayList<>();
        all.addAll(stationaryPoints(user, homeLat, homeLon,
                dayStart, dayStart.plus(Duration.ofHours(2)), 5));
        all.addAll(movingPoints(user, homeLat, homeLon, officeLat, officeLon,
                dayStart.plus(Duration.ofHours(2)).plusSeconds(60), dayStart.plus(Duration.ofMinutes(150))));
        all.addAll(stationaryPoints(user, officeLat, officeLon,
                dayStart.plus(Duration.ofMinutes(151)), dayStart.plus(Duration.ofHours(7)), 10));
        return all;
    }

    /** Persists every point. Must be called inside a transaction. */
    public static void persist(GpsPointRepository repository, List<GpsPointEntity> points) {
        for (GpsPointEntity point : points) {
            repository.persist(point);
        }
    }

    /**
     * Runs the synchronous timeline generation and asserts it succeeded. Call inside a transaction
     * after {@link #persist}.
     */
    public static void regenerateTimeline(StreamingTimelineGenerationService service, UUID userId) {
        assertThat(service.regenerateFullTimeline(userId))
                .as("timeline regeneration should succeed for user %s", userId)
                .isTrue();
    }
}
