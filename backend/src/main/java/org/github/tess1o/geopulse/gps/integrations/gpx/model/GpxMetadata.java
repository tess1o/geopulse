package org.github.tess1o.geopulse.gps.integrations.gpx.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Represents GPX metadata
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GpxMetadata {
    
    @JacksonXmlProperty(localName = "name")
    private String name;
    
    @JacksonXmlProperty(localName = "desc")
    private String description;
    
    @JacksonXmlProperty(localName = "author")
    private String author;
    
    @JacksonXmlProperty(localName = "time")
    private Instant time;
}