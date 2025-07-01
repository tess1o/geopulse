package org.github.tess1o.geopulse.statistics;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Path("/api/statistics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("USER")
@Slf4j
public class StatisticsResource {

    private final StatisticsService statisticsService;
    private final CurrentUserService currentUserService;

    @Inject
    public StatisticsResource(StatisticsService statisticsService, CurrentUserService currentUserService) {
        this.statisticsService = statisticsService;
        this.currentUserService = currentUserService;
    }

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRangeStatistics(@QueryParam("startTime") String startTime,
                                       @QueryParam("endTime") String endTime) {
        log.info("Received request for range statistics: {} - {}", startTime, endTime);

        UUID userId = currentUserService.getCurrentUserId();
        Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
        Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
        ChartGroupMode chartGroupMode = Duration.between(start, end).toDays() < 10 ? ChartGroupMode.DAYS : ChartGroupMode.WEEKS;
        var statistics = statisticsService.getStatistics(userId, start, end, chartGroupMode);
        return Response.ok(statistics).build();
    }

    @GET
    @Path("/weekly")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWeeklyStatistics() {
        UUID userId = currentUserService.getCurrentUserId();
        Instant start = Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .plus(1, java.time.temporal.ChronoUnit.DAYS)
                .minusSeconds(1);
        log.info("Received request for weekly statistics: {} - {}", start, end);
        return Response.ok(statisticsService.getStatistics(userId, start, end, ChartGroupMode.DAYS)).build();
    }

    @GET
    @Path("/monthly")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMonthlyStatistics() {
        UUID userId = currentUserService.getCurrentUserId();
        Instant start = Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .plus(1, java.time.temporal.ChronoUnit.DAYS)
                .minusSeconds(1);
        log.info("Received request for monthly statistics: {} - {}", start, end);
        return Response.ok(statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS)).build();
    }
}
