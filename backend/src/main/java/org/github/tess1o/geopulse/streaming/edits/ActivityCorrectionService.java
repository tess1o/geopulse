package org.github.tess1o.geopulse.streaming.edits;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.streaming.edits.payload.BeforeValues;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.PlaceStayPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.PlaceTripPayload;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditRepository;
import org.github.tess1o.geopulse.streaming.service.TimelineUserLock;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionSegmentDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.github.tess1o.geopulse.trips.service.TripAccessService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@ApplicationScoped
public class ActivityCorrectionService {
    public record Place(Long favoriteId, Long geocodingId, Double latitude, Double longitude, String locationName) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Activity extends TripReconstructionSegmentDto {
        @Valid
        private Place startPlace;
        @Valid
        private Place endPlace;
        @Size(max = 500)
        private String visitName;
        /** Marks a plan-scoped reconstruction correction, which a later recommit replaces as a group. */
        private boolean reconstruction;
    }

    /**
     * A batch of manual activities. {@code contextStart/contextEnd} only bound the preview and choose its default
     * endpoints; activities may be outside them because every fill carves its own interval. {@code previewToken} is
     * required by UI saves to reject a stale review; API clients may omit it.
     */
    public record Request(Long tripId, @NotEmpty @Size(max = 100) List<@Valid Activity> activities,
                          Instant contextStart, Instant contextEnd, String previewToken, UUID replacesGroupId) {
        public Request(Long tripId, List<Activity> activities, Instant contextStart, Instant contextEnd) {
            this(tripId, activities, contextStart, contextEnd, null, null);
        }
    }

    /**
     * A preview warning. {@code INFO} and {@code CONFIRM} describe what the fold will do; {@code INVALID} blocks save
     * until the user changes the request or supplies {@code replacesGroupId}.
     */
    public record Warning(String code, String severity, String message, String targetType,
                          Instant targetStart, Instant targetEnd, UUID groupId, Long correctionId,
                          List<String> actions) {
    }

    public record Preview(List<TimelineEditPreviewDTO.Event> events, Instant startTime, Instant endTime,
                          List<Warning> warnings, String token) {
        public Preview(List<TimelineEditPreviewDTO.Event> events, Instant startTime, Instant endTime) {
            this(events, startTime, endTime, List.of(), null);
        }
    }

    public record Saved(UUID groupId, List<TimelineEditDTO> edits, Instant affectedFrom) {
    }

    @Inject
    TimelineEditService edits;

    @Inject
    TimelineUserLock lock;

    @Inject
    TimelineEditRepository editRepository;

    @Inject
    EntityManager em;

    @Inject
    TripAccessService trips;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    LocationPointResolver locationPointResolver;

    @Transactional
    public Preview preview(UUID userId, Request request) {
        var commands = prepare(userId, request);
        if (request.replacesGroupId() != null) edits.validateReplacement(userId, request.replacesGroupId());
        Instant start = request.contextStart() != null ? request.contextStart() : commands.getFirst().getAnchorStart();
        Instant end = request.contextEnd() != null ? request.contextEnd() : commands.getLast().getAnchorEnd();
        String previewToken = token(userId, request, commands);
        var events = new ArrayList<TimelineEditPreviewDTO.Event>();
        for (var event : edits.previewCommands(userId, commands, start, end, request.replacesGroupId()).events()) {
            if (!overlaps(event, start, end)) continue;
            events.add(TimelineEditService.previewEvent(event));
        }
        events.sort(Comparator.comparing(TimelineEditPreviewDTO.Event::start));
        var complete = new ArrayList<TimelineEditPreviewDTO.Event>();
        Instant cursor = start;
        for (var event : events) {
            if (event.start().isAfter(cursor))
                complete.add(new TimelineEditPreviewDTO.Event("DATA_GAP", cursor, event.start(), "Unknown", null));
            complete.add(event);
            if (event.end().isAfter(cursor)) cursor = event.end();
        }
        if (end.isAfter(cursor))
            complete.add(new TimelineEditPreviewDTO.Event("DATA_GAP", cursor, end, "Unknown", null));
        if (!previewToken.equals(token(userId, request, commands))) throw new GeoPulseException(TIMELINE_PREVIEW_STALE, "The timeline changed while it was reviewed. Review the changes again.");
        return new Preview(complete, start, end, warnings(userId, request, commands), previewToken);
    }

