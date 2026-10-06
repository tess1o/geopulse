package org.github.tess1o.geopulse.export.model;

import lombok.Data;

import java.time.Instant;

/**
 * Request DTO for debug data export with privacy-preserving coordinate shifting.
 * This export is designed to help debug timeline generation issues while protecting user privacy.
 */
@Data
public class DebugExportRequest {
    /**
     * Start of the export range
     */
    private Instant startTime;

    /**
     * End of the export range
     */
    private Instant endTime;

    /**
     * Latitude shift in degrees (added to all GPS coordinates).
     * If not provided, a random shift will be generated.
     */
    private Double latitudeShift;

    /**
     * Longitude shift in degrees (added to all GPS coordinates).
     * If not provided, a random shift will be generated.
     */
    private Double longitudeShift;

    /**
     * Whether to include timeline data (stays, trips, data gaps) in the export.
     * Default: true
     */
    private boolean includeTimeline = true;

    /**
     * Whether to include timeline configuration in the export.
     * Default: true
     */
    private boolean includeConfiguration = true;
}
