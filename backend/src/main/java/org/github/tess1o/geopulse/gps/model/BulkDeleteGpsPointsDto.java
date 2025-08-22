package org.github.tess1o.geopulse.gps.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for bulk deletion of GPS points.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteGpsPointsDto {
    
    @NotEmpty(message = "GPS point IDs list cannot be empty")
    private List<Long> gpsPointIds;
}