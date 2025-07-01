package org.github.tess1o.geopulse.timeline.detection.trips;

import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TimelineTrip;

import java.util.List;

public interface TimelineTripsDetector {
    /**
     * Detect trips between stay points from GPS data.
     * 
     * @param config timeline configuration containing trip detection parameters
     * @param allPoints all GPS points for the time period
     * @param stayPoints detected stay points between which trips occur
     * @return detected trips with travel mode classification
     */
    List<TimelineTrip> detectTrips(TimelineConfig config, List<GpsPointPathPointDTO> allPoints, List<TimelineStayPoint> stayPoints);
}
