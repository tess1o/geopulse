package org.github.tess1o.geopulse.streaming.edits;

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
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.TimelineUserLock;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionSegmentDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.github.tess1o.geopulse.trips.service.TripAccessService;

import java.time.Duration;
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
    }

    public record Request(Long tripId, @NotEmpty @Size(max = 100) List<@Valid Activity> activities,
                          Instant contextStart, Instant contextEnd, Long replaceStayEditId) {
        public Request(Long tripId, List<Activity> activities, Instant contextStart, Instant contextEnd) {
            this(tripId, activities, contextStart, contextEnd, null);
        }
    }

    private record Replacement(TimelineEditEntity edit, TimelineStayEntity stay) {
    }

    public record Preview(List<TimelineEditPreviewDTO.Event> events, Instant startTime, Instant endTime) {
    }

    public record Saved(UUID groupId, List<TimelineEditDTO> edits, Instant affectedFrom) {
    }

    @Inject
    TimelineEditService edits;
    @Inject
    TimelineUserLock lock;
    @Inject
    org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository stays;
    @Inject
    org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository tripRepository;
    @Inject
    org.github.tess1o.geopulse.streaming.repository.TimelineEditRepository editRepository;
    @Inject
    EntityManager em;
    @Inject
    TripAccessService trips;

    @Transactional
    public Preview preview(UUID userId, Request request) {
        var replacement = replacement(userId, request);
        var commands = prepare(userId, request, replacement);
        var events = new ArrayList<TimelineEditPreviewDTO.Event>();
        for (var command : commands)
            events.addAll((replacement == null ? edits.preview(userId, command)
                    : edits.previewEdit(userId, command)).events().stream()
                    .filter(event -> !"DATA_GAP".equals(event.type())).toList());
        Instant start = commands.getFirst().getAnchorStart(), end = commands.getLast().getAnchorEnd();
        if (request.contextStart() != null && request.contextStart().isBefore(start)) start = request.contextStart();
        if (request.contextEnd() != null && request.contextEnd().isAfter(end)) end = request.contextEnd();
        for (var event : edits.loadCurrentEvents(userId, start, end)) {
            if (event instanceof org.github.tess1o.geopulse.streaming.model.domain.DataGap) continue;
            if (replacement != null && event instanceof org.github.tess1o.geopulse.streaming.model.domain.Stay
                    && event.getStartTime().equals(replacement.stay().getTimestamp())
                    && event.getDuration().getSeconds() == replacement.stay().getStayDuration()) continue;
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
        return new Preview(complete, start, end);
    }

    @Transactional
    public Saved save(UUID userId, Request request) {
        if (!lock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        var replacement = replacement(userId, request);
        var commands = prepare(userId, request, replacement);
        if (replacement != null) edits.revert(userId, replacement.edit().getId());
        UUID groupId = UUID.randomUUID();
        var result = new ArrayList<TimelineEditDTO>();
        for (var command : commands) {
            var dto = edits.create(userId, command);
            var entity = em.find(TimelineEditEntity.class, dto.id());
            entity.setGroupId(groupId);
            if (replacement != null && result.isEmpty()) {
                // Sequences survive backup imports and let Redo find the original after Undo clears supersededBy.
                entity.getPayload().put("replacesStaySequence", replacement.edit().getSeq());
                replacement.edit().setStatus(TimelineEditStatus.SUPERSEDED);
                replacement.edit().setSupersededBy(entity);
            }
            result.add(TimelineEditService.toDto(entity));
        }
        return new Saved(groupId, result, commands.getFirst().getAnchorStart());
    }

    @Transactional
    public Saved changeStatus(UUID userId, UUID groupId, boolean restore) {
        if (!lock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        var group = editRepository.findGroup(userId, groupId);
        if (group.isEmpty()) {
            throw new GeoPulseException(TIMELINE_EDIT_NOT_FOUND, "Correction not found or access denied");
        }
        var replacedStay = replacedStay(userId, group.getFirst());
        if (restore) {
            Replacement replacement = replacedStay == null ? null : requireReplaceable(userId, replacedStay);
            for (var edit : group) {
                if (edit.getStatus() != TimelineEditStatus.REVERTED) {
                    throw invalid("Only a completely reverted correction can be restored");
                }
                requireEmpty(userId, edit.getAnchorStart(), edit.getAnchorEnd(), replacement);
            }
            if (replacement != null) {
                edits.revert(userId, replacedStay.getId());
                replacedStay.setStatus(TimelineEditStatus.SUPERSEDED);
                replacedStay.setSupersededBy(group.getFirst());
            }
        } else if (replacedStay != null && (replacedStay.getStatus() != TimelineEditStatus.SUPERSEDED
                || replacedStay.getSupersededBy() == null
                || !groupId.equals(replacedStay.getSupersededBy().getGroupId()))) {
            throw invalid("This group no longer replaces its original stay");
        }
        var result = new ArrayList<TimelineEditDTO>();
        for (var edit : group) {
            if (!restore && edit.getStatus() != TimelineEditStatus.ACTIVE) {
                throw invalid("Only an active correction can be undone");
            }
            result.add((restore ? edits.restore(userId, edit.getId()) : edits.revert(userId, edit.getId())).orElseThrow());
        }
        if (!restore && replacedStay != null) {
            replacedStay.setStatus(TimelineEditStatus.REVERTED);
            replacedStay.setSupersededBy(null);
            edits.restore(userId, replacedStay.getId());
        }
        return new Saved(groupId, result, group.stream().map(TimelineEditEntity::getAffectedFrom).min(Instant::compareTo).orElseThrow());
    }

    private TimelineEditEntity replacedStay(UUID userId, TimelineEditEntity first) {
        if (!(first.getPayload().get("replacesStaySequence") instanceof Number seq)) return null;
        return editRepository.find("user.id = ?1 and seq = ?2", userId, seq.longValue()).firstResultOptional()
                .orElseThrow(() -> invalid("The original forced stay no longer exists"));
    }

    private Replacement replacement(UUID userId, Request request) {
        if (request == null || request.replaceStayEditId() == null) return null;
        return requireReplaceable(userId, editRepository.findByIdAndUser(request.replaceStayEditId(), userId)
                .orElseThrow(() -> new GeoPulseException(TIMELINE_EDIT_NOT_FOUND, "Correction not found or access denied")));
    }

    private Replacement requireReplaceable(UUID userId, TimelineEditEntity edit) {
        var target = edit.getApplication();
        if (edit.getKind() != TimelineEditKind.FORCE_STAY || edit.getStatus() != TimelineEditStatus.ACTIVE
                || edit.getGroupId() != null || Boolean.TRUE.equals(edit.getPayload().get("reconstruction"))
                || target == null || !"STAY".equals(target.getTargetType())) {
            throw invalid("Select an active, applied forced stay outside a correction group");
        }
        long duration = Duration.between(edit.getAnchorStart(), edit.getAnchorEnd()).getSeconds();
        var stay = stays.findOverlapping(userId, edit.getAnchorStart(), edit.getAnchorEnd()).stream()
                .filter(row -> row.getId().equals(target.getTargetId()) && row.getTimestamp().equals(edit.getAnchorStart())
                        && row.getStayDuration() == duration)
                .findFirst().orElseThrow(() -> invalid("The original forced stay has changed"));
        return new Replacement(edit, stay);
    }

    private static Activity remainingStay(Replacement replacement, Instant start, Instant end) {
        var stay = replacement.stay();
        var activity = new Activity();
        activity.setSegmentType("STAY");
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setLatitude(stay.getLocation().getY());
        activity.setLongitude(stay.getLocation().getX());
        activity.setLocationName(stay.getLocationName());
        activity.setVisitName(stay.getVisitName());
        if (stay.getFavoriteLocation() != null) activity.setFavoriteId(stay.getFavoriteLocation().getId());
        else if (stay.getGeocodingLocation() != null) activity.setGeocodingId(stay.getGeocodingLocation().getId());
        return activity;
    }

    private List<TimelineEditRequest> prepare(UUID userId, Request request, Replacement replacement) {
        if (request == null || request.activities() == null || request.activities().isEmpty() || request.activities().size() > 100) {
            throw invalid("Provide between 1 and 100 activities");
        }
        var segments = new ArrayList<>(request.activities());
        for (var segment : segments) {
            if (segment == null || segment.getStartTime() == null || segment.getEndTime() == null
                    || !segment.getEndTime().isAfter(segment.getStartTime()) || segment.getEndTime().isAfter(Instant.now())) {
                throw invalid("Each activity needs a positive interval ending in the past");
            }
        }
        if (replacement != null) {
            var trip = segments.getFirst();
            var start = replacement.edit().getAnchorStart();
            var end = replacement.edit().getAnchorEnd();
            if (segments.size() != 1 || !"TRIP".equals(trip.getSegmentType())
                    || trip.getStartTime().isBefore(start) || trip.getEndTime().isAfter(end)) {
                throw invalid("Provide one trip within the forced stay");
            }
            if (trip.getStartTime().isAfter(start)) segments.add(remainingStay(replacement, start, trip.getStartTime()));
            if (trip.getEndTime().isBefore(end)) segments.add(remainingStay(replacement, trip.getEndTime(), end));
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
            requireEmpty(userId, segment.getStartTime(), segment.getEndTime(), replacement);
            result.add(prepareCommand(userId, segment));
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
            command.setKind(TimelineEditKind.FORCE_STAY);
            command.setLatitude(segment.getLatitude());
            command.setLongitude(segment.getLongitude());
            command.setFavoriteId(segment.getFavoriteId());
            command.setGeocodingId(segment.getGeocodingId());
            command.setPlaceName(segment.getLocationName());
            if (segment.getVisitName() != null && !segment.getVisitName().isBlank())
                command.getPayload().put("visitName", segment.getVisitName().trim());
        } else if ("TRIP".equals(segment.getSegmentType())) {
            prepareTrip(userId, segment, command);
        } else throw invalid("Choose stayed at a place or travelled");
        command.getPayload().put("activityCorrection", true);
        return command;
    }

    private void prepareTrip(UUID userId, Activity segment, TimelineEditRequest command) {
        command.setKind(TimelineEditKind.FORCE_TRIP);
        addPlace(command, "start", segment.getStartPlace());
        addPlace(command, "end", segment.getEndPlace());
        // Resolve endpoints before the correction flag requires completed manual geometry.
        var resolved = edits.resolvedPayload(userId, command);
        var points = new ArrayList<TripReconstructionWaypointDto>();
        points.add(new TripReconstructionWaypointDto(
                ((Number) resolved.get("startLatitude")).doubleValue(), ((Number) resolved.get("startLongitude")).doubleValue()));
        if (segment.getWaypoints() != null) points.addAll(segment.getWaypoints());
        points.add(new TripReconstructionWaypointDto(
                ((Number) resolved.get("endLatitude")).doubleValue(), ((Number) resolved.get("endLongitude")).doubleValue()));
        try {
            ManualRouteGeometry.validate(points);
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage(), e);
        }
        double distance = ManualRouteGeometry.distance(points);
        var start = points.getFirst();
        var end = points.getLast();
        command.getPayload().put("startLatitude", start.getLatitude());
        command.getPayload().put("startLongitude", start.getLongitude());
        command.getPayload().put("endLatitude", end.getLatitude());
        command.getPayload().put("endLongitude", end.getLongitude());
        command.getPayload().put("waypoints", points);
        command.getPayload().put("distanceMeters", Math.round(distance));
        command.getPayload().put("movementType", TripType.valueOf(segment.getMovementType() == null ? "UNKNOWN" : segment.getMovementType()).name());
    }

    private void addPlace(TimelineEditRequest request, String prefix, Place place) {
        if (place == null) throw invalid("Choose both trip endpoints");
        var payload = request.getPayload();
        if (place.favoriteId() != null) payload.put(prefix + "FavoriteId", place.favoriteId());
        if (place.geocodingId() != null) payload.put(prefix + "GeocodingId", place.geocodingId());
        if (place.latitude() != null) payload.put(prefix + "Latitude", place.latitude());
        if (place.longitude() != null) payload.put(prefix + "Longitude", place.longitude());
        if (place.locationName() != null) payload.put(prefix + "LocationName", place.locationName());
    }

    private void requireEmpty(UUID userId, Instant start, Instant end, Replacement replacement) {
        if (stays.findOverlapping(userId, start, end).stream()
                .anyMatch(stay -> replacement == null || !stay.getId().equals(replacement.stay().getId()))
                || !tripRepository.findOverlapping(userId, start, end).isEmpty())
            throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, "This interval already contains a stay or trip. Edit that event instead.");
        if (editRepository.findActiveOverlapping(userId, start, end).stream().anyMatch(edit -> edit.getKind().structural()
                && (replacement == null || !edit.getId().equals(replacement.edit().getId()))))
            throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, "This interval already has a manual correction. Open its history instead.");
    }

    private static GeoPulseException invalid(String message, Throwable e) {
        return new GeoPulseException(INVALID_TIMELINE_REQUEST, message, e);
    }

    private static GeoPulseException invalid(String message) {
        return new GeoPulseException(INVALID_TIMELINE_REQUEST, message);
    }
}
