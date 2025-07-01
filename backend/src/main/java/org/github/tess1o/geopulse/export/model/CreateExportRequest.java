package org.github.tess1o.geopulse.export.model;

import lombok.Data;

import java.util.List;

@Data
public class CreateExportRequest {
    private List<String> dataTypes;
    private ExportDateRange dateRange;
    private String format = "json";
}