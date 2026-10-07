package org.github.tess1o.geopulse.gps.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * DTO for bulk deletion of GPS points.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteGpsPointsDto {
    
    @NotEmpty(message = "GPS point IDs list cannot be empty")
    @Schema(examples = "[101, 102, 103]")
    private List<Long> gpsPointIds;
}