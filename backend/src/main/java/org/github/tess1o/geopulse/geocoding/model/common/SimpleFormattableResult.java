package org.github.tess1o.geopulse.geocoding.model.common;

import lombok.Builder;
import lombok.Data;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Simple implementation of FormattableGeocodingResult.
 * Used by adapters to create results with pre-formatted display names.
 */
@Data
@Builder
public class SimpleFormattableResult implements FormattableGeocodingResult {
    
    private Point requestCoordinates;
    private Point resultCoordinates;
    private Polygon boundingBox;
    private String formattedDisplayName;
    private String providerName;
    private String city;
    private String country;
}