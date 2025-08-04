package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class LightningFastBadgeCalculator implements BadgeCalculator{

    private static final int TARGET_SPEED = 200;
    public static final String TITLE = "Lightning Fast";

    private final SpeedBadgeCalculator speedBadgeCalculator;

    public LightningFastBadgeCalculator(SpeedBadgeCalculator speedBadgeCalculator) {
        this.speedBadgeCalculator = speedBadgeCalculator;
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return speedBadgeCalculator.calculateSpeedBadget(userId, TITLE, TARGET_SPEED);
    }
}
