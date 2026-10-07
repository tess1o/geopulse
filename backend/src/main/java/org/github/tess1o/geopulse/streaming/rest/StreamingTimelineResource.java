package org.github.tess1o.geopulse.streaming.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.JobResponse;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayConversionPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationLookupResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.MultiUserTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineCountResponse;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitResponse;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.streaming.service.MultiUserTimelineService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.streaming.service.TimelineLocationLookupService;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.DATA_GAP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.DATA_GAP_OVERRIDE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_LOCATION_LOOKUP;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_JOB_ID;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_JOB_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_JOB_ALREADY_ACTIVE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_JOB_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_SPLIT_OVERRIDE_NOT_FOUND;

@Path("/timeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = ApiTags.TIMELINE)
public class StreamingTimelineResource {

    @Inject StreamingTimelineAggregator streamingTimelineAggregator;
    @Inject CurrentUserService currentUserService;
    @Inject TimelineConfigurationProvider configurationProvider;
    @Inject TimelineJobProgressService jobProgressService;
    @Inject AsyncTimelineGenerationService asyncTimelineGenerationService;
    @Inject org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProperties timelineConfigurationProperties;
    @Inject DataGapStayOverrideService dataGapStayOverrideService;
    @Inject StreamingTimelineGenerationService timelineGenerationService;
    @Inject MultiUserTimelineService multiUserTimelineService;
    @Inject TimelineLocationLookupService timelineLocationLookupService;

    @GET
    @Operation(summary = "Get the timeline",
            description = "Returns the stays, trips, and data gaps that overlap a time range, in chronological "
                    + "order. Items that start before `from` or end after `to` are included. Large ranges can "
                    + "return many items; check the size first with `GET /api/v1/timeline/count`.")
    public MovementTimelineDTO getTimeline(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant. Defaults to the earliest data.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the time range, as an ISO-8601 instant. Defaults to now.",
                    example = "2025-06-07T23:59:59Z")
            @QueryParam("to") String endTime) {
        TimeRange range = parseTimeRange(startTime, endTime);
        return streamingTimelineAggregator.getTimelineFromDb(
                currentUserService.getCurrentUserId(), range.start(), range.end());
    }

    @GET
    @Path("/location-lookup")
    @Operation(summary = "Look up visits near a point",
            description = "Finds what you know about a map point: favorites that contain it, earlier stays matched "
                    + "to it, and the nearest stays, within a search radius.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public LocationLookupResponseDTO lookupLocation(
            @Parameter(description = "Latitude in decimal degrees.", example = "50.4501")
            @QueryParam("latitude") Double latitude,
            @Parameter(description = "Longitude in decimal degrees.", example = "30.5234")
            @QueryParam("longitude") Double longitude) {
        if (latitude == null || longitude == null) {
            throw new GeoPulseException(INVALID_LOCATION_LOOKUP, "latitude and longitude are required");
        }
        try {
            return timelineLocationLookupService.lookup(
                    currentUserService.getCurrentUserId(), latitude, longitude);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_LOCATION_LOOKUP, "Invalid location",
                    Map.of("latitude", latitude, "longitude", longitude), e);
        }
    }

    @GET
    @Path("/count")
    @Operation(summary = "Count timeline items",
            description = "Returns the number of stays, trips, and data gaps in a time range, and the maximum number "
                    + "of items the web app shows at once. Use it to decide whether a range is small enough to load.")
    public TimelineCountResponse getTimelineCount(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant. Defaults to the earliest data.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the time range, as an ISO-8601 instant. Defaults to now.",
                    example = "2025-06-07T23:59:59Z")
            @QueryParam("to") String endTime) {
        TimeRange range = parseTimeRange(startTime, endTime);
        Map<String, Long> counts = streamingTimelineAggregator.getTimelineItemCounts(
                currentUserService.getCurrentUserId(), range.start(), range.end());
        return new TimelineCountResponse(
                counts.getOrDefault("stays", 0L),
                counts.getOrDefault("trips", 0L),
                counts.getOrDefault("dataGaps", 0L),
                counts.getOrDefault("totalItems", 0L),
                timelineConfigurationProperties.getViewItemLimit().longValue());
    }

    @GET
    @Path("/preferences")
    @Operation(summary = "Get effective timeline settings",
            description = "Returns the timeline detection settings in effect for the signed-in user: the user's own "
                    + "preferences merged with the server defaults. Change them with `PUT "
                    + "/api/v1/preferences/timeline`.")
    public TimelineConfig getUserPreferences() {
        return configurationProvider.getConfigurationForUser(currentUserService.getCurrentUserId());
    }

    @DELETE
    @Path("/stay-split-overrides/{overrideId}")
    @Operation(summary = "Undo a trip split",
            description = "Removes a manual split that turned part of a trip into a stay, and regenerates the "
                    + "affected timeline.")
    @Tag(name = ApiTags.TIMELINE_CORRECTIONS)
    public TripStaySplitResponse resetTripStaySplitOverride(
            @Parameter(description = "ID of the stay-split override.")
            @PathParam("overrideId") Long overrideId) {
        return timelineGenerationService
                .resetTripStaySplitOverride(currentUserService.getCurrentUserId(), overrideId)
                .orElseThrow(() -> new GeoPulseException(TRIP_SPLIT_OVERRIDE_NOT_FOUND,
                        "Trip split override not found or access denied", Map.of("overrideId", overrideId)));
    }

    @GET
    @Path("/data-gaps/{gapId}/stay-conversion-preview")
    @Operation(summary = "Preview converting a data gap to a stay",
            description = "Shows the stay that would replace a data gap if it were converted: the location (the last "
                    + "known point before the gap) and the time range.")
    @Tag(name = ApiTags.TIMELINE_CORRECTIONS)
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public DataGapStayConversionPreviewDTO getDataGapStayConversionPreview(
            @Parameter(description = "Data gap ID, as returned by the timeline.")
            @PathParam("gapId") Long gapId) {
        try {
            return dataGapStayOverrideService
                    .previewLatestPointConversion(currentUserService.getCurrentUserId(), gapId)
                    .orElseThrow(() -> new GeoPulseException(DATA_GAP_NOT_FOUND, "Data gap not found or access denied",
                            Map.of("gapId", gapId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid data gap conversion request", e);
        }
    }

    @PUT
    @Path("/data-gaps/{gapId}/stay-conversion")
    @Operation(summary = "Convert a data gap to a stay",
            description = "Replaces a data gap with a stay, for example when the phone stopped sending data while "
                    + "you stayed in one place. The conversion is saved as an override and kept when the timeline "
                    + "is regenerated.")
    @Tag(name = ApiTags.TIMELINE_CORRECTIONS)
    public DataGapStayOverrideResponseDTO convertDataGapToStay(
            @Parameter(description = "Data gap ID, as returned by the timeline.")
            @PathParam("gapId") Long gapId, DataGapStayOverrideRequest request) {
        try {
            return dataGapStayOverrideService
                    .convertGapToStay(currentUserService.getCurrentUserId(), gapId, request)
                    .orElseThrow(() -> new GeoPulseException(DATA_GAP_NOT_FOUND, "Data gap not found or access denied",
                            Map.of("gapId", gapId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid data gap conversion request", e);
        }
    }

    @DELETE
    @Path("/data-gap-overrides/{overrideId}/stay-conversion")
    @Operation(summary = "Undo a data gap conversion",
            description = "Removes a data-gap-to-stay override so the period is shown as a data gap again.")
    @Tag(name = ApiTags.TIMELINE_CORRECTIONS)
    public DataGapStayOverrideResponseDTO resetDataGapStayOverride(
            @Parameter(description = "ID of the data gap override.")
            @PathParam("overrideId") Long overrideId) {
        return timelineGenerationService
                .resetDataGapStayOverride(currentUserService.getCurrentUserId(), overrideId)
                .orElseThrow(() -> new GeoPulseException(DATA_GAP_OVERRIDE_NOT_FOUND,
                        "Data gap override not found or access denied", Map.of("overrideId", overrideId)));
    }

    @GET
    @Path("/multi-user")
    @Operation(summary = "Get timelines of several users",
            description = "Returns the timelines of the signed-in user and of friends who share their timeline, for "
                    + "the same time range, so they can be compared. Without `userIds`, includes the signed-in user "
                    + "and every friend who granted timeline access.")
    public MultiUserTimelineDTO getMultiUserTimeline(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant. Defaults to the earliest data.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the time range, as an ISO-8601 instant. Defaults to now.",
                    example = "2025-06-07T23:59:59Z")
            @QueryParam("to") String endTime,
            @Parameter(description = "Comma-separated user IDs to include. Each must be the signed-in user or a "
                    + "friend who granted timeline access.")
            @QueryParam("userIds") String userIds) {
        TimeRange range = parseTimeRange(startTime, endTime);
        return multiUserTimelineService.getMultiUserTimeline(
                currentUserService.getCurrentUserId(), range.start(), range.end(), parseUserIds(userIds));
    }

    @POST
    @Path("/jobs")
    @Operation(summary = "Regenerate the timeline",
            description = "Starts a background job that rebuilds the whole timeline of the signed-in user from GPS "
                    + "points, using the current timeline settings. Manual overrides are kept. Only one timeline "
                    + "job per user can run at a time. Poll `GET /api/v1/timeline/jobs/{jobId}` for progress.")
    public JobResponse regenerateAllTimeline() {
        try {
            return new JobResponse(asyncTimelineGenerationService
                    .regenerateTimelineAsync(currentUserService.getCurrentUserId()));
        } catch (IllegalStateException e) {
            throw new GeoPulseException(TIMELINE_JOB_ALREADY_ACTIVE, "A timeline job is already active", e);
        }
    }

    @GET
    @Path("/jobs/{jobId}")
    @Operation(summary = "Get timeline job progress",
            description = "Returns the progress of a timeline generation job: status, current step, and percentage.")
    public TimelineJobProgress getJobProgress(
            @Parameter(description = "Timeline job ID.")
            @PathParam("jobId") String jobId) {
        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_TIMELINE_JOB_ID, "Invalid job ID format", Map.of("jobId", jobId), e);
        }
        TimelineJobProgress progress = jobProgressService.getJobProgress(jobUuid)
                .orElseThrow(() -> new GeoPulseException(TIMELINE_JOB_NOT_FOUND, "Timeline job not found",
                        Map.of("jobId", jobId)));
        if (!progress.getUserId().equals(currentUserService.getCurrentUserId())) {
            throw new GeoPulseException(TIMELINE_JOB_ACCESS_DENIED, "Access denied");
        }
        return progress;
    }

    @GET
    @Path("/jobs/current")
    @Operation(summary = "Get the running timeline job",
            description = "Returns the timeline job that is currently running for the signed-in user, or `204 No "
                    + "Content` when none is running.")
    public RestResponse<TimelineJobProgress> getActiveJob() {
        return jobProgressService.getUserActiveJob(currentUserService.getCurrentUserId())
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    @GET
    @Path("/jobs/history")
    @Operation(summary = "List recent timeline jobs",
            description = "Returns recently finished timeline jobs of the signed-in user.")
    public List<TimelineJobProgress> getJobHistory() {
        return jobProgressService.getUserHistoryJobs(currentUserService.getCurrentUserId());
    }

    private static TimeRange parseTimeRange(String startTime, String endTime) {
        try {
            Instant start = startTime == null ? Instant.EPOCH : Instant.parse(startTime);
            Instant end = endTime == null ? Instant.now() : Instant.parse(endTime);
            if (start.isAfter(end)) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Start time must be before end time");
            }
            return new TimeRange(start, end);
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid time format. Use ISO-8601 format", e);
        }
    }

    private static List<UUID> parseUserIds(String userIds) {
        if (userIds == null || userIds.isBlank()) {
            return null;
        }
        try {
            return Arrays.stream(userIds.split(","))
                    .map(String::trim)
                    .map(UUID::fromString)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid user ID format", e);
        }
    }

    private record TimeRange(Instant start, Instant end) {
    }
}
