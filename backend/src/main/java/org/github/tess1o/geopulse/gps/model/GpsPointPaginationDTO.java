package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for pagination information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPointPaginationDTO {
    private int page;
    private int limit;
    private long total;
    private long totalPages;
}