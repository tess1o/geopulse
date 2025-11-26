package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        // Apply consistent rounding to match GeoUtils.createPoint() behavior
        // 8 decimal places provides approximately 1.1 meter precision at the equator
        double avgLat = lat / activePoints.size();
        double avgLon = lon / activePoints.size();

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

    public void reset() {
        currentMode = ProcessorMode.UNKNOWN;
        activePoints.clear();
        lastProcessedPoint = null;
    }
}
