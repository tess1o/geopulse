package org.github.tess1o.geopulse.export.model;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

@Data
public class ExportDateRange {
    @Schema(description = "Start of the range, inclusive (ISO-8601 instant).", examples = "2026-01-01T00:00:00Z")
    private Instant startDate;

    @Schema(description = "End of the range, inclusive (ISO-8601 instant).", examples = "2026-01-31T23:59:59Z")
    private Instant endDate;
}