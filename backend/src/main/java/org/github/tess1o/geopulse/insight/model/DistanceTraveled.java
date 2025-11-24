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
    private int byBicycle;
    private int byRunning;
    private int byTrain;
    private int byFlight;
    private int byUnknown;

    public int getTotal() {
        return byCar + byWalk + byBicycle + byRunning + byTrain + byFlight + byUnknown;
    }
}
