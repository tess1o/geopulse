package org.github.tess1o.geopulse.insight;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.insight.model.*;

@RegisterForReflection(targets = {
        UserBadgeEntity.class,
        Achievements.class,
        Badge.class,
        City.class,
        Country.class,
        DistanceTraveled.class,
        GeographicInsights.class,
        JourneyInsights.class,
        TimePatterns.class,
})
public class InsightNativeConfig {
}
