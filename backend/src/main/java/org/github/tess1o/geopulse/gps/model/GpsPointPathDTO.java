package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing a complete GPS point path.
 * Contains a list of GPS points and metadata about the path.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPointPathDTO {
    private UUID userId;
    private List<? extends GpsPoint> points;
    private int pointCount;
    
    /**
     * Constructor that automatically sets the point count.
     */
    public GpsPointPathDTO(UUID userId, List<GpsPointPathPointDTO> points) {
        this.userId = userId;
        this.points = points;
    }

    public int getPointCount() {
        return points != null ? points.size() : 0;
    }
}