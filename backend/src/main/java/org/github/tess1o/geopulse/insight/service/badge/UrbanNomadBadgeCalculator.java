package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class UrbanNomadBadgeCalculator implements BadgeCalculator {

    private static final int CITIES_THRESHOLD = 50;
    private static final String TITLE = "Urban Nomad";

    private final CitiesBadgeCalculator citiesBadgeCalculator;

    public UrbanNomadBadgeCalculator(CitiesBadgeCalculator citiesBadgeCalculator) {
        this.citiesBadgeCalculator = citiesBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "cites_visited_50";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return citiesBadgeCalculator.calculateCitiesBadge(userId, getBadgeId(), TITLE, "🏛️", CITIES_THRESHOLD);
    }
}
