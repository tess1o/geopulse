package org.github.tess1o.geopulse.gpssource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsTelemetryMappingEntry implements Serializable {
    @Schema(examples = "batt")
    private String key;
    @Schema(examples = "Battery")
    private String label;
    /**
     * Expected values: boolean, number, string.
     */
    @Schema(examples = "number")
    private String type;
    @Schema(examples = "%")
    private String unit;

    @Builder.Default
    @Schema(examples = "true")
    private boolean enabled = true;

    @Schema(examples = "1")
    private Integer order;

    @Schema(examples = "[\"1\", \"true\"]")
    private List<String> trueValues;
    @Schema(examples = "[\"0\", \"false\"]")
    private List<String> falseValues;

    @Builder.Default
    @Schema(examples = "true")
    private boolean showInGpsData = true;

    @Builder.Default
    @Schema(examples = "true")
    private boolean showInCurrentPopup = true;
}
