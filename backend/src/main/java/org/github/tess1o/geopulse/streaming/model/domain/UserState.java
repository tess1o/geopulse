package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import org.github.tess1o.geopulse.favorites.model.FavoriteAreaDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class UserState {

    private ProcessorMode currentMode = ProcessorMode.UNKNOWN;
    private final ArrayList<GPSPoint> activePoints = new ArrayList<>();
    @Getter(AccessLevel.NONE)
    private final List<GPSPoint> activePointsView = Collections.unmodifiableList(activePoints);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private double latitudeSum;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private double longitudeSum;
    private GPSPoint lastProcessedPoint;

    /** How the next event begins: with the very first point, or with the first point after a data gap. */
    private TimelineReason nextStartReason = TimelineReason.of(TimelineReasons.FIRST_POINT);

    /** Why the event being built from the active points began, and what was decided about it since. */
    private TimelineExplanation activeExplanation = new TimelineExplanation();

    /** The user's favorite areas: stay detection keeps points in the same area as the stay together. */
    private List<FavoriteAreaDto> favoriteAreas = List.of();

    /**
     * Begins explaining a new event.
     */
    public void startExplanation(TimelineReason start) {
        activeExplanation = new TimelineExplanation(start, null, null);
    }

    /**
     * The active event's explanation with its end, ready to attach to the finalized event. Explaining starts afresh.
     */
    public TimelineExplanation finishExplanation(TimelineReason end) {
        TimelineExplanation explanation = activeExplanation;
        explanation.setEnd(end);
        activeExplanation = new TimelineExplanation();
        return explanation;
    }

    public void addActivePoint(GPSPoint point) {
        this.activePoints.add(point);
        // Streamed GPS coordinates are immutable after insertion; preserve the original summation order.
        latitudeSum += point.getLatitude();
        longitudeSum += point.getLongitude();
    }

    public void clearActivePoints() {
        int previousSize = activePoints.size();
        this.activePoints.clear();
        if (previousSize > 10_000) activePoints.trimToSize();
        latitudeSum = 0;
        longitudeSum = 0;
    }

    /** Short-lived read-only view; use copyActivePoints when points must survive clearing this state. */
    public List<GPSPoint> getActivePoints() {
        return activePointsView;
    }

    public GPSPoint getFirstActivePoint() {
        return activePoints.isEmpty() ? null : activePoints.get(0);
    }

    public GPSPoint getLastActivePoint() {
        return activePoints.isEmpty() ? null : activePoints.get(activePoints.size() - 1);
    }

    public boolean hasActivePoints() {
        return !activePoints.isEmpty();
    }

    public GPSPoint calculateCentroid() {
        if (activePoints.isEmpty()) {
            return null;
        }
        // Apply consistent rounding to match GeoUtils.createPoint() behavior
        // 8 decimal places provides approximately 1.1 meter precision at the equator
        double avgLat = latitudeSum / activePoints.size();
        double avgLon = longitudeSum / activePoints.size();

        double latRounded = BigDecimal.valueOf(avgLat)
                .setScale(8, RoundingMode.HALF_UP)
                .doubleValue();
        double lonRounded = BigDecimal.valueOf(avgLon)
                .setScale(8, RoundingMode.HALF_UP)
                .doubleValue();

        return new GPSPoint(latRounded, lonRounded, 0, 0);
    }

    public List<GPSPoint> copyActivePoints() {
        return new ArrayList<>(activePoints);
    }

    /**
     * Starts over after a data gap.
     */
    public void reset() {
        currentMode = ProcessorMode.UNKNOWN;
        clearActivePoints();
        lastProcessedPoint = null;
        nextStartReason = TimelineReason.of(TimelineReasons.AFTER_DATA_GAP);
        activeExplanation = new TimelineExplanation();
    }
}
