package org.github.tess1o.geopulse.streaming.model.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class WaterTransitionTrackerTest {
    @Test
    void matchesOriginalScansAtEveryPrefixIncludingUnknownsBlipsAndDuplicateTimestamps() {
        for (int seed = 0; seed < 8; seed++) {
            Random random = new Random(seed);
            List<GPSPoint> points = new ArrayList<>();
            WaterTransitionTracker tracker = new WaterTransitionTracker(300, 100, 120, 60);
            Instant at = Instant.EPOCH;
            Boolean environment = null;
            for (int i = 0; i < 2000; i++) {
                if (i % (seed + 17) == 0) environment = switch (random.nextInt(3)) {
                    case 0 -> null;
                    case 1 -> true;
                    default -> false;
                };
                at = at.plusMillis(random.nextInt(4) == 0 ? 0 : 10000 + random.nextInt(1000));
                GPSPoint point = new GPSPoint(50 + i * .0002, 30 + Math.sin(i) * .0001, 10, 5);
                point.setTimestamp(at);
                point.setOnWater(environment);
                points.add(point);
                var expected = original(points, 300, 100);
                assertThat(tracker.findTransition(points)).as("seed %s, prefix %s", seed, i).isEqualTo(expected);
                if (i % 7 == 0) assertThat(tracker.findTransition(points)).isEqualTo(expected);
            }
        }
    }

    @Test
    void preservesBackwardSummationWhenACompletedRunIsExactlyAtTheDistanceThreshold() {
        List<GPSPoint> points = new ArrayList<>();
        double[] edges = {0, 300, 1e-14, 1e-14, 1e-14, 1e-14, 0, 60, 60};
        WaterTransitionTracker tracker = new WaterTransitionTracker(Math.nextUp(300.0), 100, 120, 60);
        for (int i = 0; i < edges.length; i++) {
            points.add(new FixedDistancePoint(edges[i], i < 6, Instant.EPOCH.plusSeconds(i * 60L)));
            assertThat(tracker.findTransition(points)).isEqualTo(original(points, Math.nextUp(300.0), 100));
        }
        assertThat(tracker.findTransition(points)).isNotNull();
        assertThat(tracker.findTransition(points).previous().distanceMeters()).isGreaterThan(300);
    }

    @Test
    void readsOnlyALinearNumberOfPointsAcrossLongRuns() {
        CountingPoints points = new CountingPoints();
        WaterTransitionTracker tracker = new WaterTransitionTracker(300, 100, 120, 60);
        int count = 30000;
        for (int i = 0; i < count; i++) {
            GPSPoint point = new GPSPoint(50 + i * .00001, 30, 10, 5);
            point.setTimestamp(Instant.EPOCH.plusSeconds(i));
            point.setOnWater((i / 10000) % 2 == 0);
            points.add(point);
            tracker.findTransition(points);
        }
        assertThat(points.reads).isLessThan(12L * count);
    }

    // Reference implementation from ef16016d9: preserve both scan directions and exact endpoint rules.
    private WaterTransitionTracker.Transition original(List<GPSPoint> points, double waterDistance, double landDistance) {
        int end = points.size() - 1;
        if (points.size() < 4 || points.get(end).getOnWater() == null
                || !Objects.equals(points.get(end).getOnWater(), points.get(end - 1).getOnWater())) return null;
        boolean water = points.get(end).getOnWater();
        int start = end;
        while (start > 0 && same(points, start - 1, start, water)) start--;
        if (start == 0) return null;
        double distance = 0;
        for (int i = start; i < end; i++) distance += points.get(i).distanceTo(points.get(i + 1));
        var current = run(points, water, distance, start, end);
        if (!eligible(current, waterDistance, landDistance)) return null;
        int index = start - 1;
        while (index > 0) {
            if (!Objects.equals(points.get(index).getOnWater(), !water)) {
                index--;
                continue;
            }
            int from = index;
            double reverseDistance = 0;
            while (from > 0 && same(points, from - 1, from, !water)) {
                reverseDistance += points.get(from - 1).distanceTo(points.get(from));
                from--;
            }
            var previous = run(points, !water, reverseDistance, from, index);
            if (eligible(previous, waterDistance, landDistance)) return new WaterTransitionTracker.Transition(previous, current);
            index = Math.max(0, from - 1);
        }
        return null;
    }

    private boolean same(List<GPSPoint> points, int a, int b, boolean water) {
        return Objects.equals(points.get(a).getOnWater(), water) && Objects.equals(points.get(b).getOnWater(), water);
    }

    private WaterTransitionTracker.Run run(List<GPSPoint> points, boolean water, double distance, int start, int end) {
        return new WaterTransitionTracker.Run(water, distance,
                Duration.between(points.get(start).getTimestamp(), points.get(end).getTimestamp()).toSeconds(), start, end);
    }

    private boolean eligible(WaterTransitionTracker.Run run, double waterDistance, double landDistance) {
        return run.distanceMeters() >= (run.onWater() ? waterDistance : landDistance)
                && run.durationSeconds() >= (run.onWater() ? 120 : 60);
    }

    private static class CountingPoints extends ArrayList<GPSPoint> {
        long reads;
        @Override public GPSPoint get(int index) { reads++; return super.get(index); }
    }

    private static class FixedDistancePoint extends GPSPoint {
        private final double edge;
        FixedDistancePoint(double edge, boolean water, Instant at) {
            this.edge = edge;
            setOnWater(water);
            setTimestamp(at);
        }
        @Override public double distanceTo(GPSPoint other) { return ((FixedDistancePoint) other).edge; }
    }
}
