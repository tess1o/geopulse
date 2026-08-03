package org.github.tess1o.geopulse.weather.service;

/** Result of attempting to persist historical reconciliation work. */
public enum WeatherReconciliationQueueStatus {
    QUEUED,
    WEATHER_DISABLED,
    BACKFILL_DISABLED,
    INVALID_RANGE
}
