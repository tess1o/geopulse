package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawGpsPointMapResponseDTO {
    private List<RawGpsPointMapPointDTO> points;
    private long totalCount;
    private int returnedCount;
    private int limit;
    private boolean limited;
}
