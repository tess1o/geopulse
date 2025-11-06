package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class MetropolisConquerorBadgeCalculator implements BadgeCalculator {

    private static final int CITIES_THRESHOLD = 200;
    private static final String TITLE = "Metropolis Conqueror";

    private final CitiesBadgeCalculator citiesBadgeCalculator;

    public MetropolisConquerorBadgeCalculator(CitiesBadgeCalculator citiesBadgeCalculator) {
        this.citiesBadgeCalculator = citiesBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "cites_visited_200";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return citiesBadgeCalculator.calculateCitiesBadge(userId, getBadgeId(), TITLE, "🌃", CITIES_THRESHOLD);
    }
}
