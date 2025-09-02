package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class UserState {

    private ProcessorMode currentMode = ProcessorMode.UNKNOWN;
    private List<GPSPoint> activePoints = new ArrayList<>();
    private GPSPoint lastProcessedPoint;

    public void addActivePoint(GPSPoint point) {
        this.activePoints.add(point);
    }

    public void clearActivePoints() {
        this.activePoints.clear();
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
        double lat = 0;
        double lon = 0;
        for (GPSPoint p : activePoints) {
            lat += p.getLatitude();
            lon += p.getLongitude();
        }
        return new GPSPoint(lat / activePoints.size(), lon / activePoints.size(), 0, 0);
    }

    public List<GPSPoint> copyActivePoints() {
        return new ArrayList<>(activePoints);
    }

    public void reset() {
        currentMode = ProcessorMode.UNKNOWN;
        activePoints.clear();
        lastProcessedPoint = null;
    }
}
