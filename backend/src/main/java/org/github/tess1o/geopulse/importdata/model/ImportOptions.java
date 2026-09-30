package org.github.tess1o.geopulse.importdata.model;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.github.tess1o.geopulse.export.model.ExportDateRange;

import java.util.List;

@Data
public class ImportOptions {
    /**
     * Always overwritten by the server from the request's format field.
     */
    @Schema(hidden = true)
    private String importFormat = "geopulse";

    @Schema(description = "Data types to import from a GeoPulse export archive. Ignored by other formats. "
            + "All data types found in the archive are imported when omitted.")
    private List<String> dataTypes;

    @Schema(description = "Only import data with timestamps inside this range. No filtering when omitted.")
    private ExportDateRange dateRangeFilter;

    /**
     * When true, existing data in the calculated date range will be deleted before import.
     * The date range is calculated as the intersection of:
     * - User's selected date range filter (if any)
     * - The actual min/max dates found in the import file
     * This ensures only data where replacements exist will be deleted.
     */
    @Schema(description = "Delete existing data in the imported date range before importing.",
            defaultValue = "false")
    private boolean clearDataBeforeImport = false;

    /**
     * Full admin backup restore mode. When true, timeline rows from the export
     * are restored as a snapshot and raw GPS import must not trigger timeline
     * regeneration when timeline data is present.
     */
    @Schema(hidden = true)
    private boolean snapshotRestore = false;
}
