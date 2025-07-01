package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MostCommonRoute {
    private String name;
    private int count;
}
