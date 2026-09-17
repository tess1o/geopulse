package org.github.tess1o.geopulse.statistics.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.statistics.service.StatisticsService;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_STATISTICS_RANGE;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/statistics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Statistics", description = "Read movement statistics for range, week, and month views.")
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
    public UserStatistics getRangeStatistics(@QueryParam("startTime") String startTime,
                                       @QueryParam("endTime") String endTime) {
        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            if (start.isAfter(end)) {
                throw problem(INVALID_STATISTICS_RANGE, "Start time must be before end time");
            }
            ChartGroupMode groupMode = Duration.between(start, end).toDays() < 10
                    ? ChartGroupMode.DAYS
                    : ChartGroupMode.WEEKS;
            return statisticsService.getStatistics(currentUserService.getCurrentUserId(), start, end, groupMode);
        } catch (DateTimeParseException e) {
            throw problem(INVALID_STATISTICS_RANGE, "Invalid time format. Use ISO-8601 format");
        }
    }

    @GET
    @Path("/weekly")
    @Produces(MediaType.APPLICATION_JSON)
    public UserStatistics getWeeklyStatistics() {
        UUID userId = currentUserService.getCurrentUserId();
        Instant start = Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .plus(1, java.time.temporal.ChronoUnit.DAYS)
                .minusSeconds(1);
        return statisticsService.getStatistics(userId, start, end, ChartGroupMode.DAYS);
    }

    @GET
    @Path("/monthly")
    @Produces(MediaType.APPLICATION_JSON)
    public UserStatistics getMonthlyStatistics() {
        UUID userId = currentUserService.getCurrentUserId();
        Instant start = Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant end = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .plus(1, java.time.temporal.ChronoUnit.DAYS)
                .minusSeconds(1);
        return statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);
    }
}
