package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class CityCollectorBadgeCalculator implements BadgeCalculator {

    private static final int CITIES_THRESHOLD = 100;
    private static final String TITLE = "City Collector";

    private final CitiesBadgeCalculator citiesBadgeCalculator;

    public CityCollectorBadgeCalculator(CitiesBadgeCalculator citiesBadgeCalculator) {
        this.citiesBadgeCalculator = citiesBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "cites_visited_100";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return citiesBadgeCalculator.calculateCitiesBadge(userId, getBadgeId(), TITLE, "🌆", CITIES_THRESHOLD);
    }
}
