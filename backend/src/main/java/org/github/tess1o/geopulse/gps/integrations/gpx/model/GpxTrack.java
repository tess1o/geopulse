package org.github.tess1o.geopulse.gps.integrations.gpx.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

/**
 * Represents a track (trk) in GPX format
 */
@Data
public class GpxTrack {
    
    @JacksonXmlProperty(localName = "name")
    private String name;
    
    @JacksonXmlProperty(localName = "desc")
    private String description;
    
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "trkseg")
    private List<GpxTrackSegment> trackSegments;
    
    /**
     * Get all track points from all segments
     */
    public List<GpxTrackPoint> getAllTrackPoints() {
        if (trackSegments == null) {
            return List.of();
        }
        
        return trackSegments.stream()
                .flatMap(segment -> segment.getValidTrackPoints().stream())
                .toList();
    }
}