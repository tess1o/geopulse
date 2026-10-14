package org.github.tess1o.geopulse.gps.model;

import org.github.tess1o.geopulse.gps.service.GpsPointService.GpsIngestSummary;

/**
 * Outcome counts of a batch ingest request: points received, stored, skipped as duplicates and skipped by
 * filters or validation.
 */
public record GpsIngestSummaryResponse(int accepted, int saved, int duplicate, int filtered) {

    public static GpsIngestSummaryResponse from(GpsIngestSummary summary) {
        return new GpsIngestSummaryResponse(summary.accepted(), summary.saved(), summary.duplicate(), summary.filtered());
    }
}
