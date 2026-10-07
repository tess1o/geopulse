package org.github.tess1o.geopulse.export.model;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Data
public class CreateExportRequest {
    @Schema(description = "Export file format.", defaultValue = "geopulse", examples = "gpx")
    private ExportFormat format;

    @Schema(description = "Start of the range, inclusive (ISO-8601 instant).", examples = "2026-01-01T00:00:00Z",
            required = true)
    private Instant startTime;

    @Schema(description = "End of the range, inclusive (ISO-8601 instant).", examples = "2026-01-31T23:59:59Z",
            required = true)
    private Instant endTime;

    @Schema(description = "Data types to include in a geopulse export. Ignored by other formats, which always "
            + "export raw GPS points.", defaultValue = "[\"rawgps\"]",
            examples = "[\"rawgps\", \"timeline\", \"favorites\"]")
    private List<String> dataTypes;

    @Schema(description = "How a gpx export is packaged. Ignored by other formats.", defaultValue = "single",
            examples = "single")
    private GpxLayout gpxLayout;

    @Schema(description = "JSON shape of an owntracks export. Ignored by other formats.", defaultValue = "ocat",
            examples = "ocat")
    private OwnTracksLayout owntracksLayout;
}
