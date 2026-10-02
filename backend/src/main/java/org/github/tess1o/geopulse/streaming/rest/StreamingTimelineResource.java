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
@Tag(name = "User: Timeline", description = "Read timelines and manage timeline generation, jobs, and overrides.")
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
    public MovementTimelineDTO getTimeline(@QueryParam("from") String startTime,
                                           @QueryParam("to") String endTime) {
        TimeRange range = parseTimeRange(startTime, endTime);
        return streamingTimelineAggregator.getTimelineFromDb(
                currentUserService.getCurrentUserId(), range.start(), range.end());
    }

    @GET
    @Path("/location-lookup")
    public LocationLookupResponseDTO lookupLocation(@QueryParam("latitude") Double latitude,
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
    public TimelineCountResponse getTimelineCount(@QueryParam("from") String startTime,
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
    public TimelineConfig getUserPreferences() {
        return configurationProvider.getConfigurationForUser(currentUserService.getCurrentUserId());
    }

    @DELETE
    @Path("/stay-split-overrides/{overrideId}")
    public TripStaySplitResponse resetTripStaySplitOverride(@PathParam("overrideId") Long overrideId) {
        return timelineGenerationService
                .resetTripStaySplitOverride(currentUserService.getCurrentUserId(), overrideId)
                .orElseThrow(() -> new GeoPulseException(TRIP_SPLIT_OVERRIDE_NOT_FOUND,
                        "Trip split override not found or access denied", Map.of("overrideId", overrideId)));
    }

    @GET
    @Path("/data-gaps/{gapId}/stay-conversion-preview")
    public DataGapStayConversionPreviewDTO getDataGapStayConversionPreview(@PathParam("gapId") Long gapId) {
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
    public DataGapStayOverrideResponseDTO convertDataGapToStay(
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
    public DataGapStayOverrideResponseDTO resetDataGapStayOverride(@PathParam("overrideId") Long overrideId) {
        return timelineGenerationService
                .resetDataGapStayOverride(currentUserService.getCurrentUserId(), overrideId)
                .orElseThrow(() -> new GeoPulseException(DATA_GAP_OVERRIDE_NOT_FOUND,
                        "Data gap override not found or access denied", Map.of("overrideId", overrideId)));
    }

    @GET
    @Path("/multi-user")
    public MultiUserTimelineDTO getMultiUserTimeline(
            @QueryParam("from") String startTime,
            @QueryParam("to") String endTime,
            @QueryParam("userIds") String userIds) {
        TimeRange range = parseTimeRange(startTime, endTime);
        return multiUserTimelineService.getMultiUserTimeline(
                currentUserService.getCurrentUserId(), range.start(), range.end(), parseUserIds(userIds));
    }

    @POST
    @Path("/jobs")
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
    public TimelineJobProgress getJobProgress(@PathParam("jobId") String jobId) {
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
    public RestResponse<TimelineJobProgress> getActiveJob() {
        return jobProgressService.getUserActiveJob(currentUserService.getCurrentUserId())
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    @GET
    @Path("/jobs/history")
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
