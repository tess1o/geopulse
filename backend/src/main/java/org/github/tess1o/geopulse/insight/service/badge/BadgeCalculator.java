package org.github.tess1o.geopulse.insight.service.badge;

import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

public interface BadgeCalculator {
    Badge calculateBadge(UUID userId);
}