package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for paginated GPS points response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPointPageDTO {
    private List<GpsPointDTO> data;
    private GpsPointPaginationDTO pagination;
}