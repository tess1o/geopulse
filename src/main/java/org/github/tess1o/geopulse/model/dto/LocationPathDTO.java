package org.github.tess1o.geopulse.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing a complete location path.
 * Contains a list of points and metadata about the path.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationPathDTO {
    private String userId;
    private List<LocationPathPointDTO> points;
    private int pointCount;
    
    /**
     * Constructor that automatically sets the point count.
     */
    public LocationPathDTO(String userId, List<LocationPathPointDTO> points) {
        this.userId = userId;
        this.points = points;
        this.pointCount = points != null ? points.size() : 0;
    }
}