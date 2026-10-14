package org.github.tess1o.geopulse.gpssource.model;

/**
 * Why a request addressed to a known GPS source was refused. Persisted in the ingest status and translated by
 * the frontend, so existing values must not be renamed.
 */
public enum GpsSourceAuthFailureCode {
    INVALID_CREDENTIALS
}
