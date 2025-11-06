package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.insight.service.CountriesBadgeCalculator;

import java.util.UUID;

@ApplicationScoped
public class WorldCitizenBadgeCalculator implements BadgeCalculator {

    private static final int COUNTRIES_THRESHOLD = 50;
    private final CountriesBadgeCalculator countriesBadgeCalculator;

    public WorldCitizenBadgeCalculator(CountriesBadgeCalculator countriesBadgeCalculator) {
        this.countriesBadgeCalculator = countriesBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "country_visited_50";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return countriesBadgeCalculator.calculateCountriesBadge(userId, getBadgeId(), "World Citizen", "🌏", COUNTRIES_THRESHOLD);
    }
}