    @Transactional
    public Saved save(UUID userId, Request request) {
        if (!lock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        var commands = prepare(userId, request);
        if (request.previewToken() != null && !request.previewToken().equals(token(userId, request, commands))) {
            throw new GeoPulseException(TIMELINE_PREVIEW_STALE,
                    "The timeline changed since it was reviewed. Review the changes again.");
        }
        var warnings = warnings(userId, request, commands);
        var invalid = warnings.stream().filter(warning -> "INVALID".equals(warning.severity())).findFirst();
        if (invalid.isPresent()) {
            var warning = invalid.get();
            throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, warning.message(),
                    parameters(warning.groupId(), warning.correctionId(), warning.actions()));
        }
        var replaceGroups = warnings.stream()
                .filter(warning -> "REPLACES_CORRECTION".equals(warning.code()))
                .map(Warning::groupId).filter(Objects::nonNull).distinct().toList();
        if (replaceGroups.size() > 1) {
            throw new GeoPulseException(TIMELINE_EDIT_CONFLICT,
                    "This change replaces more than one correction. Split it into separate changes.",
                    Map.of("conflictingGroupIds", replaceGroups));
        }
        if (!replaceGroups.isEmpty() && !replaceGroups.getFirst().equals(request.replacesGroupId())) {
            throw new GeoPulseException(TIMELINE_EDIT_CONFLICT,
                    "This activity replaces an earlier correction. Confirm the replacement first.",
                    Map.of("replacesGroupId", replaceGroups.getFirst(),
                            "actions", List.of("REPLACE_CORRECTION", "ADJUST_RANGE")));
        }
        Instant previewFrom = request.contextStart() != null ? request.contextStart() : commands.getFirst().getAnchorStart();
        Instant previewTo = request.contextEnd() != null ? request.contextEnd() : commands.getLast().getAnchorEnd();
        edits.previewCommands(userId, commands, previewFrom, previewTo, request.replacesGroupId());
        List<TimelineEditEntity> replacedGroup = List.of();
        if (request.replacesGroupId() != null) {
            replacedGroup = edits.supersedeGroup(userId, request.replacesGroupId());
        }
        UUID groupId = UUID.randomUUID();
        var result = new ArrayList<TimelineEditDTO>();
        for (var command : commands) {
            // The complete batch was folded above; re-detecting after each insert would scan the recording N times.
            command.setPending(true);
            var dto = edits.create(userId, command);
            var entity = em.find(TimelineEditEntity.class, dto.id());
            entity.setGroupId(groupId);
            result.add(TimelineEditService.toDto(entity));
        }
        if (!replacedGroup.isEmpty() && !result.isEmpty()) {
            edits.linkReplacement(replacedGroup, result.getFirst().id());
        }
        Instant affectedFrom = result.stream().map(TimelineEditDTO::affectedFrom)
                .min(Instant::compareTo).orElseThrow();
        return new Saved(groupId, result, affectedFrom);
    }

    @Transactional
    public Saved changeStatus(UUID userId, UUID groupId, boolean restore) {
        return changeStatus(userId, groupId, restore, false);
    }

