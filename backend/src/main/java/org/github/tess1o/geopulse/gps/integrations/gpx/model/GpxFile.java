package org.github.tess1o.geopulse.gps.integrations.gpx.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

/**
 * Represents the root GPX element
 */
@Data
@JacksonXmlRootElement(localName = "gpx")
public class GpxFile {
    
    @JacksonXmlProperty(isAttribute = true)
    private String version;
    
    @JacksonXmlProperty(isAttribute = true)
    private String creator;
    
    @JacksonXmlProperty(localName = "metadata")
    private GpxMetadata metadata;
    
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "wpt")
    private List<GpxWaypoint> waypoints;
    
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "trk")
    private List<GpxTrack> tracks;
    
    /**
     * Get all track points from all tracks
     */
    public List<GpxTrackPoint> getAllTrackPoints() {
        if (tracks == null) {
            return List.of();
        }
        
        return tracks.stream()
                .flatMap(track -> track.getAllTrackPoints().stream())
                .toList();
    }
    
    /**
     * Get all valid waypoints with coordinates
     */
    public List<GpxWaypoint> getValidWaypoints() {
        if (waypoints == null) {
            return List.of();
        }
        
        return waypoints.stream()
                .filter(GpxWaypoint::hasValidCoordinates)
                .toList();
    }
}