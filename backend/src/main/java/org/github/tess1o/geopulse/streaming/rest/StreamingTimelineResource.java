package org.github.tess1o.geopulse.streaming.rest;

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
import org.github.tess1o.geopulse.streaming.model.dto.TripClassificationDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TripMovementTypeUpdateRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TripMovementTypeUpdateResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitResponse;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.streaming.service.MultiUserTimelineService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.streaming.service.TimelineLocationLookupService;
import org.github.tess1o.geopulse.streaming.service.TripClassificationDetailsService;
import org.github.tess1o.geopulse.streaming.service.TripMovementTypeOverrideService;
import org.github.tess1o.geopulse.streaming.service.TripStaySplitOverrideService;
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
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_SPLIT_OVERRIDE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/streaming-timeline")
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
    @Inject TripClassificationDetailsService tripClassificationDetailsService;
    @Inject TripMovementTypeOverrideService tripMovementTypeOverrideService;
    @Inject DataGapStayOverrideService dataGapStayOverrideService;
    @Inject TripStaySplitOverrideService tripStaySplitOverrideService;
    @Inject StreamingTimelineGenerationService timelineGenerationService;
    @Inject MultiUserTimelineService multiUserTimelineService;
    @Inject TimelineLocationLookupService timelineLocationLookupService;

    @GET
    public MovementTimelineDTO getTimeline(@QueryParam("startTime") String startTime,
                                           @QueryParam("endTime") String endTime) {
        TimeRange range = parseTimeRange(startTime, endTime);
        return streamingTimelineAggregator.getTimelineFromDb(
                currentUserService.getCurrentUserId(), range.start(), range.end());
    }

    @GET
    @Path("/location-lookup")
    public LocationLookupResponseDTO lookupLocation(@QueryParam("latitude") Double latitude,
                                                    @QueryParam("longitude") Double longitude) {
        if (latitude == null || longitude == null) {
            throw problem(INVALID_LOCATION_LOOKUP, "latitude and longitude are required");
        }
        try {
            return timelineLocationLookupService.lookup(
                    currentUserService.getCurrentUserId(), latitude, longitude);
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_LOCATION_LOOKUP, detail(e, "Invalid location"),
                    Map.of("latitude", latitude, "longitude", longitude));
        }
    }

    @GET
    @Path("/count")
    public TimelineCountResponse getTimelineCount(@QueryParam("startTime") String startTime,
                                                  @QueryParam("endTime") String endTime) {
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
    @Path("/user/preferences")
    public TimelineConfig getUserPreferences() {
        return configurationProvider.getConfigurationForUser(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/trips/{tripId}/classification")
    public TripClassificationDetailsDTO getTripClassificationDetails(@PathParam("tripId") Long tripId) {
        return tripClassificationDetailsService
                .getTripClassificationDetails(tripId, currentUserService.getCurrentUserId())
                .orElseThrow(() -> problem(TRIP_NOT_FOUND, "Trip not found or access denied",
                        Map.of("tripId", tripId)));
    }

    @PUT
    @Path("/trips/{tripId}/movement-type")
    public TripMovementTypeUpdateResponseDTO updateTripMovementType(
            @PathParam("tripId") Long tripId, TripMovementTypeUpdateRequest request) {
        if (request == null || request.getMovementType() == null || request.getMovementType().isBlank()) {
            throw problem(INVALID_TIMELINE_REQUEST, "movementType is required");
        }
        TripType movementType;
        try {
            movementType = TripType.valueOf(request.getMovementType().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_TIMELINE_REQUEST, "Invalid movementType",
                    Map.of("movementType", request.getMovementType(), "allowedValues",
                            String.join(",", Arrays.stream(TripType.values()).map(Enum::name).toList())));
        }
        return tripMovementTypeOverrideService
                .setManualMovementType(currentUserService.getCurrentUserId(), tripId, movementType)
                .orElseThrow(() -> problem(TRIP_NOT_FOUND, "Trip not found or access denied",
                        Map.of("tripId", tripId)));
    }

    @DELETE
    @Path("/trips/{tripId}/movement-type")
    public TripMovementTypeUpdateResponseDTO resetTripMovementType(@PathParam("tripId") Long tripId) {
        return tripMovementTypeOverrideService
                .resetToAutomaticMovementType(currentUserService.getCurrentUserId(), tripId)
                .orElseThrow(() -> problem(TRIP_NOT_FOUND, "Trip not found or access denied",
                        Map.of("tripId", tripId)));
    }

    @POST
    @Path("/trips/{tripId}/stay-split/preview")
    public TripStaySplitResponse previewTripStaySplit(
            @PathParam("tripId") Long tripId, TripStaySplitRequest request) {
        try {
            return tripStaySplitOverrideService
                    .previewSplit(currentUserService.getCurrentUserId(), tripId, request)
                    .orElseThrow(() -> problem(TRIP_NOT_FOUND, "Trip not found or access denied",
                            Map.of("tripId", tripId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw problem(INVALID_TIMELINE_REQUEST, detail(e, "Invalid trip split request"));
        }
    }

    @PUT
    @Path("/trips/{tripId}/stay-split")
    public TripStaySplitResponse splitTripWithStay(
            @PathParam("tripId") Long tripId, TripStaySplitRequest request) {
        try {
            return tripStaySplitOverrideService
                    .splitTrip(currentUserService.getCurrentUserId(), tripId, request)
                    .orElseThrow(() -> problem(TRIP_NOT_FOUND, "Trip not found or access denied",
                            Map.of("tripId", tripId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw problem(INVALID_TIMELINE_REQUEST, detail(e, "Invalid trip split request"));
        }
    }

    @DELETE
    @Path("/trip-stay-split-overrides/{overrideId}")
    public TripStaySplitResponse resetTripStaySplitOverride(@PathParam("overrideId") Long overrideId) {
        return timelineGenerationService
                .resetTripStaySplitOverride(currentUserService.getCurrentUserId(), overrideId)
                .orElseThrow(() -> problem(TRIP_SPLIT_OVERRIDE_NOT_FOUND,
                        "Trip split override not found or access denied", Map.of("overrideId", overrideId)));
    }

    @GET
    @Path("/data-gaps/{gapId}/stay-conversion-preview")
    public DataGapStayConversionPreviewDTO getDataGapStayConversionPreview(@PathParam("gapId") Long gapId) {
        try {
            return dataGapStayOverrideService
                    .previewLatestPointConversion(currentUserService.getCurrentUserId(), gapId)
                    .orElseThrow(() -> problem(DATA_GAP_NOT_FOUND, "Data gap not found or access denied",
                            Map.of("gapId", gapId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw problem(INVALID_TIMELINE_REQUEST, detail(e, "Invalid data gap conversion request"));
        }
    }

    @PUT
    @Path("/data-gaps/{gapId}/stay-conversion")
    public DataGapStayOverrideResponseDTO convertDataGapToStay(
            @PathParam("gapId") Long gapId, DataGapStayOverrideRequest request) {
        try {
            return dataGapStayOverrideService
                    .convertGapToStay(currentUserService.getCurrentUserId(), gapId, request)
                    .orElseThrow(() -> problem(DATA_GAP_NOT_FOUND, "Data gap not found or access denied",
                            Map.of("gapId", gapId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw problem(INVALID_TIMELINE_REQUEST, detail(e, "Invalid data gap conversion request"));
        }
    }

    @DELETE
    @Path("/data-gap-overrides/{overrideId}/stay-conversion")
    public DataGapStayOverrideResponseDTO resetDataGapStayOverride(@PathParam("overrideId") Long overrideId) {
        return timelineGenerationService
                .resetDataGapStayOverride(currentUserService.getCurrentUserId(), overrideId)
                .orElseThrow(() -> problem(DATA_GAP_OVERRIDE_NOT_FOUND,
                        "Data gap override not found or access denied", Map.of("overrideId", overrideId)));
    }

    @GET
    @Path("/multi-user")
    public MultiUserTimelineDTO getMultiUserTimeline(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("userIds") String userIds) {
        TimeRange range = parseTimeRange(startTime, endTime);
        try {
            return multiUserTimelineService.getMultiUserTimeline(
                    currentUserService.getCurrentUserId(), range.start(), range.end(), parseUserIds(userIds));
        } catch (jakarta.ws.rs.ForbiddenException e) {
            throw problem(ACCESS_DENIED, detail(e, "Access denied"));
        }
    }

    @POST
    @Path("/regenerate-all")
    public JobResponse regenerateAllTimeline() {
        try {
            return new JobResponse(asyncTimelineGenerationService
                    .regenerateTimelineAsync(currentUserService.getCurrentUserId()));
        } catch (IllegalStateException e) {
            throw problem(TIMELINE_JOB_ALREADY_ACTIVE, detail(e, "A timeline job is already active"));
        }
    }

    @GET
    @Path("/jobs/{jobId}")
    public TimelineJobProgress getJobProgress(@PathParam("jobId") String jobId) {
        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_TIMELINE_JOB_ID, "Invalid job ID format", Map.of("jobId", jobId));
        }
        TimelineJobProgress progress = jobProgressService.getJobProgress(jobUuid)
                .orElseThrow(() -> problem(TIMELINE_JOB_NOT_FOUND, "Timeline job not found",
                        Map.of("jobId", jobId)));
        if (!progress.getUserId().equals(currentUserService.getCurrentUserId())) {
            throw problem(TIMELINE_JOB_ACCESS_DENIED, "Access denied");
        }
        return progress;
    }

    @GET
    @Path("/jobs/active")
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
                throw problem(INVALID_TIMELINE_REQUEST, "Start time must be before end time");
            }
            return new TimeRange(start, end);
        } catch (DateTimeParseException e) {
            throw problem(INVALID_TIMELINE_REQUEST, "Invalid time format. Use ISO-8601 format");
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
            throw problem(INVALID_TIMELINE_REQUEST, "Invalid user ID format");
        }
    }

    private static String detail(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback
                : exception.getMessage();
    }

    private record TimeRange(Instant start, Instant end) {
    }
}
