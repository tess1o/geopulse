package org.github.tess1o.geopulse.gps.integrations.gpx.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Represents a waypoint (wpt) in GPX format
 */
@Data
public class GpxWaypoint {
    
    @JacksonXmlProperty(isAttribute = true)
    private Double lat;
    
    @JacksonXmlProperty(isAttribute = true)
    private Double lon;
    
    @JacksonXmlProperty(localName = "ele")
    private Double elevation;
    
    @JacksonXmlProperty(localName = "time")
    private Instant time;
    
    @JacksonXmlProperty(localName = "name")
    private String name;
    
    @JacksonXmlProperty(localName = "desc")
    private String description;
    
    @JacksonXmlProperty(localName = "sym")
    private String symbol;
    
    /**
     * Check if this waypoint has valid coordinates
     */
    public boolean hasValidCoordinates() {
        return lat != null && lon != null && 
               lat >= -90.0 && lat <= 90.0 && 
               lon >= -180.0 && lon <= 180.0;
    }
}