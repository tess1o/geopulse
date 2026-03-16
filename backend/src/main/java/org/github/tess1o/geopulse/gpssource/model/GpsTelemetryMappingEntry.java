package org.github.tess1o.geopulse.gpssource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsTelemetryMappingEntry {
    private String key;
    private String label;
    /**
     * Expected values: boolean, number, string.
     */
    private String type;
    private String unit;

    @Builder.Default
    private boolean enabled = true;

    private Integer order;

    private List<String> trueValues;
    private List<String> falseValues;

    @Builder.Default
    private boolean showInGpsData = true;

    @Builder.Default
    private boolean showInCurrentPopup = true;
}
