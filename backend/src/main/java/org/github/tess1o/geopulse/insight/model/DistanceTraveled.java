package org.github.tess1o.geopulse.insight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class DistanceTraveled {
    private int byCar;
    private int byWalk;

    public int getTotal() {
        return byCar + byWalk;
    }
}
