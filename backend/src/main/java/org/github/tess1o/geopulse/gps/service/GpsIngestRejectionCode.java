package org.github.tess1o.geopulse.gps.service;

/**
 * Why an incoming GPS point was not stored. Persisted in the per-source ingest status and
 * translated by the frontend, so existing values must not be renamed.
 */
public enum GpsIngestRejectionCode {
    ACCURACY_EXCEEDED,
    MISSING_ACCURACY,
    NEGATIVE_ACCURACY,
    SPEED_EXCEEDED,
    DUPLICATE,
    INVALID_PAYLOAD
}
