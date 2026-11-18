package org.github.tess1o.geopulse.trips.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditRepository;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionCommitResponseDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionPreviewDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionSegmentDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Reconstructs a planned trip's missing time as plan-scoped corrections. A reconstruction never creates GPS points:
 * each described stay or movement becomes a {@code PLACE_STAY} or {@code PLACE_TRIP} correction, validated against
 * the planned trip's dates and marked as a reconstruction so a later commit can replace it.
 */
@ApplicationScoped
public class TripReconstructionService {

    private final TripAccessService tripAccessService;
    private final ActivityCorrectionService corrections;
    private final TimelineEditRepository editRepository;
    private final AsyncTimelineGenerationService asyncTimelineGenerationService;

    public TripReconstructionService(TripAccessService tripAccessService,
                                     ActivityCorrectionService corrections,
                                     TimelineEditRepository editRepository,
                                     AsyncTimelineGenerationService asyncTimelineGenerationService) {
        this.tripAccessService = tripAccessService;
        this.corrections = corrections;
        this.editRepository = editRepository;
        this.asyncTimelineGenerationService = asyncTimelineGenerationService;
    }

    public TripReconstructionPreviewDto preview(UUID actorUserId, TripReconstructionRequestDto request) {
        PreparedReconstruction prepared = prepareReconstruction(actorUserId, request);
        var preview = corrections.preview(prepared.ownerUserId(), prepared.request());
        return TripReconstructionPreviewDto.builder()
                .events(preview.events())
                .startTime(prepared.startTime())
                .endTime(prepared.endTime())
                .warnings(preview.warnings())
                .token(preview.token())
                .build();
    }

    public TripReconstructionCommitResponseDto commit(UUID actorUserId, TripReconstructionRequestDto request) {
        PreparedReconstruction prepared = prepareReconstruction(actorUserId, request);
        ActivityCorrectionService.Saved saved = corrections.save(prepared.ownerUserId(), prepared.request());

        AsyncTimelineGenerationService.TimelineSchedulingResult scheduling =
                asyncTimelineGenerationService.scheduleTimelineRegenerationFromTimestamp(
                        prepared.ownerUserId(), saved.affectedFrom());
        return TripReconstructionCommitResponseDto.builder()
                .groupId(saved.groupId())
                .corrections(saved.edits().size())
                .regenerationStartTime(saved.affectedFrom())
                .jobId(scheduling.jobId() == null ? null : scheduling.jobId().toString())
                .regenerationWarning(scheduling.queued()
                        ? "A timeline regeneration is already running; this change is applied when it finishes." : null)
                .build();
    }

    /**
     * The group a recommit replaces: the active reconstruction corrections covering the requested range. When the
     * range is covered by several groups or by unrelated corrections, replacement stays explicit and the preview
     * reports the conflict instead of guessing.
     */
    private UUID replacementGroup(UUID ownerUserId, Instant start, Instant end, Long tripId) {
        List<TimelineEditEntity> overlapping = editRepository.findActiveOverlapping(ownerUserId, start, end).stream()
                .filter(edit -> Boolean.TRUE.equals(org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayloads.reconstruction(edit.getPayload())))
                .filter(edit -> tripId == null || Objects.equals(org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayloads.tripId(edit.getPayload()), tripId))
                .toList();
        var groups = overlapping.stream().map(TimelineEditEntity::getGroupId).filter(Objects::nonNull).distinct().toList();
        if (groups.size() != 1) {
            return null;
        }
        UUID group = groups.getFirst();
        return overlapping.stream().allMatch(edit -> group.equals(edit.getGroupId())) ? group : null;
    }

    private PreparedReconstruction prepareReconstruction(UUID actorUserId, TripReconstructionRequestDto request) {
        if (request == null || request.getSegments() == null || request.getSegments().isEmpty()) {
            throw new IllegalArgumentException("At least one segment is required");
        }

        Long tripId = request.getTripId();
        if (tripId != null && tripId <= 0) {
            tripId = null;
        }
        TripEntity trip = null;
        UUID ownerUserId;

        if (tripId != null) {
            TripAccessContext access = tripAccessService.requireOwnerAccess(actorUserId, tripId);
            trip = access.trip();
            ownerUserId = access.ownerUserId();
            if (trip.getStartTime() == null || trip.getEndTime() == null) {
                throw new IllegalArgumentException("Trip must have start and end time before reconstruction");
            }
        } else {
            ownerUserId = actorUserId;
        }

        List<NormalizedSegment> normalized = new ArrayList<>();
        int segmentIndex = 0;
        for (TripReconstructionSegmentDto segment : request.getSegments()) {
            normalized.add(normalizeSegment(segment, segmentIndex++));
        }
        normalized.sort(Comparator.comparing(NormalizedSegment::startTime));

        for (int i = 0; i < normalized.size(); i++) {
            NormalizedSegment current = normalized.get(i);
            if (trip != null) {
                if (trip.getStartTime() != null && current.startTime().isBefore(trip.getStartTime())) {
                    throw new IllegalArgumentException("Segment " + (current.index() + 1) + " starts before trip start time");
                }
                if (trip.getEndTime() != null && current.endTime().isAfter(trip.getEndTime())) {
                    throw new IllegalArgumentException("Segment " + (current.index() + 1) + " ends after trip end time");
                }
            }
            if (i == 0) {
                continue;
            }
            NormalizedSegment previous = normalized.get(i - 1);
            if (current.startTime().isBefore(previous.endTime())) {
                throw new IllegalArgumentException(
                        "Segments overlap between positions " + (previous.index() + 1) + " and " + (current.index() + 1)
                );
            }
        }

        Instant startTime = normalized.getFirst().startTime();
        Instant endTime = normalized.getLast().endTime();
        List<ActivityCorrectionService.Activity> activities = normalized.stream()
                .map(TripReconstructionService::toActivity).toList();
        UUID replacement = request.getReplacesGroupId() != null ? request.getReplacesGroupId()
                : QuarkusTransaction.requiringNew().call(() -> replacementGroup(ownerUserId, startTime, endTime, request.getTripId()));
        ActivityCorrectionService.Request correctionRequest = new ActivityCorrectionService.Request(tripId, activities, startTime, endTime, request.getPreviewToken(), replacement);
        return new PreparedReconstruction(ownerUserId, tripId, startTime, endTime, correctionRequest);
    }

