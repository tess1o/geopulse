package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.*;

import java.util.UUID;

@ApplicationScoped
public class JourneyInsightService {

    private final GeographicInsightService geographicInsightService;
    private final TimePatternService timePatternService;
    private final AchievementService achievementService;
    private final DistanceCalculationService distanceCalculationService;
    private final WeatherInsightService weatherInsightService;

    public JourneyInsightService(
            GeographicInsightService geographicInsightService,
            TimePatternService timePatternService,
            AchievementService achievementService,
            DistanceCalculationService distanceCalculationService,
            WeatherInsightService weatherInsightService) {
        this.geographicInsightService = geographicInsightService;
        this.timePatternService = timePatternService;
        this.achievementService = achievementService;
        this.distanceCalculationService = distanceCalculationService;
        this.weatherInsightService = weatherInsightService;
    }

    public JourneyInsights getJourneyInsights(UUID userId) {
        GeographicInsights geographic = geographicInsightService.calculateGeographicInsights(userId);
        TimePatterns timePatterns = timePatternService.calculateTimePatterns(userId);
        Achievements achievements = achievementService.calculateAchievements(userId);
        DistanceTraveled distanceTraveled = distanceCalculationService.calculateDistanceTraveled(userId);
        WeatherInsights weather = weatherInsightService.calculateWeatherInsights(userId);

        return new JourneyInsights(geographic, timePatterns, achievements, distanceTraveled, weather);
    }
}
