package org.github.tess1o.geopulse.coverage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageCell {
    private double latitude;
    private double longitude;
    private int gridMeters;
    private long seenCount;
}