    @Transactional
    public Saved changeStatus(UUID userId, UUID groupId, boolean restore, boolean cascade) {
        if (!lock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        var group = editRepository.findGroup(userId, groupId);
        if (group.isEmpty()) {
            throw new GeoPulseException(TIMELINE_EDIT_NOT_FOUND, "Correction not found or access denied");
        }
        if (restore) {
            for (var edit : group) {
                if (edit.getStatus() != TimelineEditStatus.REVERTED) {
                    throw invalid("Only a completely reverted correction can be restored");
                }
            }
        }
        var result = new ArrayList<TimelineEditDTO>();
        for (var edit : restore ? group : group.reversed()) {
            if (!restore && edit.getStatus() != TimelineEditStatus.ACTIVE) {
                throw invalid("Only an active correction can be undone");
            }
            result.add((restore ? edits.restore(userId, edit.getId()) : edits.revert(userId, edit.getId(), cascade)).orElseThrow());
        }
        if (!restore) {
            edits.reactivateReplacedBy(userId, result.stream().map(TimelineEditDTO::id).toList());
        } else {
            // Undoing the group cascaded through the detail corrections made on its events; restoring the group
            // restores that chain as well.
            var sequences = new HashSet<Long>();
            group.stream().map(TimelineEditEntity::getBeforeValues).filter(Objects::nonNull)
                    .map(BeforeValues::cascadeUndoneSequences)
                    .filter(Objects::nonNull).forEach(sequences::addAll);
            var dependents = editRepository.findByUser(userId).stream()
                    .filter(edit -> edit.getStatus() == TimelineEditStatus.REVERTED)
                    .filter(edit -> sequences.contains(edit.getSeq()))
                    .sorted(Comparator.comparingLong(TimelineEditEntity::getSeq))
                    .toList();
            for (var dependent : dependents) {
                result.add(edits.restore(userId, dependent.getId()).orElseThrow());
            }
        }
        return new Saved(groupId, result, result.stream().map(TimelineEditDTO::affectedFrom)
                .min(Instant::compareTo).orElseThrow());
    }

    private List<TimelineEditRequest> prepare(UUID userId, Request request) {
        if (request == null || request.activities() == null || request.activities().isEmpty() || request.activities().size() > 100) {
            throw invalid("Provide between 1 and 100 activities");
        }
        var segments = new ArrayList<>(request.activities());
        for (var segment : segments) {
            // A plan-scoped batch describes a planned trip and may lie in the future; recorded-time corrections may not.
            if (segment == null || segment.getStartTime() == null || segment.getEndTime() == null
                    || !segment.getEndTime().isAfter(segment.getStartTime())
                    || (request.tripId() == null && segment.getEndTime().isAfter(Instant.now()))) {
                throw invalid("Each activity needs a positive interval ending in the past");
            }
        }
        segments.sort(Comparator.comparing(TripReconstructionSegmentDto::getStartTime));
        if (request.tripId() != null) {
            var context = trips.requireOwnerAccess(userId, request.tripId());
            var trip = context.trip();
            if (trip.getStartTime() == null || trip.getEndTime() == null
                    || segments.getFirst().getStartTime().isBefore(trip.getStartTime())
                    || segments.getLast().getEndTime().isAfter(trip.getEndTime())) {
                throw invalid("Activities must be within the planned trip dates");
            }
        }
        var result = new ArrayList<TimelineEditRequest>();
        Instant previousEnd = null;
        for (var segment : segments) {
            if (previousEnd != null && segment.getStartTime().isBefore(previousEnd)) {
                throw invalid("Activities cannot overlap");
            }
            previousEnd = segment.getEndTime();
            var command = prepareCommand(userId, segment);
            command.setPayload(withScope(command.getPayload(), request.tripId(), segment.isReconstruction()));
            result.add(command);
        }
        return result;
    }

    private TimelineEditRequest prepareCommand(UUID userId, Activity segment) {
        var command = new TimelineEditRequest();
        command.setAnchorStart(segment.getStartTime());
        command.setAnchorEnd(segment.getEndTime());
        command.setAffectedFrom(segment.getStartTime());
        command.setAffectedTo(segment.getEndTime());
        if ("STAY".equals(segment.getSegmentType())) {
            prepareStay(userId, segment, command);
        } else if ("TRIP".equals(segment.getSegmentType())) {
            prepareTrip(userId, segment, command);
        } else throw invalid("Choose stayed at a place or travelled");
        return command;
    }

    private void prepareStay(UUID userId, Activity segment, TimelineEditRequest command) {
        command.setKind(TimelineEditKind.FORCE_STAY);
        Long favoriteId = segment.getFavoriteId();
        Long geocodingId = segment.getGeocodingId();
        String locationName = segment.getLocationName();
        // A stay given only by coordinates takes the place known at those coordinates, as a GPS stay does.
        if (favoriteId == null && geocodingId == null
                && segment.getLatitude() != null && segment.getLongitude() != null) {
            var resolved = locationPointResolver.resolveLocationWithReferences(userId,
                    GeoUtils.createPoint(
                            segment.getLongitude(), segment.getLatitude()));
            favoriteId = resolved.getFavoriteId();
            geocodingId = resolved.getGeocodingId();
            if (locationName == null || locationName.isBlank()) {
                locationName = resolved.getLocationName();
            }
        }
        PlaceStayPayload.PlaceStayPayloadBuilder payload = PlaceStayPayload.builder()
                .favoriteId(favoriteId).geocodingId(geocodingId)
                .latitude(segment.getLatitude()).longitude(segment.getLongitude())
                .locationName(locationName)
                .activityCorrection(true);
        if (segment.getLocationName() != null && !segment.getLocationName().isBlank()
                && segment.getFavoriteId() == null && segment.getGeocodingId() == null) {
            payload.locationNameCustom(true);
        } else if (locationName == null || locationName.isBlank()) {
            // No known place at these coordinates: let the post-edit geocoding pass name the stay.
            payload.resolveLocationName(true);
        }
        String visitName = segment.getVisitName() == null ? null : segment.getVisitName().trim();
        if (visitName != null && !visitName.isBlank()) {
            payload.visitName(visitName);
        }
        // A stay carved out of an existing trip is a split: its effect reaches the whole trip, and the legs it
        // leaves behind keep the manual-split explanation.
        edits.loadCurrentEvents(userId, segment.getStartTime(), segment.getEndTime()).stream()
                .filter(Trip.class::isInstance)
                .map(Trip.class::cast)
                .filter(trip -> !trip.getStartTime().isAfter(segment.getStartTime())
                        && trip.getEndTime().isAfter(segment.getEndTime()))
                .findFirst()
                .ifPresent(trip -> {
                    command.setAffectedFrom(trip.getStartTime());
                    command.setAffectedTo(trip.getEndTime());
                    payload.explanation("MANUAL_SPLIT").carveExplanation("MANUAL_SPLIT");
                });
        command.setPayload(payload.build());
    }

    private void prepareTrip(UUID userId, Activity segment, TimelineEditRequest command) {
        command.setKind(TimelineEditKind.FORCE_TRIP);
        PlaceTripPayload.PlaceTripPayloadBuilder raw = PlaceTripPayload.builder()
                .movementType(TripType.valueOf(segment.getMovementType() == null
                        ? "UNKNOWN" : segment.getMovementType()));
        addPlace(raw, "start", segment.getStartPlace());
        addPlace(raw, "end", segment.getEndPlace());
        command.setPayload(raw.build());
        // Resolve endpoints before the correction flag requires completed manual geometry.
        PlaceTripPayload resolved = (PlaceTripPayload) edits.resolvePayload(userId, command);
        var points = new ArrayList<TripReconstructionWaypointDto>();
        points.add(new TripReconstructionWaypointDto(resolved.startLatitude(), resolved.startLongitude()));
        if (segment.getWaypoints() != null) points.addAll(segment.getWaypoints());
        points.add(new TripReconstructionWaypointDto(resolved.endLatitude(), resolved.endLongitude()));
        try {
            ManualRouteGeometry.validate(points);
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage(), e);
        }
        double distance = ManualRouteGeometry.distance(points);
        var start = points.getFirst();
        var end = points.getLast();
        command.setPayload(resolved.toBuilder()
                .startLatitude(start.getLatitude()).startLongitude(start.getLongitude())
                .endLatitude(end.getLatitude()).endLongitude(end.getLongitude())
                .waypoints(points)
                .distanceMeters(Math.round(distance))
                .activityCorrection(true)
                .build());
    }

