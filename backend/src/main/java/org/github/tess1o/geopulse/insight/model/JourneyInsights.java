package org.github.tess1o.geopulse.insight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JourneyInsights {
    private GeographicInsights geographic;
    private TimePatterns timePatterns;
    private Achievements achievements;
    private DistanceTraveled distanceTraveled;
}