    private static ActivityCorrectionService.Activity toActivity(NormalizedSegment segment) {
        ActivityCorrectionService.Activity activity = new ActivityCorrectionService.Activity();
        activity.setReconstruction(true);
        activity.setSegmentType(segment.type() == SegmentType.STAY ? "STAY" : "TRIP");
        activity.setStartTime(segment.startTime());
        activity.setEndTime(segment.endTime());
        if (segment.type() == SegmentType.STAY) {
            activity.setLatitude(segment.latitude());
            activity.setLongitude(segment.longitude());
            activity.setLocationName(segment.locationName());
            return activity;
        }
        List<LatLon> waypoints = segment.waypoints();
        activity.setMovementType(segment.movementType().name());
        activity.setStartPlace(new ActivityCorrectionService.Place(null, null,
                waypoints.getFirst().latitude(), waypoints.getFirst().longitude(), null));
        activity.setEndPlace(new ActivityCorrectionService.Place(null, null,
                waypoints.getLast().latitude(), waypoints.getLast().longitude(), null));
        activity.setWaypoints(waypoints.subList(1, waypoints.size() - 1).stream()
                .map(point -> new TripReconstructionWaypointDto(point.latitude(), point.longitude())).toList());
        return activity;
    }

    private NormalizedSegment normalizeSegment(TripReconstructionSegmentDto segment, int index) {
        if (segment == null) {
            throw new IllegalArgumentException("Segment " + (index + 1) + " is null");
        }
        if (segment.getStartTime() == null || segment.getEndTime() == null) {
            throw new IllegalArgumentException("Segment " + (index + 1) + " must include startTime and endTime");
        }
        if (!segment.getEndTime().isAfter(segment.getStartTime())) {
            throw new IllegalArgumentException("Segment " + (index + 1) + " endTime must be after startTime");
        }

        String rawType = segment.getSegmentType();
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("Segment " + (index + 1) + " segmentType is required");
        }
        SegmentType type;
        try {
            type = SegmentType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Segment " + (index + 1) + " has invalid segmentType. Allowed values: STAY, TRIP", ex);
        }

        if (type == SegmentType.STAY) {
            if (segment.getLatitude() == null || segment.getLongitude() == null) {
                throw new IllegalArgumentException("Stay segment " + (index + 1) + " requires latitude and longitude");
            }
            String locationName = segment.getLocationName() != null ? segment.getLocationName().trim() : "";
            return new NormalizedSegment(index, type, segment.getStartTime(), segment.getEndTime(),
                    segment.getLatitude(), segment.getLongitude(), locationName.isEmpty() ? null : locationName,
                    null, List.of());
        }

        List<TripReconstructionWaypointDto> inputWaypoints = segment.getWaypoints() != null
                ? segment.getWaypoints() : List.of();
        if (inputWaypoints.size() < 2) {
            throw new IllegalArgumentException("Trip segment " + (index + 1) + " requires at least 2 waypoints");
        }
        List<LatLon> waypoints = new ArrayList<>();
        for (TripReconstructionWaypointDto waypoint : inputWaypoints) {
            if (waypoint == null || waypoint.getLatitude() == null || waypoint.getLongitude() == null) {
                throw new IllegalArgumentException("Trip segment " + (index + 1) + " contains invalid waypoint");
            }
            waypoints.add(new LatLon(waypoint.getLatitude(), waypoint.getLongitude()));
        }
        return new NormalizedSegment(index, type, segment.getStartTime(), segment.getEndTime(),
                null, null, null, parseTripType(segment.getMovementType()), waypoints);
    }

    private TripType parseTripType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return TripType.CAR;
        }
        try {
            return TripType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid movementType. Allowed values: WALK, BICYCLE, RUNNING, CAR, TRAIN, FLIGHT, BOAT, UNKNOWN", ex);
        }
    }

    private enum SegmentType {
        STAY,
        TRIP
    }

    private record LatLon(double latitude, double longitude) {
    }

    private record NormalizedSegment(int index,
                                     SegmentType type,
                                     Instant startTime,
                                     Instant endTime,
                                     Double latitude,
                                     Double longitude,
                                     String locationName,
                                     TripType movementType,
                                     List<LatLon> waypoints) {
    }

    private record PreparedReconstruction(UUID ownerUserId,
                                          Long tripId,
                                          Instant startTime,
                                          Instant endTime,
                                          ActivityCorrectionService.Request request) {

    }
}
