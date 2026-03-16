package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.AIMovementTimelineDTO;
import org.github.tess1o.geopulse.ai.model.AIStayStatsDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineFriendCandidateDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineStayDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineTripDTO;
import org.github.tess1o.geopulse.ai.model.AITripStatsDTO;
import org.github.tess1o.geopulse.ai.model.StayGroupBy;
import org.github.tess1o.geopulse.ai.model.TripGroupBy;
import org.github.tess1o.geopulse.statistics.model.RoutesStatistics;
import org.github.tess1o.geopulse.statistics.service.RoutesAnalysisService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * AI Tools class containing business logic for timeline queries.
 */
@Slf4j
@ApplicationScoped
public class AITimelineTools {

    private final StreamingTimelineAggregator streamingTimelineAggregator;
    private final RoutesAnalysisService routesAnalysisService;
    private final AITimelineTargetResolver targetResolver;

    @Inject
    public AITimelineTools(StreamingTimelineAggregator streamingTimelineAggregator,
                           RoutesAnalysisService routesAnalysisService,
                           AITimelineTargetResolver targetResolver) {
        this.streamingTimelineAggregator = streamingTimelineAggregator;
        this.routesAnalysisService = routesAnalysisService;
        this.targetResolver = targetResolver;
    }

    public AIMovementTimelineDTO queryTimeline(String startDate, String endDate) {
        return queryTimeline(startDate, endDate, null, null);
    }

    public AIMovementTimelineDTO queryTimeline(String startDate, String endDate, String targetScope, String targetUser) {
        log.info("🔧 AI TOOL EXECUTED: queryTimeline({}, {}, {}, {})", startDate, endDate, targetScope, targetUser);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        AIMovementTimelineDTO timeline = getTimeline(start, end, targetScope, targetUser);
        log.debug("Returned AI timeline with {} stays and {} trips", timeline.getStaysCount(), timeline.getTripsCount());
        return timeline;
    }

    public List<AITimelineStayDTO> getVisitedLocations(String startDate, String endDate) {
        return getVisitedLocations(startDate, endDate, null, null);
    }

    public List<AITimelineStayDTO> getVisitedLocations(String startDate, String endDate, String targetScope, String targetUser) {
        log.info("🔧 AI TOOL EXECUTED: getVisitedLocations({}, {}, {}, {})", startDate, endDate, targetScope, targetUser);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        AIMovementTimelineDTO timeline = getTimeline(start, end, targetScope, targetUser);
        log.debug("Returned {} visited locations", timeline.getStaysCount());
        return timeline.getStays();
    }

    public List<AITimelineTripDTO> getTripMovements(String startDate, String endDate) {
        return getTripMovements(startDate, endDate, null, null);
    }

    public List<AITimelineTripDTO> getTripMovements(String startDate, String endDate, String targetScope, String targetUser) {
        log.info("🔧 AI TOOL EXECUTED: getTripMovements({}, {}, {}, {})", startDate, endDate, targetScope, targetUser);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        AIMovementTimelineDTO timeline = getTimeline(start, end, targetScope, targetUser);
        log.debug("Returned {} trip movements", timeline.getTripsCount());
        return timeline.getTrips();
    }

    public List<AIStayStatsDTO> getStayStats(String startDate, String endDate, StayGroupBy groupBy) {
        return getStayStats(startDate, endDate, groupBy, null, null);
    }

    public List<AIStayStatsDTO> getStayStats(String startDate, String endDate, StayGroupBy groupBy, String targetScope, String targetUser) {
        log.info("🔧 AI TOOL EXECUTED: getStayStats({}, {}, {}, {}, {})", startDate, endDate, groupBy, targetScope, targetUser);

        LocalDate startLocalDate = LocalDate.parse(startDate);
        LocalDate endLocalDate = LocalDate.parse(endDate);
        Instant start = startLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = resolveTimelineOwnerUserId(targetScope, targetUser);

        try {
            List<AIStayStatsDTO> stats = streamingTimelineAggregator.getStayStats(userId, start, end, groupBy);
            log.debug("Returned {} stay statistics groups", stats.size());
            return stats;
        } catch (AIToolException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Invalid groupBy parameter for stay stats: {}", groupBy, e);
            return List.of();
        } catch (Exception e) {
            log.error("Unable to query stay statistics", e);
            return List.of();
        }
    }

    public List<AITripStatsDTO> getTripStats(String startDate, String endDate, TripGroupBy groupBy) {
        return getTripStats(startDate, endDate, groupBy, null, null);
    }

    public List<AITripStatsDTO> getTripStats(String startDate, String endDate, TripGroupBy groupBy, String targetScope, String targetUser) {
        log.info("🔧 AI TOOL EXECUTED: getTripStats({}, {}, {}, {}, {})", startDate, endDate, groupBy, targetScope, targetUser);

        LocalDate startLocalDate = LocalDate.parse(startDate);
        LocalDate endLocalDate = LocalDate.parse(endDate);
        Instant start = startLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = resolveTimelineOwnerUserId(targetScope, targetUser);

        try {
            List<AITripStatsDTO> stats = streamingTimelineAggregator.getTripStats(userId, start, end, groupBy);
            log.debug("Returned {} trip statistics groups", stats.size());
            return stats;
        } catch (AIToolException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Invalid groupBy parameter for trip stats: {}", groupBy, e);
            return List.of();
        } catch (Exception e) {
            log.error("Unable to query trip statistics", e);
            return List.of();
        }
    }

    public RoutesStatistics getRoutePatterns(String startDate, String endDate) {
        return getRoutePatterns(startDate, endDate, null, null);
    }

    public RoutesStatistics getRoutePatterns(String startDate, String endDate, String targetScope, String targetUser) {
        log.info("🔧 AI TOOL EXECUTED: getRoutePatterns({}, {}, {}, {})", startDate, endDate, targetScope, targetUser);

        LocalDate startLocalDate = LocalDate.parse(startDate);
        LocalDate endLocalDate = LocalDate.parse(endDate);
        Instant start = startLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = resolveTimelineOwnerUserId(targetScope, targetUser);

        try {
            MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);
            RoutesStatistics routeStats = routesAnalysisService.getRoutesStatistics(timeline);
            String mostCommonRoute = routeStats.getMostCommonRoute() != null ? routeStats.getMostCommonRoute().getName() : "N/A";
            log.debug("Returned route patterns: {} unique routes, most common: {}",
                    routeStats.getUniqueRoutesCount(),
                    mostCommonRoute);

            return routeStats;
        } catch (AIToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unable to analyze route patterns", e);
            return RoutesStatistics.builder()
                    .uniqueRoutesCount(0)
                    .avgTripDurationSeconds(0.0)
                    .longestTripDurationSeconds(0.0)
                    .longestTripDistanceMeters(0.0)
                    .build();
        }
    }

    public List<AITimelineFriendCandidateDTO> listAccessibleTimelineFriends() {
        log.info("🔧 AI TOOL EXECUTED: listAccessibleTimelineFriends()");
        return targetResolver.listAccessibleTimelineFriends();
    }

    private AIMovementTimelineDTO getTimeline(LocalDate startDate, LocalDate endDate, String targetScope, String targetUser) {
        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = resolveTimelineOwnerUserId(targetScope, targetUser);

        try {
            return streamingTimelineAggregator.getTimelineForAI(userId, start, end);
        } catch (AIToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unable to query timeline", e);
            return new AIMovementTimelineDTO();
        }
    }

    private UUID resolveTimelineOwnerUserId(String targetScope, String targetUser) {
        return targetResolver.resolveTarget(targetScope, targetUser).timelineOwnerUserId();
    }
}
