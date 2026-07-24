package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@Singleton
public class RainTravelerBadgeCalculator extends AbstractWeatherBadgeCalculator {

    private static final int TARGET = 10;

    @Inject
    public RainTravelerBadgeCalculator(WeatherBadgeQueryService queryService) {
        super(queryService);
    }

    @Override
    public String getBadgeId() {
        return "weather_rain_traveler";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        long rainySamplesCount = queryService.countRainySamples(userId);
        boolean earned = rainySamplesCount >= TARGET;

        return Badge.builder()
                .id(getBadgeId())
                .icon("🌧️")
                .title("Rain Traveler")
                .description("Collect 10 rainy weather samples")
                .earned(earned)
                .earnedDate(earned ? earnedDate(queryService.nthRainySampleAt(userId, TARGET)) : null)
                .current((int) Math.min(rainySamplesCount, TARGET))
                .target(TARGET)
                .progress(clampProgress(rainySamplesCount, TARGET))
                .build();
    }
}
