package org.github.tess1o.geopulse.streaming.edits;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.service.trips.TripWaterClassificationService;
import org.github.tess1o.geopulse.streaming.util.TimelineGpsAccuracyFilter;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditTargetDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditApplicationEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditApplicationRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.TimelineUserLock;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_EDIT_CONFLICT;

@ApplicationScoped
public class TimelineEditService {
    @Inject ObjectMapper objectMapper;
    @Inject TimelineEditRepository editRepository;
    @Inject TimelineEditApplicationRepository applicationRepository;
    @Inject TimelineStayRepository stayRepository;
    @Inject TimelineTripRepository tripRepository;
    @Inject TimelineDataGapRepository gapRepository;
    @Inject TimelineRebuildRequestRepository rebuildRequestRepository;
    @Inject FavoritesRepository favoritesRepository;
    @Inject TimelineEditApplier applier;
    @Inject TimelineUserLock userLock;
    @Inject EntityManager entityManager;
    @Inject GpsPointRepository gpsPoints;
    @Inject TimelineConfigurationProvider configuration;
    @Inject TimelineEventFinalizationService finalization;
    @Inject TripWaterClassificationService waterClassification;

    @Transactional
    public List<TimelineEditDTO> list(UUID userId) {
        return editRepository.findByUser(userId).stream().map(TimelineEditService::toDto).toList();
    }

