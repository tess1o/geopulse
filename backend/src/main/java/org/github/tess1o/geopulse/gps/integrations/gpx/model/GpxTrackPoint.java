package org.github.tess1o.geopulse.gps.integrations.gpx.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Represents a track point (trkpt) in GPX format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GpxTrackPoint {
    
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
    
    @JacksonXmlProperty(localName = "speed")
    private Double speed; // m/s
    
    @JacksonXmlProperty(localName = "course")
    private Double course; // degrees
    
    /**
     * Check if this track point has valid coordinates
     */
    public boolean hasValidCoordinates() {
        return lat != null && lon != null && 
               lat >= -90.0 && lat <= 90.0 && 
               lon >= -180.0 && lon <= 180.0;
    }
    
    /**
     * Check if this track point has valid timestamp
     */
    public boolean hasValidTime() {
        return time != null;
    }
}