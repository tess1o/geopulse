package org.github.tess1o.geopulse.export.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateExportRequest {
    private List<String> dataTypes;
    private ExportDateRange dateRange;
    private String format = "json";
    private Map<String, Object> options;
}