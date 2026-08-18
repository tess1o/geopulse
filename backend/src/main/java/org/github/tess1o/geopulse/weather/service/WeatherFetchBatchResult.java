package org.github.tess1o.geopulse.weather.service;

public record WeatherFetchBatchResult(
        boolean workClaimed,
        int processedTargets,
        String blockedReason
) {
    public static WeatherFetchBatchResult empty() {
        return new WeatherFetchBatchResult(false, 0, null);
    }

    public static WeatherFetchBatchResult blocked(String reason) {
        return new WeatherFetchBatchResult(false, 0, reason);
    }
}

