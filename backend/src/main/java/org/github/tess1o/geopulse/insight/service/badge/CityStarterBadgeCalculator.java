package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class CityStarterBadgeCalculator implements BadgeCalculator {

    private static final int CITIES_THRESHOLD = 3;
    private static final String TITLE = "City Starter";

    private final CitiesBadgeCalculator citiesBadgeCalculator;

    public CityStarterBadgeCalculator(CitiesBadgeCalculator citiesBadgeCalculator) {
        this.citiesBadgeCalculator = citiesBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "city_starter";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return citiesBadgeCalculator.calculateCitiesBadge(userId, "city_starter", TITLE, "🏙️", CITIES_THRESHOLD);
    }
}
