package org.github.tess1o.geopulse.statistics.resource;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.statistics.service.StatisticsService;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_STATISTICS_RANGE;

@Path("/statistics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.STATISTICS)
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
    @Operation(summary = "Get statistics for a range",
            description = "Returns movement statistics for a time range: total distance, time moving, daily average "
                    + "distance, average speed, unique places, most active day, top places, frequent routes, and "
                    + "distance charts by movement type. Charts are grouped by day for ranges shorter than 10 days "
                    + "and by week otherwise. Days and weeks follow the user's timezone.")
    public UserStatistics getRangeStatistics(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant. Defaults to the earliest data.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the time range, as an ISO-8601 instant. Defaults to now.",
                    example = "2025-06-30T23:59:59Z")
            @QueryParam("to") String endTime) {
        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            if (start.isAfter(end)) {
                throw new GeoPulseException(INVALID_STATISTICS_RANGE, "Start time must be before end time");
            }
            ChartGroupMode groupMode = Duration.between(start, end).toDays() < 10
                    ? ChartGroupMode.DAYS
                    : ChartGroupMode.WEEKS;
            UserEntity user = currentUserService.getCurrentUser();
            return statisticsService.getStatistics(user.getId(), start, end, groupMode, userZone(user));
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_STATISTICS_RANGE, "Invalid time format. Use ISO-8601 format", e);
        }
    }

    @GET
    @Path("/weekly")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get statistics for the last 7 days",
            description = "Returns the same statistics as the range endpoint for the last 7 days up to the end of "
                    + "today in the user's timezone, with charts grouped by day.")
    public UserStatistics getWeeklyStatistics() {
        return getStatisticsForLastDays(7, ChartGroupMode.DAYS);
    }

    @GET
    @Path("/monthly")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get statistics for the last 30 days",
            description = "Returns the same statistics as the range endpoint for the last 30 days up to the end of "
                    + "today in the user's timezone, with charts grouped by week.")
    public UserStatistics getMonthlyStatistics() {
        return getStatisticsForLastDays(30, ChartGroupMode.WEEKS);
    }

    /** From local midnight {@code days} days ago through the end of today, in the user's timezone. */
    private UserStatistics getStatisticsForLastDays(int days, ChartGroupMode groupMode) {
        UserEntity user = currentUserService.getCurrentUser();
        ZoneId zone = userZone(user);
        LocalDate today = LocalDate.now(zone);
        Instant start = today.minusDays(days).atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1);
        return statisticsService.getStatistics(user.getId(), start, end, groupMode, zone);
    }

    private static ZoneId userZone(UserEntity user) {
        return TimestampUtils.resolveZone(user.getTimezone());
    }
}
