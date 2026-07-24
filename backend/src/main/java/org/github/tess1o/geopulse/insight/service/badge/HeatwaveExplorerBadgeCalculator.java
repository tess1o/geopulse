package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@Singleton
public class HeatwaveExplorerBadgeCalculator extends AbstractWeatherBadgeCalculator {

    private static final double THRESHOLD_C = 30.0;

    @Inject
    public HeatwaveExplorerBadgeCalculator(WeatherBadgeQueryService queryService) {
        super(queryService);
    }

    @Override
    public String getBadgeId() {
        return "weather_heatwave";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Double maxTemperature = queryService.maxTemperature(userId);
        boolean earned = maxTemperature != null && maxTemperature >= THRESHOLD_C;
        int current = maxTemperature == null ? 0 : (int) Math.round(maxTemperature);

        return Badge.builder()
                .id(getBadgeId())
                .icon("🔥")
                .title("Heatwave Explorer")
                .description("Record a weather sample at 30 C or warmer")
                .earned(earned)
                .earnedDate(earned ? earnedDate(queryService.firstSampleAtOrAboveTemperature(userId, THRESHOLD_C)) : null)
                .current(current)
                .target((int) THRESHOLD_C)
                .progress(earned ? 100 : clampProgress(Math.max(0, current), (int) THRESHOLD_C))
                .build();
    }
}
