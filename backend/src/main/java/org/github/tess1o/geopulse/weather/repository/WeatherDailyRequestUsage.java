package org.github.tess1o.geopulse.weather.repository;

import java.time.LocalDate;

public record WeatherDailyRequestUsage(
        LocalDate usageDate,
        long requestCount,
        long ongoingRequestCount,
        long backfillRequestCount
) {
    public static WeatherDailyRequestUsage empty(LocalDate date) {
        return new WeatherDailyRequestUsage(date, 0, 0, 0);
    }
}

