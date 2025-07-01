package org.github.tess1o.geopulse.importdata.model;

import lombok.Data;
import org.github.tess1o.geopulse.export.model.ExportDateRange;

import java.util.List;

@Data
public class ImportOptions {
    private String importFormat = "geopulse";
    private List<String> dataTypes;
    private ExportDateRange dateRangeFilter;
}