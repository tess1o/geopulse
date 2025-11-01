package org.github.tess1o.geopulse.importdata.service;

import java.time.Instant;

/**
 * Result of a streaming import operation.
 * Used by all GPS import strategies to report import statistics.
 */
public class StreamingImportResult {
    public final int imported;
    public final int skipped;
    public final int totalRecords;
    public final Instant firstTimestamp;
    public final Integer processedFiles;  // Optional, used only for ZIP imports

    /**
     * Constructor for single-file imports (GPX, GeoJSON, OwnTracks, Google Timeline)
     */
    public StreamingImportResult(int imported, int skipped, int totalRecords, Instant firstTimestamp) {
        this(imported, skipped, totalRecords, firstTimestamp, null);
    }

    /**
     * Constructor for multi-file imports (GPX ZIP)
     */
    public StreamingImportResult(int imported, int skipped, int totalRecords, Instant firstTimestamp, Integer processedFiles) {
        this.imported = imported;
        this.skipped = skipped;
        this.totalRecords = totalRecords;
        this.firstTimestamp = firstTimestamp;
        this.processedFiles = processedFiles;
    }
}
