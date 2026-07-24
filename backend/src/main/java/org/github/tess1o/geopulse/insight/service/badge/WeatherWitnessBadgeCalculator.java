package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@Singleton
public class WeatherWitnessBadgeCalculator extends AbstractWeatherBadgeCalculator {

    private static final int TARGET = 1;

    @Inject
    public WeatherWitnessBadgeCalculator(WeatherBadgeQueryService queryService) {
        super(queryService);
    }

    @Override
    public String getBadgeId() {
        return "weather_first_sample";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        long samplesCount = queryService.countSamples(userId);
        boolean earned = samplesCount >= TARGET;

        return Badge.builder()
                .id(getBadgeId())
                .icon("🌤️")
                .title("Weather Witness")
                .description("Collect your first weather sample")
                .earned(earned)
                .earnedDate(earned ? earnedDate(queryService.firstSampleAt(userId)) : null)
                .current((int) Math.min(samplesCount, TARGET))
                .target(TARGET)
                .progress(clampProgress(samplesCount, TARGET))
                .build();
    }
}
