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
     * Latitude shift in degrees. Must be 0: a latitude shift changes east-west distances, so the exported timeline
     * could not be reproduced. Kept so the request shape stays the same.
     */
    private Double latitudeShift;

    /**
     * Longitude shift in degrees (added to all GPS coordinates, wrapped to [-180, 180]).
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

    /**
     * What the user thinks is wrong, in their own words. Optional.
     */
    private String issueDescription;

    /**
     * When the problem happened, so the report can open that moment. Optional.
     */
    private Instant issueTime;
}