    private void addPlace(PlaceTripPayload.PlaceTripPayloadBuilder payload, String prefix, Place place) {
        if (place == null) throw invalid("Choose both trip endpoints");
        if (prefix.equals("start")) {
            if (place.favoriteId() != null) payload.startFavoriteId(place.favoriteId());
            if (place.geocodingId() != null) payload.startGeocodingId(place.geocodingId());
            if (place.latitude() != null) payload.startLatitude(place.latitude());
            if (place.longitude() != null) payload.startLongitude(place.longitude());
            if (place.locationName() != null) payload.startLocationName(place.locationName());
        } else {
            if (place.favoriteId() != null) payload.endFavoriteId(place.favoriteId());
            if (place.geocodingId() != null) payload.endGeocodingId(place.geocodingId());
            if (place.latitude() != null) payload.endLatitude(place.latitude());
            if (place.longitude() != null) payload.endLongitude(place.longitude());
            if (place.locationName() != null) payload.endLocationName(place.locationName());
        }
    }

    /** Marks a plan-scoped reconstruction correction, which a later recommit replaces as a group. */
    private static CorrectionPayload withScope(CorrectionPayload payload, Long tripId, boolean reconstruction) {
        if (payload instanceof PlaceStayPayload stay) {
            return stay.toBuilder()
                    .tripId(tripId == null ? stay.tripId() : tripId)
                    .reconstruction(reconstruction || Boolean.TRUE.equals(stay.reconstruction()))
                    .build();
        }
        if (payload instanceof PlaceTripPayload trip) {
            return trip.toBuilder()
                    .tripId(tripId == null ? trip.tripId() : tripId)
                    .reconstruction(reconstruction || Boolean.TRUE.equals(trip.reconstruction()))
                    .build();
        }
        return payload;
    }

