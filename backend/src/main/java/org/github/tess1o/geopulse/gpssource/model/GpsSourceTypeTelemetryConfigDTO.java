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
public class GpsSourceTypeTelemetryConfigDTO {
    private String sourceType;
    private boolean customized;
    private List<GpsTelemetryMappingEntry> mapping;
}