    public TimelineEditPreviewDTO preview(UUID userId, TimelineEditRequest request) {
        validateRequest(request);
        if (request.getKind().structural()) {
            Instant from = request.getAnchorStart().isBefore(request.getAffectedFrom()) ? request.getAnchorStart() : request.getAffectedFrom();
            Instant to = request.getAnchorEnd().isAfter(request.getAffectedTo()) ? request.getAnchorEnd() : request.getAffectedTo();
            editRepository.findActiveOverlapping(userId, from, to).stream()
                    .filter(edit -> edit.getGroupId() != null && edit.getKind().structural())
                    .findFirst().ifPresent(edit -> {
                        throw new GeoPulseException(TIMELINE_EDIT_CONFLICT,
                                "Undo the activity correction group before changing its structure",
                                Map.of("conflictingEditId", edit.getId()));
                    });
        }
        if (insertsAtAnchor(request.getKind()) && !(request.getPayload() != null && Boolean.TRUE.equals(request.getPayload().get("activityCorrection")))) {
            gpsPoints.findLatest(userId).ifPresent(latest -> {
                boolean ongoing = gapRepository.findByUserIdAndTimeRange(userId,
                                request.getAnchorStart(), request.getAnchorEnd()).stream()
                        .anyMatch(gap -> gap.getStartTime().equals(latest.getTimestamp())
                                && gap.getStartTime().isBefore(request.getAnchorEnd())
                                && gap.getEndTime().isAfter(request.getAnchorStart()));
                if (ongoing) throw new GeoPulseException(INVALID_TIMELINE_REQUEST,
                        "Cannot convert an ongoing data gap");
            });
        }
        TimelineEditCommand command = new TimelineEditCommand(0L, 1, request.getKind(), request.getAnchorStart(),
                request.getAnchorEnd(), resolvedPayload(userId, request));
        TimelineEditResult result = applier.apply(loadCurrentEvents(userId, request.getAffectedFrom(),
                request.getAffectedTo()), List.of(command), trip -> recalculateTrip(userId, trip));
        if (!result.unappliedEditIds().isEmpty()) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "The edit does not match a current timeline event");
        }
        return new TimelineEditPreviewDTO(result.events().stream()
                .filter(event -> overlaps(event, request.getAffectedFrom(), request.getAffectedTo()))
                .map(TimelineEditService::previewEvent)
                .toList());
    }

    @Transactional
    public TimelineEditDTO create(UUID userId, TimelineEditRequest request) {
        validateRequest(request);
        if (!userLock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        preview(userId, request);

        List<TimelineEditEntity> conflicts = editRepository.findActiveOverlapping(userId,
                        request.getAffectedFrom(), request.getAffectedTo()).stream()
                .filter(existing -> conflicts(existing, request))
                .toList();
        Optional<TimelineEditEntity> structuralConflict = conflicts.stream()
                .filter(conflict -> conflict.getKind().structural()).findFirst();
        if (structuralConflict.isPresent() && !request.isSupersede()) {
            TimelineEditEntity conflict = structuralConflict.get();
            throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, "The edit overlaps an active timeline correction",
                    Map.of("conflictingEditId", conflict.getId(),
                            "from", conflict.getAffectedFrom(), "to", conflict.getAffectedTo()));
        }

        LinkedHashMap<String, Object> payload = resolvedPayload(userId, request);
        Long favoriteId = request.getFavoriteId() != null ? request.getFavoriteId() : longValue(payload, "favoriteId");
        Long geocodingId = request.getGeocodingId() != null ? request.getGeocodingId() : longValue(payload, "geocodingId");
        FavoritesEntity favorite = favorite(userId, favoriteId);
        ReverseGeocodingLocationEntity geocoding = geocoding(userId, geocodingId);
        TimelineEditEntity entity = TimelineEditEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .seq(editRepository.nextSeq(userId))
                .kind(request.getKind())
                .anchorStart(request.getAnchorStart())
                .anchorEnd(request.getAnchorEnd())
                .affectedFrom(request.getAffectedFrom())
                .affectedTo(request.getAffectedTo())
                .payload(payload)
                .beforeValues(captureBeforeValues(userId, request))
                .placeFavorite(favorite)
                .placeGeocoding(geocoding)
                .placeLatitude(doubleValue(payload, "latitude"))
                .placeLongitude(doubleValue(payload, "longitude"))
                .placeName(textValue(payload, "locationName"))
                .status(TimelineEditStatus.ACTIVE)
                .build();
        editRepository.persist(entity);
        entityManager.flush();
        for (TimelineEditEntity conflict : conflicts) {
            conflict.setStatus(TimelineEditStatus.SUPERSEDED);
            conflict.setSupersededBy(entity);
            applicationRepository.deleteById(conflict.getId());
        }
        rebuildRequestRepository.request(userId, request.getAffectedFrom());
        return toDto(entity);
    }

    @Transactional
    public Optional<TimelineEditDTO> revert(UUID userId, Long editId) {
        if (!userLock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        Optional<TimelineEditEntity> found = editRepository.findByIdAndUser(editId, userId);
        found.ifPresent(edit -> {
            edit.setStatus(TimelineEditStatus.REVERTED);
            applicationRepository.deleteById(edit.getId());
            rebuildRequestRepository.request(userId, edit.getAffectedFrom());
        });
        return found.map(TimelineEditService::toDto);
    }

    @Transactional
    public Optional<TimelineEditDTO> restore(UUID userId, Long editId) {
        if (!userLock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        Optional<TimelineEditEntity> found = editRepository.findByIdAndUser(editId, userId);
        found.ifPresent(edit -> {
            if (edit.getStatus() != TimelineEditStatus.REVERTED) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Only a reverted edit can be restored");
            }
            Optional<TimelineEditEntity> conflict = editRepository.findActiveOverlapping(userId,
                            edit.getAffectedFrom(), edit.getAffectedTo()).stream()
                    .filter(active -> active.getId() != null && !active.getId().equals(edit.getId()))
                    .filter(active -> conflicts(active, edit))
                    .findFirst();
            if (conflict.isPresent()) {
                throw new GeoPulseException(TIMELINE_EDIT_CONFLICT,
                        "A newer timeline edit conflicts with this restore",
                        Map.of("conflictingEditId", conflict.get().getId()));
            }
            edit.setStatus(TimelineEditStatus.ACTIVE);
            rebuildRequestRepository.request(userId, edit.getAffectedFrom());
        });
        return found.map(TimelineEditService::toDto);
    }

    public boolean grouped(UUID userId, Long editId) {
        return editRepository.findByIdAndUser(editId, userId).map(edit -> edit.getGroupId() != null).orElse(false);
    }

    public Instant activeEnd(UUID userId) {
        // Only missing-activity corrections can be replayed without generated GPS events.
        return editRepository.findByUser(userId).stream()
                .filter(edit -> edit.getStatus() == TimelineEditStatus.ACTIVE
                        && Boolean.TRUE.equals(edit.getPayload().get("activityCorrection")))
                .map(TimelineEditEntity::getAffectedTo).max(Instant::compareTo).orElse(null);
    }

    public TimelineEditResult apply(UUID userId, List<TimelineEvent> events, Instant from, Instant to) {
        List<TimelineEditCommand> commands = editRepository.findActiveOverlapping(userId, from, to).stream()
                // A forced event is created at its anchor without matching an existing event. When the regeneration
                // starts after that anchor, the copy already persisted lies before "from" and outlives the pass, so
                // inserting another would duplicate it. Its end may lie after "to": nothing generated exists there,
                // and the edit itself defines how far the event reaches.
                .filter(edit -> !insertsAtAnchor(edit.getKind()) || !edit.getAnchorStart().isBefore(from))
                .map(TimelineEditService::command)
                .toList();
        // Generation owns these events; without edits there is no mutation to isolate with deep copies.
        if (commands.isEmpty()) return new TimelineEditResult(List.copyOf(events), Map.of(), List.of());
        List<TimelineEvent> covered = events;
        var insertions = commands.stream().filter(command -> insertsAtAnchor(command.kind())).toList();
        if (!insertions.isEmpty()) {
            covered = new ArrayList<>(events);
            Instant firstInsertion = insertions.stream().map(TimelineEditCommand::anchorStart).min(Instant::compareTo).orElseThrow();
            if (events.isEmpty()) {
                if (Duration.between(firstInsertion, to).getSeconds() > 0)
                    covered.add(DataGap.fromTimeRange(firstInsertion, to));
            } else {
                // Extend coverage for edits outside generated history; preserve ordinary spacing inside it.
                Instant firstEvent = events.stream().map(TimelineEvent::getStartTime).min(Instant::compareTo).orElseThrow();
                Instant lastEvent = events.stream().map(TimelineEvent::getEndTime).max(Instant::compareTo).orElseThrow();
                Instant lastInsertion = insertions.stream().map(TimelineEditCommand::anchorEnd).max(Instant::compareTo).orElseThrow();
                if (Duration.between(firstInsertion, firstEvent).getSeconds() > 0)
                    covered.add(DataGap.fromTimeRange(firstInsertion, firstEvent));
                if (Duration.between(lastEvent, lastInsertion).getSeconds() > 0)
                    covered.add(DataGap.fromTimeRange(lastEvent, lastInsertion));
            }
        }
        return applier.apply(covered, commands, trip -> recalculateTrip(userId, trip));
    }

    /** Kinds that always insert their event at the anchor instead of matching one of the regenerated events. */
    private static boolean insertsAtAnchor(TimelineEditKind kind) {
        return kind == TimelineEditKind.FORCE_STAY || kind == TimelineEditKind.FORCE_TRIP;
    }

    private void recalculateTrip(UUID userId, Trip trip) {
        if (trip.getManualRoute() != null) return;
        var config = configuration.getConfigurationForUser(userId);
        Instant from = trip.getStartPoint().getTimestamp().isBefore(trip.getStartTime())
                ? trip.getStartPoint().getTimestamp() : trip.getStartTime();
        Instant to = trip.getEndPoint().getTimestamp().isAfter(trip.getEndTime())
                ? trip.getEndPoint().getTimestamp() : trip.getEndTime();
        List<GPSPoint> recording = gpsPoints.findEssentialPointsInInterval(userId, from, to).stream()
                .filter(point -> TimelineGpsAccuracyFilter.shouldInclude(point, config)).toList();
        if (recording.size() < 2) {
            recording = List.of(trip.getStartPoint(), trip.getEndPoint());
        }
        List<GPSPoint> points = new ArrayList<>();
        points.add(pointAt(recording, trip.getStartTime()));
        recording.stream().filter(point -> point.getTimestamp().isAfter(trip.getStartTime())
                && point.getTimestamp().isBefore(trip.getEndTime())).forEach(points::add);
        points.add(pointAt(recording, trip.getEndTime()));
        var state = new UserState();
        points.forEach(state::addActivePoint);
        Trip calculated = finalization.finalizeTrip(state, config);
        if (calculated == null) throw new IllegalArgumentException("Unable to calculate edited trip");
        trip.setStartPoint(calculated.getStartPoint());
        trip.setEndPoint(calculated.getEndPoint());
        trip.setDistanceMeters(calculated.getDistanceMeters());
        trip.setStatistics(calculated.getStatistics());
        trip.setWaterStatistics(waterClassification.calculateStatistics(userId, trip.getStartTime(), trip.getEndTime(), config));
        trip.setAltitudeStatistics(calculated.getAltitudeStatistics());
    }

    private static GPSPoint pointAt(List<GPSPoint> points, Instant time) {
        GPSPoint left = points.getFirst();
        for (GPSPoint right : points) {
            if (right.getTimestamp().equals(time)) return right;
            if (right.getTimestamp().isAfter(time)) {
                if (!left.getTimestamp().isBefore(time)) return atTime(right, time);
                double ratio = (double) Duration.between(left.getTimestamp(), time).toMillis()
                        / Duration.between(left.getTimestamp(), right.getTimestamp()).toMillis();
                return GPSPoint.builder().timestamp(time)
                        .latitude(left.getLatitude() + ratio * (right.getLatitude() - left.getLatitude()))
                        .longitude(Math.IEEEremainder(left.getLongitude()
                                + ratio * Math.IEEEremainder(right.getLongitude() - left.getLongitude(), 360), 360))
                        .speed(left.getSpeed() + ratio * (right.getSpeed() - left.getSpeed()))
                        .accuracy(Math.max(left.getAccuracy(), right.getAccuracy()))
                        .altitude(left.getAltitude() == null || right.getAltitude() == null ? null
                                : left.getAltitude() + ratio * (right.getAltitude() - left.getAltitude()))
                        .onWater(java.util.Objects.equals(left.getOnWater(), right.getOnWater()) ? left.getOnWater() : null)
                        .build();
            }
            left = right;
        }
        return atTime(left, time);
    }

    private static GPSPoint atTime(GPSPoint point, Instant time) {
        return GPSPoint.builder().timestamp(time).latitude(point.getLatitude()).longitude(point.getLongitude())
                .speed(point.getSpeed()).accuracy(point.getAccuracy()).altitude(point.getAltitude())
                .onWater(point.getOnWater()).build();
    }

    @Transactional
    public void clearMissingApplications(UUID userId) {
        applicationRepository.deleteMissingTargets(userId);
    }

    @Transactional
    public void recordApplications(UUID userId, TimelineEditResult result) {
        for (Long editId : result.appliedEditIds()) applicationRepository.deleteById(editId);
        // An unmatched edit can target an intact event before this partial pass; missing targets are cleared after persistence.
        entityManager.flush();
        result.applications().forEach((editId, event) -> findPersistedTarget(userId, event).ifPresent(target -> {
            TimelineEditApplicationEntity application = TimelineEditApplicationEntity.builder()
                    .edit(entityManager.getReference(TimelineEditEntity.class, editId))
                    .targetType(target.type())
                    .targetId(target.id())
                    .build();
            applicationRepository.persist(application);
        }));
    }

    public static TimelineEditDTO toDto(TimelineEditEntity entity) {
        TimelineEditApplicationEntity application = entity.getApplication();
        return TimelineEditDTO.builder()
                .id(entity.getId()).groupId(entity.getGroupId()).seq(entity.getSeq()).kind(entity.getKind())
                .anchorStart(entity.getAnchorStart()).anchorEnd(entity.getAnchorEnd())
                .affectedFrom(entity.getAffectedFrom()).affectedTo(entity.getAffectedTo())
                .payload(entity.getPayload()).beforeValues(entity.getBeforeValues()).status(entity.getStatus())
                .applied(application != null)
                .target(application == null ? null : new TimelineEditTargetDTO(
                        application.getTargetType(), application.getTargetId()))
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .build();
    }

    private LinkedHashMap<String, Object> captureBeforeValues(UUID userId, TimelineEditRequest request) {
        return loadCurrentEvents(userId, request.getAnchorStart(), request.getAnchorEnd()).stream()
                .filter(event -> event.getStartTime().equals(request.getAnchorStart())
                        && event.getEndTime().equals(request.getAnchorEnd()))
                .findFirst().map(event -> {
                    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                    values.put("type", event.getType().name());
                    values.put("start", event.getStartTime().toString());
                    values.put("end", event.getEndTime().toString());
                    if (event instanceof Stay stay) {
                        values.put("visitName", stay.getVisitName());
                        values.put("locationName", stay.getLocationName());
                        values.put("favoriteId", stay.getFavoriteId());
                        values.put("geocodingId", stay.getGeocodingId());
                        values.put("latitude", stay.getLatitude());
                        values.put("longitude", stay.getLongitude());
                    } else if (event instanceof Trip trip) {
                        values.put("movementType", trip.getTripType().name());
                        values.put("startLatitude", trip.getStartPoint().getLatitude());
                        values.put("startLongitude", trip.getStartPoint().getLongitude());
                        values.put("endLatitude", trip.getEndPoint().getLatitude());
                        values.put("endLongitude", trip.getEndPoint().getLongitude());
                    }
                    return values;
                }).orElse(null);
    }

    public List<TimelineEvent> loadCurrentEvents(UUID userId, Instant from, Instant to) {
        List<TimelineEvent> result = new ArrayList<>();
        stayRepository.findOverlapping(userId, from, to).forEach(stay -> result.add(fromEntity(stay)));
        tripRepository.findOverlapping(userId, from, to).forEach(trip -> result.add(fromEntity(trip)));
        gapRepository.findByUserIdAndTimeRange(userId, from, to).forEach(gap -> result.add(fromEntity(gap)));
        result.sort(Comparator.comparing(TimelineEvent::getStartTime).thenComparing(TimelineEvent::getEndTime));
        return result;
    }

    private Optional<Target> findPersistedTarget(UUID userId, TimelineEvent event) {
        if (event instanceof Stay) {
            return stayRepository.findOverlapping(userId, event.getStartTime(), event.getEndTime()).stream()
                    .filter(stay -> exact(stay.getTimestamp(), stay.getStayDuration(), event))
                    .findFirst().map(stay -> new Target("STAY", stay.getId()));
        }
        if (event instanceof Trip) {
            return tripRepository.findOverlapping(userId, event.getStartTime(), event.getEndTime()).stream()
                    .filter(trip -> exact(trip.getTimestamp(), trip.getTripDuration(), event))
                    .findFirst().map(trip -> new Target("TRIP", trip.getId()));
        }
        return gapRepository.findByUserIdAndTimeRange(userId, event.getStartTime(), event.getEndTime()).stream()
                .filter(gap -> gap.getStartTime().equals(event.getStartTime()) && gap.getEndTime().equals(event.getEndTime()))
                .findFirst().map(gap -> new Target("DATA_GAP", gap.getId()));
    }

    private static boolean exact(Instant start, long duration, TimelineEvent event) {
        return start.equals(event.getStartTime()) && duration == event.getDuration().getSeconds();
    }

    private static TimelineEditCommand command(TimelineEditEntity entity) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(entity.getPayload());
        Map<String, Object> before = entity.getBeforeValues();
        if (before != null) {
            Object sourceType = before.get("type");
            if (sourceType == null && before.containsKey("movementType")) sourceType = "TRIP";
            if (sourceType == null && before.containsKey("locationName")) sourceType = "STAY";
            if (sourceType != null) payload.put("sourceType", sourceType);
            for (String key : List.of("startLatitude", "startLongitude", "endLatitude", "endLongitude")) {
                if (before.containsKey(key)) payload.put("source" + Character.toUpperCase(key.charAt(0)) + key.substring(1), before.get(key));
            }
        }
        FavoritesEntity favorite = entity.getPlaceFavorite();
        ReverseGeocodingLocationEntity geocoding = entity.getPlaceGeocoding();
        payload.remove("favoriteId");
        if (favorite != null) payload.put("favoriteId", favorite.getId());
        payload.remove("geocodingId");
        if (geocoding != null) payload.put("geocodingId", geocoding.getId());
        putIfPresent(payload, "latitude", entity.getPlaceLatitude());
        putIfPresent(payload, "longitude", entity.getPlaceLongitude());
        // Migrated split stays may have a custom name independent from the referenced place.
        if (!Boolean.TRUE.equals(payload.get("locationNameCustom"))) {
            if (favorite != null) payload.put("locationName", favorite.getName());
            else if (geocoding != null) payload.put("locationName", geocoding.getDisplayName());
            else putIfPresent(payload, "locationName", entity.getPlaceName());
        }
        return new TimelineEditCommand(entity.getId(), entity.getSeq(), entity.getKind(),
                entity.getAnchorStart(), entity.getAnchorEnd(), payload);
    }

    private static LinkedHashMap<String, Object> normalizedPayload(TimelineEditRequest request) {
        LinkedHashMap<String, Object> payload = request.getPayload() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getPayload());
        putIfPresent(payload, "favoriteId", request.getFavoriteId());
        putIfPresent(payload, "geocodingId", request.getGeocodingId());
        putIfPresent(payload, "latitude", request.getLatitude());
        putIfPresent(payload, "longitude", request.getLongitude());
        putIfPresent(payload, "locationName", request.getPlaceName());
        return payload;
    }

    public LinkedHashMap<String, Object> resolvedPayload(UUID userId, TimelineEditRequest request) {
        LinkedHashMap<String, Object> payload = normalizedPayload(request);
        resolvePlace(userId, payload, "");
        // A split stay's second place is a reference too, resolved the same way into a prefixed snapshot.
        resolvePlace(userId, payload, "right");
        resolvePlace(userId, payload, "start");
        resolvePlace(userId, payload, "end");
        if ((request.getKind() == TimelineEditKind.SET_PLACE || request.getKind() == TimelineEditKind.FORCE_STAY)
                && (doubleValue(payload, "latitude") == null || doubleValue(payload, "longitude") == null)) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose a place with valid coordinates");
        }
        if (request.getKind() == TimelineEditKind.FORCE_TRIP) {
            if (Boolean.TRUE.equals(payload.get("activityCorrection"))) ManualRouteGeometry.read(payload.get("waypoints"), objectMapper);
            for (String prefix : List.of("start", "end")) {
                Double latitude = doubleValue(payload, prefix + "Latitude");
                Double longitude = doubleValue(payload, prefix + "Longitude");
                if (latitude == null || longitude == null) {
                    throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose both trip endpoints");
                }
            }
        }
        return payload;
    }

    private void resolvePlace(UUID userId, LinkedHashMap<String, Object> payload, String prefix) {
        String favoriteKey = placeKey(prefix, "favoriteId");
        String geocodingKey = placeKey(prefix, "geocodingId");
        Long favoriteId = longValue(payload, favoriteKey);
        Long geocodingId = longValue(payload, geocodingKey);
        if (favoriteId != null && geocodingId != null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST,
                    "A place edit cannot reference both a favorite and a geocoded place");
        }
        FavoritesEntity favorite = favorite(userId, favoriteId);
        if (favorite != null) {
            payload.put(favoriteKey, favorite.getId());
            payload.remove(geocodingKey);
            payload.put(placeKey(prefix, "locationName"), favorite.getName());
            if (favorite.getGeometry() != null && !favorite.getGeometry().isEmpty()) {
                var point = favorite.getGeometry().getInteriorPoint();
                payload.put(placeKey(prefix, "latitude"), point.getY());
                payload.put(placeKey(prefix, "longitude"), point.getX());
            }
        }
        ReverseGeocodingLocationEntity geocoding = geocoding(userId, geocodingId);
        if (geocoding != null) {
            var point = geocoding.getResultCoordinates() != null
                    ? geocoding.getResultCoordinates() : geocoding.getRequestCoordinates();
            payload.put(geocodingKey, geocoding.getId());
            payload.remove(favoriteKey);
            payload.put(placeKey(prefix, "locationName"), geocoding.getDisplayName());
            payload.put(placeKey(prefix, "latitude"), point.getY());
            payload.put(placeKey(prefix, "longitude"), point.getX());
        }
        String latitudeKey = placeKey(prefix, "latitude");
        String longitudeKey = placeKey(prefix, "longitude");
        if (payload.containsKey(latitudeKey) || payload.containsKey(longitudeKey)) {
            Double latitude = doubleValue(payload, latitudeKey);
            Double longitude = doubleValue(payload, longitudeKey);
            if (latitude == null || longitude == null) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "A place requires both coordinates");
            }
            try { GeoUtils.createPoint(longitude, latitude); }
            catch (IllegalArgumentException invalid) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Place coordinates must be valid", invalid);
            }
        }
    }

    private static String placeKey(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static void validateRequest(TimelineEditRequest request) {
        if (request == null || request.getKind() == null || request.getAnchorStart() == null
                || request.getAnchorEnd() == null || request.getAnchorEnd().isBefore(request.getAnchorStart())) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "A kind and a valid anchor interval are required");
        }
        boolean pointAnchor = request.getAnchorStart().equals(request.getAnchorEnd());
        if (pointAnchor && request.getKind() != TimelineEditKind.SET_MOVEMENT_TYPE
                && request.getKind() != TimelineEditKind.SET_TIMES && request.getKind() != TimelineEditKind.REMOVE_EVENT) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "This edit requires a positive anchor interval");
        }
        if (pointAnchor && request.getKind() == TimelineEditKind.REMOVE_EVENT
                && (request.getPayload() == null || !"DISMISS".equals(request.getPayload().get("resolution")))) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "A zero-duration transition must be dismissed without filling time");
        }
        if (request.getAffectedFrom() == null) request.setAffectedFrom(request.getAnchorStart());
        if (request.getAffectedTo() == null) request.setAffectedTo(pointAnchor
                ? request.getAnchorEnd().plusSeconds(1) : request.getAnchorEnd());
        if (!request.getAffectedTo().isAfter(request.getAffectedFrom())) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Affected end must be after affected start");
        }
    }

    private static boolean conflicts(TimelineEditEntity existing, TimelineEditRequest requested) {
        if (existing.getKind().structural() || requested.getKind().structural()) {
            return existing.getKind().structural() && requested.getKind().structural();
        }
        return existing.getKind() == requested.getKind()
                && mutualMajorityOverlap(existing.getAnchorStart(), existing.getAnchorEnd(),
                requested.getAnchorStart(), requested.getAnchorEnd());
    }

    private static boolean conflicts(TimelineEditEntity existing, TimelineEditEntity requested) {
        if (existing.getKind().structural() || requested.getKind().structural()) {
            return existing.getKind().structural() && requested.getKind().structural();
        }
        return existing.getKind() == requested.getKind()
                && mutualMajorityOverlap(existing.getAnchorStart(), existing.getAnchorEnd(),
                requested.getAnchorStart(), requested.getAnchorEnd());
    }

    private static boolean mutualMajorityOverlap(Instant firstStart, Instant firstEnd,
                                                 Instant secondStart, Instant secondEnd) {
        if (firstStart.equals(firstEnd) || secondStart.equals(secondEnd)) {
            return firstStart.equals(secondStart) && firstEnd.equals(secondEnd);
        }
        Instant overlapStart = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        Instant overlapEnd = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        if (!overlapEnd.isAfter(overlapStart)) return false;
        long overlap = Duration.between(overlapStart, overlapEnd).toMillis();
        return overlap * 2 > Duration.between(firstStart, firstEnd).toMillis()
                && overlap * 2 > Duration.between(secondStart, secondEnd).toMillis();
    }

    private static boolean overlaps(TimelineEvent event, Instant from, Instant to) {
        if (event instanceof Trip && event.getDuration().isZero()) {
            return !event.getStartTime().isBefore(from) && event.getStartTime().isBefore(to);
        }
        return event.getStartTime().isBefore(to) && event.getEndTime().isAfter(from);
    }

    static TimelineEditPreviewDTO.Event previewEvent(TimelineEvent event) {
        String label = event instanceof Stay stay ? (stay.getVisitName() == null ? stay.getLocationName() : stay.getVisitName())
                : event instanceof Trip trip ? trip.getTripType().name() : "Data gap";
        return new TimelineEditPreviewDTO.Event(event.getType().name(), event.getStartTime(), event.getEndTime(), label,
                event instanceof Stay stay ? stay.getLocationName() : null);
    }

    private static Stay fromEntity(TimelineStayEntity stay) {
        return Stay.builder().startTime(stay.getTimestamp()).duration(Duration.ofSeconds(stay.getStayDuration()))
                .latitude(stay.getLocation().getY()).longitude(stay.getLocation().getX())
                .locationName(stay.getLocationName()).visitName(stay.getVisitName())
                .favoriteId(stay.getFavoriteLocation() == null ? null : stay.getFavoriteLocation().getId())
                .geocodingId(stay.getGeocodingLocation() == null ? null : stay.getGeocodingLocation().getId()).build();
    }

    private static Trip fromEntity(TimelineTripEntity trip) {
        return Trip.builder().startTime(trip.getTimestamp()).duration(Duration.ofSeconds(trip.getTripDuration()))
                .tripType(TripType.valueOf(trip.getMovementType())).movementTypeSource(trip.getMovementTypeSource())
                .distanceMeters(trip.getDistanceMeters()).manualRoute(trip.getManualRoute())
                .startPoint(GPSPoint.builder().timestamp(trip.getTimestamp()).latitude(trip.getStartPoint().getY())
                        .longitude(trip.getStartPoint().getX()).build())
                .endPoint(GPSPoint.builder().timestamp(trip.getTimestamp().plusSeconds(trip.getTripDuration()))
                        .latitude(trip.getEndPoint().getY()).longitude(trip.getEndPoint().getX()).build()).build();
    }

    private static DataGap fromEntity(TimelineDataGapEntity gap) {
        return DataGap.fromTimeRange(gap.getStartTime(), gap.getEndTime());
    }

    private FavoritesEntity favorite(UUID userId, Long id) {
        if (id == null) return null;
        return favoritesRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeoPulseException(INVALID_TIMELINE_REQUEST,
                        "Favorite place not found or access denied"));
    }

    private ReverseGeocodingLocationEntity geocoding(UUID userId, Long id) {
        if (id == null) return null;
        return entityManager.createQuery("""
                        SELECT location FROM ReverseGeocodingLocationEntity location
                        WHERE location.id = :id AND (location.user IS NULL OR location.user.id = :userId)
                        """, ReverseGeocodingLocationEntity.class)
                .setParameter("id", id).setParameter("userId", userId)
                .getResultStream().findFirst()
                .orElseThrow(() -> new GeoPulseException(INVALID_TIMELINE_REQUEST,
                        "Geocoded place not found or access denied"));
    }

    private static Long longValue(Map<String, Object> payload, String key) {
        return payload.get(key) instanceof Number number ? number.longValue() : null;
    }

    private static Double doubleValue(Map<String, Object> payload, String key) {
        return payload.get(key) instanceof Number number ? number.doubleValue() : null;
    }

    private static String textValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private static void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) payload.putIfAbsent(key, value);
    }

    private record Target(String type, Long id) {
    }
}
