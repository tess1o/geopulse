package org.github.tess1o.geopulse.timeline.detection.stays;

import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;

import java.util.List;

public interface StayPointDetector {
    List<TimelineStayPoint> detectStayPoints(TimelineConfig timelineConfig, List<TrackPoint> points);
}
