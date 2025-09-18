package org.github.tess1o.geopulse.insight.service.badge;

import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

public interface BadgeCalculator {
    Badge calculateBadge(UUID userId);
    
    /**
     * Get the badge ID for this calculator without performing calculations.
     * This should return the same ID that calculateBadge() would return.
     */
    String getBadgeId();
}