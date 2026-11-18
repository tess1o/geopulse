package org.github.tess1o.geopulse.statistics.service;

import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public interface StatisticsService {
    /**
     * @param zone the user's timezone; per-day and per-week buckets (charts, daily average, most active day)
     *             follow local calendar days in this zone
     */
    UserStatistics getStatistics(UUID userId, Instant from, Instant to, ChartGroupMode chartGroupMode, ZoneId zone);
}
