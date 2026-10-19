package org.github.tess1o.geopulse.ai.service;

import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.digest.model.TimeDigest;
import org.github.tess1o.geopulse.digest.service.DigestService;
import org.github.tess1o.geopulse.insight.service.JourneyInsightService;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.statistics.service.StatisticsService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class AIInsightsToolsTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private UserEntity user;
    private DigestService digestService;
    private StatisticsService statisticsService;
    private CoverageService coverageService;
    private AIInsightsTools tools;

    @BeforeEach
    void setUp() {
        CurrentUserService currentUserService = Mockito.mock(CurrentUserService.class);
        digestService = Mockito.mock(DigestService.class);
        statisticsService = Mockito.mock(StatisticsService.class);
        coverageService = Mockito.mock(CoverageService.class);
        tools = new AIInsightsTools(currentUserService, digestService, statisticsService,
                Mockito.mock(JourneyInsightService.class), coverageService);

        user = new UserEntity();
        user.setId(USER_ID);
        user.setTimezone("Europe/Kyiv");
        when(currentUserService.getCurrentUserId()).thenReturn(USER_ID);
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void getPeriodDigest_withoutMonthUsesYearlyDigest() {
        when(digestService.getYearlyDigest(USER_ID, 2025, "Europe/Kyiv")).thenReturn(new TimeDigest());

        tools.getPeriodDigest(2025, null);

        verify(digestService).getYearlyDigest(USER_ID, 2025, "Europe/Kyiv");
        verify(digestService, never()).getMonthlyDigest(USER_ID, 2025, 1, "Europe/Kyiv");
    }

    @Test
    void getPeriodDigest_rejectsInvalidMonth() {
        AIToolException error = assertThrows(AIToolException.class, () -> tools.getPeriodDigest(2025, 13));

        assertEquals("INVALID_ARGUMENT", error.getCode());
    }

    @Test
    void getStatistics_usesWholeDaysInUserTimezone() {
        when(statisticsService.getStatistics(Mockito.eq(USER_ID), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new UserStatistics());

        tools.getStatistics("2026-01-01", "2026-01-31");

        verify(statisticsService).getStatistics(USER_ID,
                Instant.parse("2025-12-31T22:00:00Z"),
                Instant.parse("2026-01-31T21:59:59.999Z"),
                ChartGroupMode.WEEKS);
    }

    @Test
    void getStatistics_rejectsReversedRange() {
        AIToolException error = assertThrows(AIToolException.class,
                () -> tools.getStatistics("2026-02-01", "2026-01-01"));

        assertEquals("INVALID_ARGUMENT", error.getCode());
    }

    @Test
    void getCoverageSummary_requiresCoverageEnabled() {
        user.setCoverageEnabled(false);

        AIToolException error = assertThrows(AIToolException.class, () -> tools.getCoverageSummary(null));

        assertEquals("COVERAGE_DISABLED", error.getCode());
        verify(coverageService, never()).getCoverageSummary(Mockito.any(), anyInt());
    }

    @Test
    void getCoverageSummary_defaultsGridSize() {
        user.setCoverageEnabled(true);
        when(coverageService.isGridSupported(CoverageDefaults.DEFAULT_GRID_METERS)).thenReturn(true);

        tools.getCoverageSummary(null);

        verify(coverageService).getCoverageSummary(USER_ID, CoverageDefaults.DEFAULT_GRID_METERS);
    }
}
