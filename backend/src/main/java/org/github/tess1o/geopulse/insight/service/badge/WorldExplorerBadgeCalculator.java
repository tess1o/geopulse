package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class WorldExplorerBadgeCalculator implements BadgeCalculator {

    private static final int CITIES_THRESHOLD = 20;
    private static final String TITLE = "World Explorer";

    private final CitiesBadgeCalculator citiesBadgeCalculator;

    public WorldExplorerBadgeCalculator(CitiesBadgeCalculator citiesBadgeCalculator) {
        this.citiesBadgeCalculator = citiesBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "city_world_explorer";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return citiesBadgeCalculator.calculateCitiesBadge(userId, getBadgeId(), TITLE, "\uD83E\uDDF3️", CITIES_THRESHOLD);
    }
}

