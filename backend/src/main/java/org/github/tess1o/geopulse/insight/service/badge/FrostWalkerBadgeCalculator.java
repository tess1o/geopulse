package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@Singleton
public class FrostWalkerBadgeCalculator extends AbstractWeatherBadgeCalculator {

    private static final double THRESHOLD_C = 0.0;
    private static final int TARGET = 1;

    @Inject
    public FrostWalkerBadgeCalculator(WeatherBadgeQueryService queryService) {
        super(queryService);
    }

    @Override
    public String getBadgeId() {
        return "weather_frost_walker";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Double minTemperature = queryService.minTemperature(userId);
        boolean earned = minTemperature != null && minTemperature <= THRESHOLD_C;

        return Badge.builder()
                .id(getBadgeId())
                .icon("❄️")
                .title("Frost Walker")
                .description("Record a weather sample at 0 C or colder")
                .earned(earned)
                .earnedDate(earned ? earnedDate(queryService.firstSampleAtOrBelowTemperature(userId, THRESHOLD_C)) : null)
                .current(earned ? TARGET : 0)
                .target(TARGET)
                .progress(earned ? 100 : 0)
                .build();
    }
}
