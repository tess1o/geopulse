package org.github.tess1o.geopulse.gps.integrations.overland.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OverlandLocations {
    private List<OverlandLocationMessage> locations;
}