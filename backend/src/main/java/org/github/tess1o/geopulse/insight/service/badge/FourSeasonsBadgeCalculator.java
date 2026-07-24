package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@Singleton
public class FourSeasonsBadgeCalculator extends AbstractWeatherBadgeCalculator {

    private static final int TARGET = 4;

    @Inject
    public FourSeasonsBadgeCalculator(WeatherBadgeQueryService queryService) {
        super(queryService);
    }

    @Override
    public String getBadgeId() {
        return "weather_four_seasons";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        WeatherBadgeQueryService.WeatherSeasonCoverage coverage = queryService.seasonCoverage(userId);
        boolean earned = coverage.seasonsCount() >= TARGET;

        return Badge.builder()
                .id(getBadgeId())
                .icon("🍂")
                .title("Four Seasons")
                .description("Collect weather samples in all four seasons")
                .earned(earned)
                .earnedDate(earned ? earnedDate(coverage.completedAt()) : null)
                .current(Math.min(coverage.seasonsCount(), TARGET))
                .target(TARGET)
                .progress(clampProgress(coverage.seasonsCount(), TARGET))
                .build();
    }
}
