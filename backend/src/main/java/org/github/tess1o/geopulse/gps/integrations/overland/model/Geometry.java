package org.github.tess1o.geopulse.gps.integrations.overland.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Geometry {
    private String type;
    private double[] coordinates; // [longitude, latitude]
}