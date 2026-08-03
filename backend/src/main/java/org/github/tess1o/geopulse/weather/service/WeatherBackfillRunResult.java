package org.github.tess1o.geopulse.weather.service;

public record WeatherBackfillRunResult(
        int chunksProcessed,
        int targetsCreated,
        int targetsAlreadyKnown,
        int targetsSkipped,
        long pendingUserRanges
) {
}
