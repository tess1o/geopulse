package org.github.tess1o.geopulse.statistics.service;

import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;

import java.time.Instant;
import java.util.UUID;

public interface StatisticsService {
    UserStatistics getStatistics(UUID userId, Instant from, Instant to, ChartGroupMode chartGroupMode);
}
