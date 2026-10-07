package org.github.tess1o.geopulse.importdata.model;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.time.Instant;
import java.util.List;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_DATE_RANGE;

@Data
public class ImportOptions {
    /**
     * Always overwritten by the server from the request's format field.
     */
    @Schema(hidden = true)
    private String importFormat = "geopulse";

    @Schema(description = "Data types to import from a GeoPulse export archive. Ignored by other formats. "
            + "All data types found in the archive are imported when omitted.",
            examples = "[\"rawgps\", \"favorites\"]")
    private List<String> dataTypes;

    @Schema(description = "Only import data at or after this time (ISO-8601 instant). Send together with endTime. "
            + "No filtering when both are omitted.", examples = "2026-01-01T00:00:00Z")
    private Instant startTime;

    @Schema(description = "Only import data at or before this time (ISO-8601 instant). Send together with startTime. "
            + "No filtering when both are omitted.", examples = "2026-01-31T23:59:59Z")
    private Instant endTime;

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

    public boolean hasTimeRange() {
        return startTime != null && endTime != null;
    }

    /**
     * Returns whether {@code timestamp} falls outside the requested time range; always false without a range.
     */
    public boolean isOutsideTimeRange(Instant timestamp) {
        return hasTimeRange() && timestamp != null
                && (timestamp.isBefore(startTime) || timestamp.isAfter(endTime));
    }

    /**
     * Rejects a half-open range or one that ends before it starts.
     */
    public void validateTimeRange() {
        if ((startTime == null) != (endTime == null)) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "startTime and endTime must be sent together");
        }
        if (hasTimeRange() && startTime.isAfter(endTime)) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "Start time must be before end time");
        }
    }
}
