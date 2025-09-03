package org.github.tess1o.geopulse.importdata.model;

import lombok.Data;
import org.github.tess1o.geopulse.export.model.ExportDateRange;

import java.util.List;

@Data
public class ImportOptions {
    private String importFormat = "geopulse";
    private List<String> dataTypes;
    private ExportDateRange dateRangeFilter;
    
    /**
     * When true, existing data in the calculated date range will be deleted before import.
     * The date range is calculated as the intersection of:
     * - User's selected date range filter (if any)
     * - The actual min/max dates found in the import file
     * This ensures only data where replacements exist will be deleted.
     */
    private boolean clearDataBeforeImport = false;
}