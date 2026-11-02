package org.github.tess1o.geopulse.streaming.model.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Incremental environment runs for one append-only active segment. Holds no GPS-point references. */
public class WaterTransitionTracker {
    private final double minWaterDistance;
    private final double minLandDistance;
    private final double minWaterSeconds;
    private final double minLandSeconds;
    private int processed;
    private int start;
    private Boolean onWater;
    private double distance;
    private Run lastWater;
    private Run lastLand;

    public WaterTransitionTracker(double minWaterDistance, double minLandDistance,
                                  double minWaterSeconds, double minLandSeconds) {
        this.minWaterDistance = minWaterDistance;
        this.minLandDistance = minLandDistance;
        this.minWaterSeconds = minWaterSeconds;
        this.minLandSeconds = minLandSeconds;
    }

    public Transition findTransition(List<GPSPoint> points) {
        while (processed < points.size()) {
            Boolean environment = points.get(processed).getOnWater();
            if (processed == 0 || !Objects.equals(onWater, environment)) {
                finishRun(points);
                start = processed;
                onWater = environment;
                distance = 0;
            } else if (onWater != null) {
                distance += points.get(processed - 1).distanceTo(points.get(processed));
            }
            processed++;
        }
        if (processed < 4 || start == 0 || onWater == null || start == processed - 1) return null;
        Run previous = onWater ? lastLand : lastWater;
        if (previous == null) return null;
        double seconds = seconds(points, start, processed - 1);
        if (!eligible(onWater, distance, seconds)) return null;
        return new Transition(previous, new Run(onWater, distance, seconds, start, processed - 1));
    }

    private void finishRun(List<GPSPoint> points) {
        if (processed == 0 || onWater == null) return;
        int end = processed - 1;
        double seconds = seconds(points, start, end);
        if (seconds < (onWater ? minWaterSeconds : minLandSeconds)) return;
        // Preserve the old backward summation at thresholds; scan each completed run just once.
        double reverseDistance = 0;
        for (int i = end; i > start; i--) {
            reverseDistance += points.get(i - 1).distanceTo(points.get(i));
        }
        if (eligible(onWater, reverseDistance, seconds)) {
            Run run = new Run(onWater, reverseDistance, seconds, start, end);
            if (onWater) lastWater = run;
            else lastLand = run;
        }
    }

    private boolean eligible(boolean water, double meters, double seconds) {
        return meters >= (water ? minWaterDistance : minLandDistance)
                && seconds >= (water ? minWaterSeconds : minLandSeconds);
    }

    private static double seconds(List<GPSPoint> points, int from, int to) {
        return Duration.between(points.get(from).getTimestamp(), points.get(to).getTimestamp()).toSeconds();
    }

    public record Run(boolean onWater, double distanceMeters, double durationSeconds, int startIndex, int endIndex) {}
    public record Transition(Run previous, Run current) {}
}
