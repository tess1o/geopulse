package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.insight.service.CountriesBadgeCalculator;

import java.util.UUID;

@ApplicationScoped
public class PassportVeteranBadgeCalculator implements BadgeCalculator {

    private static final int COUNTRIES_THRESHOLD = 20;
    private final CountriesBadgeCalculator countriesBadgeCalculator;

    public PassportVeteranBadgeCalculator(CountriesBadgeCalculator countriesBadgeCalculator) {
        this.countriesBadgeCalculator = countriesBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "country_visited_20";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return countriesBadgeCalculator.calculateCountriesBadge(userId, getBadgeId(), "Passport Veteran", "📔", COUNTRIES_THRESHOLD);
    }
}
