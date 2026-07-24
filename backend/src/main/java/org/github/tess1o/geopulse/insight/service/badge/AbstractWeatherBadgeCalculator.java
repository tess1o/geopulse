package org.github.tess1o.geopulse.insight.service.badge;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

abstract class AbstractWeatherBadgeCalculator implements BadgeCalculator {

    protected final WeatherBadgeQueryService queryService;

    protected AbstractWeatherBadgeCalculator(WeatherBadgeQueryService queryService) {
        this.queryService = queryService;
    }

    protected String earnedDate(Instant instant) {
        return instant == null
                ? null
                : instant.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE);
    }

    protected int clampProgress(long current, long target) {
        if (target <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.max(0, (current * 100) / target));
    }
}
