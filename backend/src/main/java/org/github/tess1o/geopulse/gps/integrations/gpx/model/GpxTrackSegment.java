package org.github.tess1o.geopulse.gps.integrations.gpx.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

/**
 * Represents a track segment (trkseg) in GPX format
 */
@Data
public class GpxTrackSegment {
    
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "trkpt")
    private List<GpxTrackPoint> trackPoints;
    
    /**
     * Get all valid track points with coordinates and time
     */
    public List<GpxTrackPoint> getValidTrackPoints() {
        if (trackPoints == null) {
            return List.of();
        }
        
        return trackPoints.stream()
                .filter(point -> point.hasValidCoordinates() && point.hasValidTime())
                .toList();
    }
}