    /**
     * Describes what a fill will carve and enforces the hybrid overlap rule:
     * <ul>
     *     <li>nested inside an earlier fill: allowed, the earlier fill remains for the rest;</li>
     *     <li>contains an earlier fill: allowed after an explicit replacement;</li>
     *     <li>crosses an earlier fill's edge: invalid, with Adjust range / Replace correction actions.</li>
     * </ul>
     * Fills over automatic events are informational. Removals never block a fill; time removed earlier can be filled
     * again.
     */
    private List<Warning> warnings(UUID userId, Request request, List<TimelineEditRequest> commands) {
        return edits.fillWarnings(userId, commands, request.replacesGroupId());
    }

    private static Map<String, Object> parameters(UUID groupId, Long correctionId, List<String> actions) {
        var parameters = new LinkedHashMap<String, Object>();
        if (groupId != null) parameters.put("conflictingGroupId", groupId);
        if (correctionId != null) parameters.put("conflictingEditId", correctionId);
        if (actions != null && !actions.isEmpty()) parameters.put("actions", actions);
        return parameters;
    }

    private String token(UUID userId, Request request, List<TimelineEditRequest> commands) {
        try {
            var canonical = new LinkedHashMap<String, Object>();
            canonical.put("userId", userId.toString());
            Instant from = request.contextStart() != null ? request.contextStart() : commands.getFirst().getAnchorStart();
            Instant to = request.contextEnd() != null ? request.contextEnd() : commands.getLast().getAnchorEnd();
            for (var command : commands) {
                if (command.getAnchorStart().isBefore(from)) from = command.getAnchorStart();
                if (command.getAnchorEnd().isAfter(to)) to = command.getAnchorEnd();
            }
            canonical.put("state", edits.previewState(userId, from, to));
            canonical.put("tripId", request.tripId());
            canonical.put("replacesGroupId", request.replacesGroupId());
            canonical.put("activities", commands.stream().map(command -> Map.of(
                    "kind", command.getKind().name(),
                    "start", command.getAnchorStart().toString(),
                    "end", command.getAnchorEnd().toString(),
                    "payload", command.getPayload())).toList());
            var bytes = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute the preview token", e);
        }
    }

    private static boolean overlaps(TimelineEvent event, Instant from, Instant to) {
        if (event.getDuration().isZero()) {
            return !event.getStartTime().isBefore(from) && event.getStartTime().isBefore(to);
        }
        return event.getStartTime().isBefore(to) && event.getEndTime().isAfter(from);
    }

    private static GeoPulseException invalid(String message, Throwable e) {
        return new GeoPulseException(INVALID_TIMELINE_REQUEST, message, e);
    }

    private static GeoPulseException invalid(String message) {
        return new GeoPulseException(INVALID_TIMELINE_REQUEST, message);
    }
}
