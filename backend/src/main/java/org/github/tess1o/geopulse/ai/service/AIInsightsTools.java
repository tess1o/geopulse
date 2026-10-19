package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.AIPeriodDigestDTO;
import org.github.tess1o.geopulse.ai.model.AIStatisticsDTO;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.digest.service.DigestService;
import org.github.tess1o.geopulse.insight.model.JourneyInsights;
import org.github.tess1o.geopulse.insight.service.JourneyInsightService;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.service.StatisticsService;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.github.tess1o.geopulse.ai.service.AIToolArguments.endOfDay;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.invalid;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.requireDate;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.requireOrdered;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.startOfDay;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.zone;

/**
 * AI tools for summaries: monthly and yearly digests, range statistics, lifetime insights and coverage.
 */
@Slf4j
@ApplicationScoped
public class AIInsightsTools {

    private final CurrentUserService currentUserService;
    private final DigestService digestService;
    private final StatisticsService statisticsService;
    private final JourneyInsightService journeyInsightService;
    private final CoverageService coverageService;

    @Inject
    public AIInsightsTools(CurrentUserService currentUserService,
                           DigestService digestService,
                           StatisticsService statisticsService,
                           JourneyInsightService journeyInsightService,
                           CoverageService coverageService) {
        this.currentUserService = currentUserService;
        this.digestService = digestService;
        this.statisticsService = statisticsService;
        this.journeyInsightService = journeyInsightService;
        this.coverageService = coverageService;
    }

    public AIPeriodDigestDTO getPeriodDigest(Integer year, Integer month) {
        log.info("🔧 AI TOOL EXECUTED: getPeriodDigest({}, {})", year, month);
        if (year == null || year < 2000 || year > 2100) {
            throw invalid("year must be between 2000 and 2100", "year");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw invalid("month must be between 1 and 12", "month");
        }
        UserEntity user = currentUserService.getCurrentUser();
        return AIPeriodDigestDTO.from(month == null
                ? digestService.getYearlyDigest(user.getId(), year, user.getTimezone())
                : digestService.getMonthlyDigest(user.getId(), year, month, user.getTimezone()));
    }

    public AIStatisticsDTO getStatistics(String startDate, String endDate) {
        log.info("🔧 AI TOOL EXECUTED: getStatistics({}, {})", startDate, endDate);
        LocalDate start = requireDate(startDate, "startDate");
        LocalDate end = requireDate(endDate, "endDate");
        requireOrdered(start, end);
        UserEntity user = currentUserService.getCurrentUser();
        ZoneId zone = zone(user.getTimezone());
        // Charts are dropped from the result; the group mode only matters for them.
        return AIStatisticsDTO.from(statisticsService.getStatistics(
                user.getId(), startOfDay(start, zone), endOfDay(end, zone), ChartGroupMode.WEEKS));
    }

    public JourneyInsights getJourneyInsights() {
        log.info("🔧 AI TOOL EXECUTED: getJourneyInsights()");
        return journeyInsightService.getJourneyInsights(currentUserService.getCurrentUserId());
    }

    public CoverageSummary getCoverageSummary(Integer gridMeters) {
        log.info("🔧 AI TOOL EXECUTED: getCoverageSummary({})", gridMeters);
        UserEntity user = currentUserService.getCurrentUser();
        if (!user.isCoverageEnabled()) {
            throw new AIToolException("COVERAGE_DISABLED",
                    "Coverage is not enabled for this user. It can be turned on in GeoPulse's Coverage page.");
        }
        int grid = gridMeters == null ? CoverageDefaults.DEFAULT_GRID_METERS : gridMeters;
        if (!coverageService.isGridSupported(grid)) {
            throw new AIToolException(AIToolArguments.INVALID_ARGUMENT,
                    "Unsupported grid size. Use 20, 50, 250, 1000, 5000, 20000 or 40000.",
                    Map.of("argument", "gridMeters", "gridMeters", grid));
        }
        return coverageService.getCoverageSummary(user.getId(), grid);
    }
}
