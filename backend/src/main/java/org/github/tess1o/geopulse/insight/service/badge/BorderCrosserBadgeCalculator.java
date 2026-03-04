package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.insight.service.CountriesBadgeCalculator;

import java.util.UUID;

@ApplicationScoped
public class BorderCrosserBadgeCalculator implements BadgeCalculator {

    private static final int COUNTRIES_THRESHOLD = 2;
    private final CountriesBadgeCalculator countriesBadgeCalculator;

    public BorderCrosserBadgeCalculator(CountriesBadgeCalculator countriesBadgeCalculator) {
        this.countriesBadgeCalculator = countriesBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "country_visited_2";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return countriesBadgeCalculator.calculateCountriesBadge(userId, getBadgeId(), "Border Crosser", "🌐", COUNTRIES_THRESHOLD);
    }
}
