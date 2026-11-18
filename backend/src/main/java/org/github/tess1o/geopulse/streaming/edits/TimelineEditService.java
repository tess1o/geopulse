package org.github.tess1o.geopulse.streaming.edits;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayloads;

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
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEventType;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.edits.payload.BeforeValues;
import org.github.tess1o.geopulse.streaming.edits.payload.ClearPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.MergePayload;
import org.github.tess1o.geopulse.streaming.edits.payload.PlaceStayPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.PlaceTripPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.ResizePayload;
import org.github.tess1o.geopulse.streaming.edits.payload.SetLabelPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.SetPlacePayload;
import org.github.tess1o.geopulse.streaming.edits.payload.SetRoutePayload;
import org.github.tess1o.geopulse.streaming.edits.payload.SetTripTypePayload;
import org.github.tess1o.geopulse.streaming.edits.payload.SplitStayPayload;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditTargetDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineCorrectionEffectEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineCorrectionEffectRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.TimelineUserLock;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_EDIT_CONFLICT;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_EDIT_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_PREVIEW_STALE;

@ApplicationScoped
public class TimelineEditService {
    @Inject org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService generation;
    @Inject ObjectMapper objectMapper;
    @Inject TimelineEditRepository editRepository;
    @Inject TimelineCorrectionEffectRepository effectRepository;
    @Inject TimelineStayRepository stayRepository;
    @Inject TimelineTripRepository tripRepository;
    @Inject TimelineDataGapRepository gapRepository;
    @Inject TimelineRebuildRequestRepository rebuildRequestRepository;
    @Inject FavoritesRepository favoritesRepository;
    @Inject org.github.tess1o.geopulse.trips.service.TripAccessService tripAccess;
    @Inject TimelineEditApplier applier;
    @Inject TimelineUserLock userLock;
    @Inject EntityManager entityManager;
    @Inject GpsPointRepository gpsPoints;
    @Inject TimelineConfigurationProvider configuration;
    @Inject TimelineEventFinalizationService finalization;
    @Inject TripWaterClassificationService waterClassification;
    @Inject org.github.tess1o.geopulse.shared.service.LocationPointResolver locationPointResolver;

    @Transactional
    public List<TimelineEditDTO> list(UUID userId) {
        return editRepository.findByUserForHistory(userId).stream().map(TimelineEditService::toDto).toList();
    }

    @Transactional
    public TimelineEditPreviewDTO preview(UUID userId, TimelineEditRequest request) {
        validateRequest(request);
        widenToNeighbours(userId, request);
        if (request.getReplacesGroupId() != null) validateReplacement(userId, request.getReplacesGroupId());
        var token = previewToken(userId, request);
        var result = previewEdit(userId, request);
        if (!token.equals(previewToken(userId, request))) throw new GeoPulseException(TIMELINE_PREVIEW_STALE, "The timeline changed while it was reviewed. Review the change again.");
        return new TimelineEditPreviewDTO(result.events(), token);
    }

    private List<TimelineEditEntity> checkedConflicts(UUID userId, TimelineEditRequest request) {
        return editRepository.findActiveOverlapping(userId, request.getAffectedFrom(), request.getAffectedTo())
                .stream().filter(existing -> conflicts(existing, request.getKind(), request.getAnchorStart(), request.getAnchorEnd())).toList();
    }

    public List<ActivityCorrectionService.Warning> fillWarnings(UUID userId, List<TimelineEditRequest> commands, UUID replacesGroupId) {
        var result = new ArrayList<ActivityCorrectionService.Warning>();
        Instant from = commands.stream().map(TimelineEditRequest::getAnchorStart).min(Instant::compareTo).orElseThrow();
        Instant to = commands.stream().map(TimelineEditRequest::getAnchorEnd).max(Instant::compareTo).orElseThrow();
        var events = loadCurrentEvents(userId, from, to);
        for (var command : commands) {
            if (!isOwner(command.getKind())) {
                continue;
            }
            for (var event : events) {
                if (!overlaps(event, command.getAnchorStart(), command.getAnchorEnd())) {
                    continue;
                }
                result.add(new ActivityCorrectionService.Warning("CARVES_EVENT", "INFO",
                        "This replaces the " + event.getType().name().toLowerCase(Locale.ROOT) + " in this period.",
                        event.getType().name(), event.getStartTime(), event.getEndTime(), null, null, List.of()));
            }
            if (CorrectionPayloads.tripId(command.getPayload()) == null && command.getAnchorEnd().isAfter(Instant.now())) {
                result.add(new ActivityCorrectionService.Warning("FUTURE_ACTIVITY", "INVALID", "An activity must end in the past.", null, null, null, null, null, List.of("ADJUST_RANGE")));
            }
            gpsPoints.findLatest(userId).ifPresent(latest -> {
                boolean coversOngoing = gapRepository.findByUserIdAndTimeRange(userId, command.getAnchorStart(), command.getAnchorEnd()).stream()
                        .anyMatch(gap -> gap.getStartTime().equals(latest.getTimestamp())
                                && !command.getAnchorStart().isAfter(gap.getStartTime())
                                && !command.getAnchorEnd().isBefore(gap.getEndTime()));
                if (coversOngoing) result.add(new ActivityCorrectionService.Warning("ONGOING_GAP", "INVALID", "Cannot convert an ongoing data gap", "DATA_GAP", command.getAnchorStart(), command.getAnchorEnd(), null, null, List.of("ADJUST_RANGE")));
            });
            for (var edit : editRepository.findActiveAnchorOverlapping(userId, command.getAnchorStart(), command.getAnchorEnd())) {
                if (!isOwner(edit.getKind())
                        || (edit.getGroupId() != null && edit.getGroupId().equals(replacesGroupId))) {
                    continue;
                }
                boolean exact = edit.getAnchorStart().equals(command.getAnchorStart())
                        && edit.getAnchorEnd().equals(command.getAnchorEnd());
                boolean nested = !command.getAnchorStart().isBefore(edit.getAnchorStart())
                        && !command.getAnchorEnd().isAfter(edit.getAnchorEnd());
                boolean contains = !edit.getAnchorStart().isBefore(command.getAnchorStart())
                        && !edit.getAnchorEnd().isAfter(command.getAnchorEnd());
                if (exact || contains) {
                    result.add(new ActivityCorrectionService.Warning("REPLACES_CORRECTION", "CONFIRM",
                            "This activity replaces an earlier correction.",
                            null, edit.getAnchorStart(), edit.getAnchorEnd(),
                            edit.getGroupId(), edit.getId(),
                            List.of("REPLACE_CORRECTION", "ADJUST_RANGE")));
                } else if (nested) {
                    result.add(new ActivityCorrectionService.Warning("CARVES_CORRECTION", "INFO",
                            "This activity is carved out of an earlier correction; the rest of that correction remains.",
                            null, edit.getAnchorStart(), edit.getAnchorEnd(), edit.getGroupId(), edit.getId(), List.of()));
                } else {
                    result.add(new ActivityCorrectionService.Warning("PARTIAL_OVERLAP", "INVALID",
                            "This activity cuts an earlier correction in half. Adjust the range or replace that correction.",
                            null, edit.getAnchorStart(), edit.getAnchorEnd(),
                            edit.getGroupId(), edit.getId(),
                            List.of("ADJUST_RANGE", "REPLACE_CORRECTION")));
                }
            }
        }
        return result;
    }

    private static boolean isOwner(TimelineEditKind kind) {
        return kind == TimelineEditKind.FORCE_STAY || kind == TimelineEditKind.FORCE_TRIP;
    }

    private void validateFillWarnings(UUID userId, TimelineEditRequest request) {
        for (var warning : fillWarnings(userId, List.of(request), request.getReplacesGroupId())) {
            if ("INVALID".equals(warning.severity()) || "REPLACES_CORRECTION".equals(warning.code())) {
                var parameters = new LinkedHashMap<String, Object>();
                if (warning.groupId() != null) parameters.put("conflictingGroupId", warning.groupId());
                if (warning.correctionId() != null) parameters.put("conflictingEditId", warning.correctionId());
                parameters.put("actions", warning.actions());
                throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, warning.message(), parameters);
            }
        }
    }

    /**
     * Everything a review depends on, cheaply: the correction set, the effective settings, the pending rebuild (its
     * revision changes on every GPS write) and the stored events of the reviewed window (they change when a
     * finished regeneration moved something the user is looking at). Hashing every GPS row is not needed: each GPS
     * write records a rebuild request before it can change the timeline.
     */
    public Object previewState(UUID userId, Instant from, Instant to) {
        var state = new LinkedHashMap<String, Object>();
        state.put("corrections", editRepository.findByUser(userId).stream().map(e ->
                java.util.Arrays.asList(e.getId(), e.getSeq(), e.getStatus(), e.getPayload(), e.getBeforeValues(),
                        e.getSupersededBy() == null ? null : e.getSupersededBy().getId())).toList());
        state.put("config", configuration.getConfigurationForUser(userId));
        // Only what changes the reviewed period counts: a pending rebuild that reaches into it (and then every new
        // revision of it), and the points recorded in it. Points recorded after it, as a tracking phone keeps
        // sending, leave the review valid.
        state.put("rebuild", rebuildRequestRepository.find(userId)
                .filter(request -> pendingReaches(userId, request.rebuildFrom(), to))
                .map(request -> List.of(request.rebuildFrom().toString(), request.revision())).orElse(null));
        state.put("points", gpsPoints.fingerprint(userId, from, to));
        state.put("window", loadCurrentEvents(userId, from, to).stream().map(TimelineEditService::fingerprint).toList());
        return state;
    }

    private static List<Object> fingerprint(TimelineEvent event) {
        var values = new ArrayList<Object>(List.of(event.getType().name(), event.getStartTime().toString(),
                event.getEndTime().toString()));
        if (event instanceof Stay stay) {
            values.addAll(java.util.Arrays.asList(stay.getLocationName(), stay.getVisitName(), stay.getFavoriteId(),
                    stay.getGeocodingId(), stay.getLatitude(), stay.getLongitude()));
        } else if (event instanceof Trip trip) {
            values.addAll(java.util.Arrays.asList(trip.getTripType(), trip.getMovementTypeSource(),
                    trip.getDistanceMeters(), trip.getManualRoute() == null ? 0 : trip.getManualRoute().size()));
        }
        return values;
    }

    private String previewToken(UUID userId, TimelineEditRequest request) {
        try {
            var canonical = new LinkedHashMap<String, Object>();
            canonical.put("userId", userId.toString());
            canonical.put("state", previewState(userId, request.getAffectedFrom(), request.getAffectedTo()));
            canonical.put("replacesGroupId", request.getReplacesGroupId());
            canonical.put("kind", request.getKind() == null ? null : request.getKind().name());
            canonical.put("anchorStart", request.getAnchorStart() == null ? null : request.getAnchorStart().toString());
            canonical.put("anchorEnd", request.getAnchorEnd() == null ? null : request.getAnchorEnd().toString());
            canonical.put("affectedFrom", request.getAffectedFrom() == null ? null : request.getAffectedFrom().toString());
            canonical.put("affectedTo", request.getAffectedTo() == null ? null : request.getAffectedTo().toString());
            canonical.put("payload", resolvePayload(userId, request));
            canonical.put("favoriteId", request.getFavoriteId());
            canonical.put("geocodingId", request.getGeocodingId());
            canonical.put("latitude", request.getLatitude());
            canonical.put("longitude", request.getLongitude());
            canonical.put("placeName", request.getPlaceName());
            var bytes = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute the preview token", e);
        }
    }

    TimelineEditPreviewDTO previewEdit(UUID userId, TimelineEditRequest request) {
        TimelineEditResult result = previewCommands(userId, List.of(request), request.getAffectedFrom(), request.getAffectedTo(), request.getReplacesGroupId());
        if (result.unappliedEditIds().contains(0L)) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "The edit does not match a current timeline event");
        }
        return new TimelineEditPreviewDTO(result.events().stream()
                .filter(event -> overlaps(event, request.getAffectedFrom(), request.getAffectedTo()))
                .map(TimelineEditService::previewEvent)
                .toList(), null);
    }

    /**
     * Folds a set of not-yet-persisted edits over the current timeline as one change, so a multi-activity preview
     * shows the same result a rebuild would produce instead of each command's carved copy of the same event.
     */
    TimelineEditResult previewCommands(UUID userId, List<TimelineEditRequest> requests, Instant from, Instant to) {
        return previewCommands(userId, requests, from, to, null);
    }

    TimelineEditResult previewCommands(UUID userId, List<TimelineEditRequest> requests, Instant from, Instant to, UUID replacesGroupId) {
        var commands = new ArrayList<TimelineEditCommand>();
        List<TimelineEvent> baseline = loadCurrentEvents(userId, from, to);
        long seq = 1;
        var existingEdits = editRepository.findByUser(userId);
        // The stored timeline is the review base unless a pending rebuild still changes the reviewed period. A
        // tracking phone keeps a rebuild pending for its newest points, which leaves an older period as stored.
        boolean pending = rebuildRequestRepository.find(userId)
                .filter(request -> pendingReaches(userId, request.rebuildFrom(), to)).isPresent();
        // A restored snapshot without a recording remains the review base until its first rebuild.
        boolean rebuildBase = replacesGroupId != null || (pending && (gpsPoints.findLatest(userId).isPresent()
                || existingEdits.stream().anyMatch(e -> e.getStatus() == TimelineEditStatus.ACTIVE && insertsAtAnchor(e.getKind()))));
        if (rebuildBase) {
            if (replacesGroupId != null && editRepository.findGroup(userId, replacesGroupId).isEmpty())
                throw new GeoPulseException(TIMELINE_EDIT_NOT_FOUND, "Correction not found or access denied");
            // Detect again only from the restart point before the reviewed window up to two stored stays after it,
            // and fold the corrections a generation pass would fold there (see apply): the result for the window is
            // the same, without reading the recording up to today inside a request.
            Instant until = reviewEnd(userId, from, to);
            var detected = generation.detectForCorrections(userId, from, until);
            baseline = detected.events();
            Instant restart = detected.from();
            for (var existing : existingEdits) {
                if (existing.getStatus() == TimelineEditStatus.ACTIVE
                        && !java.util.Objects.equals(replacesGroupId, existing.getGroupId())
                        && existing.getAffectedTo().isAfter(restart)
                        && (until == null || existing.getAffectedFrom().isBefore(until))
                        && (!insertsAtAnchor(existing.getKind()) || !existing.getAnchorStart().isBefore(restart))) {
                    commands.add(command(existing));
                }
                seq = Math.max(seq, existing.getSeq() + 1);
            }
        }
        for (var request : requests) {
            commands.add(new TimelineEditCommand(0L, seq++, request.getKind(), request.getAnchorStart(),
                    request.getAnchorEnd(), resolvePayload(userId, request)));
        }
        var result = applier.apply(baseline, commands, trip -> recalculateTrip(userId, trip));
        if (result.unappliedEditIds().contains(0L)) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "The activities do not match the current timeline");
        }
        return result;
    }

    /** Whether a pending rebuild from {@code rebuildFrom} regenerates anything before {@code to}. */
    private boolean pendingReaches(UUID userId, Instant rebuildFrom, Instant to) {
        // The real-time job restarts two stays back; that is the earliest a pending rebuild changes the timeline.
        Instant restart = generation.findRestartPoint(userId, rebuildFrom,
                        org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService.RestartPoint.BEFORE_PREVIOUS_STAY)
                .orElse(org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService.DEFAULT_START_DATE);
        return restart.isBefore(to);
    }

    /**
     * Where a review of [from, to) can stop reading the recording: the end of the second stored detection stay after
     * the period and after every active correction reaching into it. Null when there are not two such stays, so the
     * review reads to the last point (and sees the ongoing gap).
     */
    Instant reviewEnd(UUID userId, Instant from, Instant to) {
        Instant end = to;
        for (var edit : editRepository.findActiveOverlapping(userId, from, to)) {
            if (edit.getAffectedTo().isAfter(end)) end = edit.getAffectedTo();
        }
        var after = stayRepository.findDetectionStaysFrom(userId, end, 2);
        if (after.size() < 2) return null;
        var last = after.getLast();
        return last.getTimestamp().plusSeconds(last.getStayDuration());
    }

    @Transactional
    public TimelineEditDTO create(UUID userId, TimelineEditRequest request) {
        validateRequest(request);
        widenToNeighbours(userId, request);
        if (!userLock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        if (request.getPreviewToken() != null && !request.getPreviewToken().equals(previewToken(userId, request))) {
            throw new GeoPulseException(TIMELINE_PREVIEW_STALE,
                    "The timeline changed since it was reviewed. Review the change again.");
        }
        var conflicts = checkedConflicts(userId, request);
        if (!request.isPending()) {
            previewEdit(userId, request);
        }

        // A correction depends on the corrections that produced the events it changes: undoing one of those must
        // undo or ask about this one rather than retargeting it. Unrelated corrections nearby are not dependencies.
        var prerequisites = producers(userId, request);
        var beforeValues = captureBeforeValues(userId, request);
        Instant affectedFrom = request.getAffectedFrom();
        Instant affectedTo = request.getAffectedTo();
        // Replay the earlier structural output being carved, without making an independent fill depend on it.
        if (request.getKind().structural()) {
            for (var conflict : conflicts) {
                if (conflict.getAffectedFrom().isBefore(affectedFrom)) affectedFrom = conflict.getAffectedFrom();
                if (conflict.getAffectedTo().isAfter(affectedTo)) affectedTo = conflict.getAffectedTo();
            }
        }
        if (!prerequisites.isEmpty()) {
            if (beforeValues == null) {
                // An inserted event is created at its anchor instead of matching a current event, so it has no before
                // values to remember; a detail edit may also chain on a correction without an exact snapshot.
                if (request.getKind().structural() && !insertsAtAnchor(request.getKind()))
                    throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Select a current event to chain edits");
                beforeValues = BeforeValues.builder().build();
            }
            beforeValues = beforeValues.toBuilder()
                    .prerequisiteSequences(prerequisites.stream().map(TimelineEditEntity::getSeq).toList()).build();
            for (var prerequisite : prerequisites) {
                if (prerequisite.getAffectedFrom().isBefore(affectedFrom)) affectedFrom = prerequisite.getAffectedFrom();
                if (prerequisite.getAffectedTo().isAfter(affectedTo)) affectedTo = prerequisite.getAffectedTo();
            }
        }

        CorrectionPayload payload = resolvePayload(userId, request);
        PlaceResolved place = mainPlace(payload);
        FavoritesEntity favorite = place == null || place.favoriteId() == null
                ? null : favorite(userId, place.favoriteId());
        ReverseGeocodingLocationEntity geocoding = place == null || place.geocodingId() == null
                ? null : geocoding(userId, place.geocodingId());
        TimelineEditEntity entity = TimelineEditEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .groupId(UUID.randomUUID())
                .seq(editRepository.nextSeq(userId))
                .kind(request.getKind())
                .anchorStart(request.getAnchorStart())
                .anchorEnd(request.getAnchorEnd())
                .affectedFrom(affectedFrom)
                .affectedTo(affectedTo)
                .payload(payload)
                .payloadVersion(1)
                .beforeValues(beforeValues)
                .placeFavorite(favorite)
                .placeGeocoding(geocoding)
                .placeLatitude(place == null ? null : place.latitude())
                .placeLongitude(place == null ? null : place.longitude())
                .placeName(place == null ? null : place.locationName())
                .status(TimelineEditStatus.ACTIVE)
                .build();
        editRepository.persist(entity);
        entityManager.flush();
        // Structural corrections compose in sequence; only a same-field modifier replaces its predecessor.
        for (TimelineEditEntity conflict : request.getKind().structural() ? List.<TimelineEditEntity>of() : conflicts) {
            requireNoDependent(userId, conflict);
            conflict.setStatus(TimelineEditStatus.SUPERSEDED);
            conflict.setSupersededBy(entity);
            effectRepository.deleteForCorrection(conflict.getId());
        }
        rebuildRequestRepository.requestWindow(userId, affectedFrom, affectedTo);
        return toDto(entity);
    }

    /**
     * Saves one event correction as its own group through the unified corrections API. A group is the unit of undo,
     * restore and replacement even for a single correction.
     */
    @Transactional
    public ActivityCorrectionService.Saved saveGrouped(UUID userId, TimelineEditRequest request) {
        if (!userLock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        validateRequest(request);
        widenToNeighbours(userId, request);
        validateFillWarnings(userId, request);
        if (request.getPreviewToken() != null && !request.getPreviewToken().equals(previewToken(userId, request)))
            throw new GeoPulseException(TIMELINE_PREVIEW_STALE, "The timeline changed since it was reviewed. Review the change again.");
        request.setPreviewToken(null);
        List<TimelineEditEntity> replaced = request.getReplacesGroupId() == null
                ? List.of() : supersedeGroup(userId, request.getReplacesGroupId());
        TimelineEditDTO created = create(userId, request);
        if (!replaced.isEmpty()) {
            linkReplacement(replaced, created.id());
        }
        return new ActivityCorrectionService.Saved(created.groupId(), List.of(created), created.affectedFrom());
    }

    @Transactional
    public Optional<TimelineEditDTO> revert(UUID userId, Long editId) {
        return revert(userId, editId, false);
    }

    @Transactional
    public Optional<TimelineEditDTO> revert(UUID userId, Long editId, boolean cascade) {
        if (!userLock.tryLock(userId)) throw new TimelineGenerationLockException(userId);
        Optional<TimelineEditEntity> found = editRepository.findByIdAndUser(editId, userId);
        found.ifPresent(edit -> {
            if (edit.getStatus() == TimelineEditStatus.SUPERSEDED) {
                if (edit.getSupersededBy() != null) {
                    throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, "Undo the replacement first",
                            Map.of("conflictingEditId", edit.getSupersededBy().getId()));
                }
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Only an active correction can be undone");
            }
            if (edit.getStatus() != TimelineEditStatus.ACTIVE) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Only an active correction can be undone");
            }
            var dependents = activeDependents(userId, edit);
            if (!cascade && !dependents.isEmpty()) {
                var dependent = dependents.getFirst();
                throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, "Undo the later correction first",
                        Map.of("conflictingEditId", dependent.getId()));
            }
            var before = edit.getBeforeValues() == null ? BeforeValues.builder().build() : edit.getBeforeValues();
            edit.setBeforeValues(before.toBuilder().cascadeUndoneSequences(dependents.stream().map(TimelineEditEntity::getSeq).toList()).build());
            Instant from = edit.getAffectedFrom();
            Instant to = edit.getAffectedTo();
            for (var dependent : dependents.reversed()) {
                dependent.setStatus(TimelineEditStatus.REVERTED);
                effectRepository.deleteForCorrection(dependent.getId());
                if (dependent.getAffectedFrom().isBefore(from)) from = dependent.getAffectedFrom();
                if (dependent.getAffectedTo().isAfter(to)) to = dependent.getAffectedTo();
            }
            edit.setStatus(TimelineEditStatus.REVERTED);
            effectRepository.deleteForCorrection(edit.getId());
            rebuildRequestRepository.requestWindow(userId, from, to);
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
            for (var predecessor : editRepository.find("user.id = ?1 and supersededBy.id = ?2", userId, editId).list()) {
                if (predecessor.getStatus() == TimelineEditStatus.ACTIVE) {
                    predecessor.setStatus(TimelineEditStatus.SUPERSEDED);
                    effectRepository.deleteForCorrection(predecessor.getId());
                }
            }
            var activeEdits = editRepository.findActiveOverlapping(userId, edit.getAffectedFrom(), edit.getAffectedTo());
            var prerequisites = prerequisiteSequences(edit);
            if (!activeEdits.stream().map(TimelineEditEntity::getSeq).toList().containsAll(prerequisites)) {
                throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, "Restore the earlier corrections before restoring this edit");
            }
            Optional<TimelineEditEntity> conflict = activeEdits.stream()
                    .filter(active -> active.getId() != null && !active.getId().equals(edit.getId()))
                    // Earlier resizes, clears and merges were part of the valid state when this edit was saved. An
                    // earlier fill may have been undone at that time, so fills are always checked against fills.
                    .filter(active -> active.getSeq() > edit.getSeq()
                            || (insertsAtAnchor(edit.getKind()) && insertsAtAnchor(active.getKind())))
                    .filter(active -> !prerequisites.contains(active.getSeq()) && restoreConflicts(active, edit))
                    .findFirst();
            if (conflict.isPresent()) {
                throw new GeoPulseException(TIMELINE_EDIT_CONFLICT,
                        "A newer timeline edit conflicts with this restore",
                        Map.of("conflictingEditId", conflict.get().getId()));
            }
            edit.setStatus(TimelineEditStatus.ACTIVE);
            edit.setSupersededBy(null);
            rebuildRequestRepository.requestWindow(userId, edit.getAffectedFrom(), edit.getAffectedTo());
        });
        return found.map(TimelineEditService::toDto);
    }

    /**
     * Marks a whole correction group as replaced by a newer correction. The old group stays in history and can be
     * restored by undoing the replacement. Later corrections that depend on the old group block the replacement
     * unless they are undone first, the same rule as undo.
     */
    @Transactional
    public List<TimelineEditEntity> validateReplacement(UUID userId, UUID groupId) {
        var group = editRepository.findGroup(userId, groupId);
        if (group.isEmpty()) {
            throw new GeoPulseException(TIMELINE_EDIT_NOT_FOUND, "Correction not found or access denied");
        }
        for (var edit : group) {
            if (edit.getStatus() != TimelineEditStatus.ACTIVE) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Only an active correction can be replaced");
            }
            var dependents = activeDependents(userId, edit).stream().filter(e -> !groupId.equals(e.getGroupId())).toList();
            if (!dependents.isEmpty()) throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, "Undo the later correction first", Map.of("conflictingEditId", dependents.getFirst().getId()));
        }
        return group;
    }

    @Transactional
    public List<TimelineEditEntity> supersedeGroup(UUID userId, UUID groupId) {
        var group = validateReplacement(userId, groupId);
        for (var edit : group) {
            edit.setStatus(TimelineEditStatus.SUPERSEDED);
            effectRepository.deleteForCorrection(edit.getId());
            rebuildRequestRepository.requestWindow(userId, edit.getAffectedFrom(), edit.getAffectedTo());
        }
        return group;
    }

    /** Links a superseded group to the correction that replaced it, for history and restore navigation. */
    @Transactional
    public void linkReplacement(List<TimelineEditEntity> group, Long replacementEditId) {
        var replacement = replacementEditId == null ? null : editRepository.findById(replacementEditId);
        for (var edit : group) {
            edit.setSupersededBy(replacement);
        }
    }

    /** Reactivates replaced corrections when the replacement group is undone. */
    @Transactional
    public void reactivateReplacedBy(UUID userId, List<Long> revertedEditIds) {
        if (revertedEditIds.isEmpty()) {
            return;
        }
        for (var edit : editRepository.find("user.id = ?1 and supersededBy.id in ?2",
                userId, revertedEditIds).list()) {
            if (edit.getStatus() == TimelineEditStatus.SUPERSEDED) {
                edit.setStatus(TimelineEditStatus.ACTIVE);
                rebuildRequestRepository.requestWindow(userId, edit.getAffectedFrom(), edit.getAffectedTo());
            }
        }
    }

    private void requireNoDependent(UUID userId, TimelineEditEntity edit) {
        var dependents = activeDependents(userId, edit);
        if (!dependents.isEmpty()) {
            throw new GeoPulseException(TIMELINE_EDIT_CONFLICT, "Undo the later correction first",
                    Map.of("conflictingEditId", dependents.getFirst().getId()));
        }
    }

    /**
     * The active edits that cannot be replayed without this one, oldest first. Group members are pulled in whole,
     * because a correction group is replayed and undone as one unit.
     */
    private List<TimelineEditEntity> activeDependents(UUID userId, TimelineEditEntity edit) {
        List<TimelineEditEntity> active = editRepository.findByUser(userId).stream()
                .filter(candidate -> candidate.getStatus() == TimelineEditStatus.ACTIVE).toList();
        var dependents = new LinkedHashMap<Long, TimelineEditEntity>();
        var sequences = new HashSet<Long>();
        sequences.add(edit.getSeq());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var candidate : active) {
                if (candidate.getId().equals(edit.getId()) || dependents.containsKey(candidate.getId())) continue;
                if (prerequisiteSequences(candidate).stream().noneMatch(sequences::contains)) continue;
                changed = addWithGroup(active, edit.getId(), candidate, dependents, sequences) || changed;
            }
        }
        return dependents.values().stream().sorted(Comparator.comparingLong(TimelineEditEntity::getSeq)).toList();
    }

    private static boolean addWithGroup(List<TimelineEditEntity> active, Long rootId, TimelineEditEntity candidate,
                                        Map<Long, TimelineEditEntity> dependents, Set<Long> sequences) {
        boolean added = false;
        var members = candidate.getGroupId() == null ? List.of(candidate)
                : active.stream().filter(member -> candidate.getGroupId().equals(member.getGroupId())).toList();
        for (var member : members) {
            if (member.getId().equals(rootId)) continue;
            if (dependents.putIfAbsent(member.getId(), member) == null) added = true;
            sequences.add(member.getSeq());
        }
        return added;
    }

    private static List<Long> prerequisiteSequences(TimelineEditEntity edit) {
        return edit.getBeforeValues() == null || edit.getBeforeValues().prerequisiteSequences() == null
                ? List.of() : edit.getBeforeValues().prerequisiteSequences();
    }

    /**
     * End of the last active fill. A fill inserts its event at its anchor, so with no detected events the fold still
     * produces it.
     */
    public Instant activeEnd(UUID userId) {
        return editRepository.maxActiveAffectedTo(userId, List.of(TimelineEditKind.FORCE_STAY, TimelineEditKind.FORCE_TRIP));
    }

    public TimelineEditResult apply(UUID userId, List<TimelineEvent> events, Instant from, Instant to) {
        List<TimelineEditCommand> commands = editRepository.findActiveOverlapping(userId, from, to).stream()
                // A forced event is created at its anchor without matching an existing event. When the regeneration
                // starts after that anchor, the copy already persisted lies before "from" and outlives the pass, so
                // inserting another would duplicate it. Its end may lie after "to": nothing generated exists there,
                // and the edit itself defines how far the event reaches.
                .filter(edit -> !insertsAtAnchor(edit.getKind()) || !edit.getAnchorStart().isBefore(from))
                .map(this::command)
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

    /** The active corrections whose affected range overlaps [from, to). */
    public List<TimelineEditEntity> activeOverlapping(UUID userId, Instant from, Instant to) {
        return editRepository.findActiveOverlapping(userId, from, to);
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
    public void clearMissingEffects(UUID userId) {
        effectRepository.deleteMissingTargets(userId);
    }

    /**
     * Rebuilds the effects projection for the corrections this pass considered. A correction that contributed
     * nothing loses its old row only when this pass covered its whole affected range; a correction reaching before
     * the pass may still target a persisted event outside the regenerated window. Effects outside the window are
     * left untouched, and stale targets whose rows disappear are cleared after persistence.
     */
    @Transactional
    public void recordEffects(UUID userId, TimelineEditResult result, Instant windowFrom) {
        // A regeneration may fold thousands of corrections: every step here is a fixed number of statements.
        var cleared = new ArrayList<>(result.appliedEditIds());
        cleared.addAll(editRepository.findIdsAffectedFrom(result.unappliedEditIds(), windowFrom));
        entityManager.flush();
        effectRepository.deleteForCorrections(cleared);
        var targets = persistedTargets(userId, result.effects().values().stream().flatMap(List::stream).toList());
        var rows = new ArrayList<TimelineCorrectionEffectRepository.EffectRow>();
        result.effects().forEach((editId, events) -> events.forEach(event -> {
            Target target = targets.get(event);
            if (target != null) rows.add(new TimelineCorrectionEffectRepository.EffectRow(editId, target.type(), target.id()));
        }));
        effectRepository.insertAll(rows);
    }

    /**
     * The persisted row of each event, matched exactly as {@link #findPersistedTarget} does, with one query per event
     * type over the span of the given events instead of one query per event.
     */
    private Map<TimelineEvent, Target> persistedTargets(UUID userId, List<TimelineEvent> events) {
        Map<TimelineEvent, Target> targets = new java.util.IdentityHashMap<>();
        if (events.isEmpty()) return targets;
        Instant first = events.stream().map(TimelineEvent::getStartTime).min(Instant::compareTo).orElseThrow();
        Instant last = events.stream().map(TimelineEvent::getStartTime).max(Instant::compareTo).orElseThrow();
        Map<List<Object>, Long> stays = new java.util.HashMap<>();
        Map<List<Object>, Long> trips = new java.util.HashMap<>();
        Map<List<Object>, Long> gaps = new java.util.HashMap<>();
        if (events.stream().anyMatch(Stay.class::isInstance)) {
            entityManager.createQuery("""
                            select s.id, s.timestamp, s.stayDuration from TimelineStayEntity s
                            where s.user.id = :userId and s.timestamp between :first and :last
                            order by s.timestamp, s.id""", Object[].class)
                    .setParameter("userId", userId).setParameter("first", first).setParameter("last", last)
                    .getResultList()
                    .forEach(row -> stays.putIfAbsent(List.of(row[1], row[2]), (Long) row[0]));
        }
        if (events.stream().anyMatch(Trip.class::isInstance)) {
            entityManager.createQuery("""
                            select t.id, t.timestamp, t.tripDuration from TimelineTripEntity t
                            where t.user.id = :userId and t.timestamp between :first and :last
                            order by t.timestamp, t.id""", Object[].class)
                    .setParameter("userId", userId).setParameter("first", first).setParameter("last", last)
                    .getResultList()
                    .forEach(row -> trips.putIfAbsent(List.of(row[1], row[2]), (Long) row[0]));
        }
        if (events.stream().anyMatch(DataGap.class::isInstance)) {
            entityManager.createQuery("""
                            select g.id, g.startTime, g.endTime from TimelineDataGapEntity g
                            where g.user.id = :userId and g.startTime between :first and :last
                            order by g.startTime, g.id""", Object[].class)
                    .setParameter("userId", userId).setParameter("first", first).setParameter("last", last)
                    .getResultList()
                    .forEach(row -> gaps.putIfAbsent(List.of(row[1], row[2]), (Long) row[0]));
        }
        for (TimelineEvent event : events) {
            Long id;
            String type;
            if (event instanceof Stay) {
                id = stays.get(List.of(event.getStartTime(), event.getDuration().getSeconds()));
                type = "STAY";
            } else if (event instanceof Trip) {
                id = trips.get(List.of(event.getStartTime(), event.getDuration().getSeconds()));
                type = "TRIP";
            } else {
                id = gaps.get(List.of(event.getStartTime(), event.getEndTime()));
                type = "DATA_GAP";
            }
            if (id != null) targets.put(event, new Target(type, id));
        }
        return targets;
    }

    public static TimelineEditDTO toDto(TimelineEditEntity entity) {
        TimelineCorrectionEffectEntity effect = entity.getEffects().isEmpty() ? null : entity.getEffects().getFirst();
        TimelineEditEntity replacedBy = entity.getStatus() == TimelineEditStatus.SUPERSEDED ? entity.getSupersededBy() : null;
        return TimelineEditDTO.builder()
                .id(entity.getId()).groupId(entity.getGroupId()).seq(entity.getSeq())
                .op(TimelineCorrectionOp.of(entity.getKind()))
                .anchorStart(entity.getAnchorStart()).anchorEnd(entity.getAnchorEnd())
                .affectedFrom(entity.getAffectedFrom()).affectedTo(entity.getAffectedTo())
                .payloadVersion(entity.getPayloadVersion())
                .payload(entity.getPayload()).beforeValues(entity.getBeforeValues())
                .status(TimelineCorrectionStatus.of(entity.getStatus()))
                .applied(effect != null)
                .target(effect == null ? null : new TimelineEditTargetDTO(
                        effect.getTargetType(), effect.getTargetId()))
                .effects(entity.getEffects().stream().map(e -> new TimelineEditTargetDTO(e.getTargetType(), e.getTargetId())).toList())
                .replacedByGroupId(replacedBy == null ? null : replacedBy.getGroupId())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * A resize trims its neighbours, a merge may swallow one event in between, and a removal extends, merges or
     * dismisses next to its neighbours. The affected range covers those neighbours whatever range the client sent, so
     * preview, rebuild and replay all see the same events.
     */
    private void widenToNeighbours(UUID userId, TimelineEditRequest request) {
        int count = switch (request.getKind()) {
            case SET_TIMES, REMOVE_EVENT -> 1;
            case MERGE -> 2;
            default -> 0;
        };
        if (count == 0) return;
        for (TimelineEvent event : neighbours(userId, request.getAnchorStart(), request.getAnchorEnd(), count)) {
            if (event.getStartTime().isBefore(request.getAffectedFrom())) request.setAffectedFrom(event.getStartTime());
            if (event.getEndTime().isAfter(request.getAffectedTo())) request.setAffectedTo(event.getEndTime());
        }
    }

    /** Up to {@code count} stored events that end at or before {@code from}, and as many starting at or after {@code to}. */
    private List<TimelineEvent> neighbours(UUID userId, Instant from, Instant to, int count) {
        var before = new ArrayList<TimelineEvent>();
        stayRepository.find("user.id = ?1 and timestamp < ?2 order by timestamp desc", userId, from)
                .page(0, count).list().forEach(stay -> before.add(fromEntity(stay)));
        tripRepository.find("user.id = ?1 and timestamp < ?2 order by timestamp desc", userId, from)
                .page(0, count).list().forEach(trip -> before.add(fromEntity(trip)));
        gapRepository.find("user.id = ?1 and startTime < ?2 order by startTime desc", userId, from)
                .page(0, count).list().forEach(gap -> before.add(fromEntity(gap)));
        var after = new ArrayList<TimelineEvent>();
        stayRepository.find("user.id = ?1 and timestamp >= ?2 order by timestamp", userId, to)
                .page(0, count).list().forEach(stay -> after.add(fromEntity(stay)));
        tripRepository.find("user.id = ?1 and timestamp >= ?2 order by timestamp", userId, to)
                .page(0, count).list().forEach(trip -> after.add(fromEntity(trip)));
        gapRepository.find("user.id = ?1 and startTime >= ?2 order by startTime", userId, to)
                .page(0, count).list().forEach(gap -> after.add(fromEntity(gap)));
        var result = new ArrayList<TimelineEvent>();
        before.stream().filter(event -> !event.getEndTime().isAfter(from))
                .sorted(Comparator.comparing(TimelineEvent::getStartTime).reversed())
                .limit(count).forEach(result::add);
        after.stream().filter(event -> !(event.getStartTime().equals(from) && event.getEndTime().equals(to)))
                .sorted(Comparator.comparing(TimelineEvent::getStartTime).thenComparing(TimelineEvent::getEndTime))
                .limit(count).forEach(result::add);
        return result;
    }

    /**
     * The in-effect structural corrections that produced the events this correction changes, read from the effects
     * projection of the stored timeline. Fills and removals act on time and consume no earlier output.
     */
    private List<TimelineEditEntity> producers(UUID userId, TimelineEditRequest request) {
        if (insertsAtAnchor(request.getKind()) || request.getKind() == TimelineEditKind.REMOVE_EVENT) {
            return List.of();
        }
        var command = new TimelineEditCommand(0L, 0, request.getKind(), request.getAnchorStart(),
                request.getAnchorEnd(), resolvePayload(userId, request));
        var events = loadCurrentEvents(userId, request.getAffectedFrom(), request.getAffectedTo());
        var producers = new LinkedHashMap<Long, TimelineEditEntity>();
        for (TimelineEvent event : TimelineEditApplier.consumedEvents(events, command)) {
            findPersistedTarget(userId, event).ifPresent(target -> effectRepository
                    .findForTargets(target.type(), List.of(target.id())).stream()
                    .map(TimelineCorrectionEffectEntity::getCorrection)
                    .filter(edit -> edit.getStatus() == TimelineEditStatus.ACTIVE && edit.getKind().structural())
                    .forEach(edit -> producers.putIfAbsent(edit.getId(), edit)));
        }
        return producers.values().stream().sorted(Comparator.comparingLong(TimelineEditEntity::getSeq)).toList();
    }

    private BeforeValues captureBeforeValues(UUID userId, TimelineEditRequest request) {
        return loadCurrentEvents(userId, request.getAnchorStart(), request.getAnchorEnd()).stream()
                .filter(event -> event.getStartTime().equals(request.getAnchorStart())
                        && event.getEndTime().equals(request.getAnchorEnd()))
                .findFirst().map(event -> {
                    BeforeValues.BeforeValuesBuilder values = BeforeValues.builder()
                            .type(event.getType()).start(event.getStartTime()).end(event.getEndTime());
                    if (event instanceof Stay stay) {
                        values.visitName(stay.getVisitName()).locationName(stay.getLocationName())
                                .favoriteId(stay.getFavoriteId()).geocodingId(stay.getGeocodingId())
                                .latitude(stay.getLatitude()).longitude(stay.getLongitude());
                    } else if (event instanceof Trip trip) {
                        values.movementType(trip.getTripType())
                                .startLatitude(trip.getStartPoint().getLatitude())
                                .startLongitude(trip.getStartPoint().getLongitude())
                                .endLatitude(trip.getEndPoint().getLatitude())
                                .endLongitude(trip.getEndPoint().getLongitude());
                    }
                    return values.build();
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

    private TimelineEditCommand command(TimelineEditEntity entity) {
        CorrectionPayload payload = placeForCommand(entity);
        TimelineEventType sourceType = sourceType(entity.getBeforeValues());
        return new TimelineEditCommand(entity.getId(), entity.getSeq(), entity.getKind(),
                entity.getAnchorStart(), entity.getAnchorEnd(), payload, sourceType, prerequisiteSequences(entity));
    }

    private static TimelineEventType sourceType(BeforeValues before) {
        if (before == null) return null;
        if (before.type() != null) return before.type();
        // Rows written before before-values carried a type usually still have one of these fields.
        if (before.movementType() != null) return TimelineEventType.TRIP;
        if (before.locationName() != null) return TimelineEventType.STAY;
        return null;
    }

    /**
     * Rebuilds the payload of a stored correction against the current place references. A referenced place that was
     * renamed is reflected, a deleted reference is resolved again from the stored coordinates, and a custom name
     * survives either way.
     */
    private CorrectionPayload placeForCommand(TimelineEditEntity entity) {
        CorrectionPayload payload = CorrectionPayloads.require(entity.getKind(), entity.getPayload());
        if (payload instanceof PlaceStayPayload stay) {
            return stayForCommand(entity, stay);
        }
        if (payload instanceof SetPlacePayload place) {
            return setPlaceForCommand(entity, place);
        }
        return payload;
    }

    private PlaceStayPayload stayForCommand(TimelineEditEntity entity, PlaceStayPayload stay) {
        FavoritesEntity favorite = entity.getPlaceFavorite();
        ReverseGeocodingLocationEntity geocoding = entity.getPlaceGeocoding();
        boolean hadReference = stay.favoriteId() != null || stay.geocodingId() != null;
        boolean customName = Boolean.TRUE.equals(stay.locationNameCustom());
        String locationName = stay.locationName();
        if (!customName) {
            if (favorite != null) {
                locationName = favorite.getName();
            } else if (geocoding != null) {
                locationName = geocoding.getDisplayName();
            } else {
                if (locationName == null) locationName = entity.getPlaceName();
                // The place a correction referenced may be gone. Resolve its coordinates again, as a GPS stay
                // would, instead of leaving a dangling reference. A coordinate-only stay keeps the name it was
                // given.
                if (entity.getKind() == TimelineEditKind.FORCE_STAY && hadReference) {
                    Double latitude = stay.latitude() != null ? stay.latitude() : entity.getPlaceLatitude();
                    Double longitude = stay.longitude() != null ? stay.longitude() : entity.getPlaceLongitude();
                    if (latitude != null && longitude != null) {
                        var resolved = locationPointResolver.resolveLocationWithReferences(entity.getUser().getId(),
                                GeoUtils.createPoint(longitude, latitude));
                        return stay.toBuilder()
                                .favoriteId(resolved.getFavoriteId()).geocodingId(resolved.getGeocodingId())
                                .locationName(resolved.getLocationName() != null
                                        ? resolved.getLocationName() : locationName)
                                .build();
                    }
                }
            }
        }
        return stay.toBuilder()
                .favoriteId(favorite == null ? null : favorite.getId())
                .geocodingId(geocoding == null ? null : geocoding.getId())
                .locationName(locationName)
                .build();
    }

    private SetPlacePayload setPlaceForCommand(TimelineEditEntity entity, SetPlacePayload place) {
        FavoritesEntity favorite = entity.getPlaceFavorite();
        ReverseGeocodingLocationEntity geocoding = entity.getPlaceGeocoding();
        String locationName = place.locationName();
        if (favorite != null) {
            locationName = favorite.getName();
        } else if (geocoding != null) {
            locationName = geocoding.getDisplayName();
        } else if (locationName == null) {
            locationName = entity.getPlaceName();
        }
        return place.toBuilder()
                .favoriteId(favorite == null ? null : favorite.getId())
                .geocodingId(geocoding == null ? null : geocoding.getId())
                .locationName(locationName)
                .build();
    }

    public void validateImported(TimelineEditEntity edit) {
        var request = new TimelineEditRequest();
        request.setKind(edit.getKind()); request.setAnchorStart(edit.getAnchorStart()); request.setAnchorEnd(edit.getAnchorEnd());
        request.setAffectedFrom(edit.getAffectedFrom()); request.setAffectedTo(edit.getAffectedTo()); request.setPayload(edit.getPayload());
        request.setLatitude(edit.getPlaceLatitude()); request.setLongitude(edit.getPlaceLongitude()); request.setPlaceName(edit.getPlaceName());
        validateRequest(request);
        resolvePayload(edit.getUser().getId(), request);
    }

    public CorrectionPayload resolvePayload(UUID userId, TimelineEditRequest request) {
        CorrectionPayload supplied = request.getPayload();
        Long tripId = CorrectionPayloads.tripId(supplied);
        if (tripId != null) {
            var trip = tripAccess.requireOwnerAccess(userId, tripId).trip();
            if (trip.getStartTime() == null || trip.getEndTime() == null
                    || request.getAnchorStart().isBefore(trip.getStartTime())
                    || request.getAnchorEnd().isAfter(trip.getEndTime()))
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Activities must be within the planned trip dates");
        }
        return switch (request.getKind()) {
            case FORCE_STAY -> resolvePlaceStay(userId, request, supplied);
            case FORCE_TRIP -> resolvePlaceTrip(userId, supplied);
            case SET_PLACE -> resolveSetPlace(userId, request, supplied);
            case SET_VISIT_NAME -> labelPayload(supplied);
            case SET_MOVEMENT_TYPE -> tripTypePayload(supplied);
            case SET_TIMES -> resizePayload(supplied);
            case SET_ROUTE -> routePayload(supplied);
            case SPLIT_STAY -> resolveSplitStay(userId, supplied);
            case MERGE -> mergePayload(supplied);
            case REMOVE_EVENT -> clearPayload(supplied);
        };
    }

    private PlaceStayPayload resolvePlaceStay(UUID userId, TimelineEditRequest request, CorrectionPayload supplied) {
        PlaceStayPayload payload = supplied instanceof PlaceStayPayload stay ? stay : PlaceStayPayload.builder().build();
        boolean customName = Boolean.TRUE.equals(payload.locationNameCustom());
        PlaceResolved place = resolvePlace(userId,
                firstNonNull(payload.favoriteId(), request.getFavoriteId()),
                firstNonNull(payload.geocodingId(), request.getGeocodingId()),
                firstNonNull(payload.latitude(), request.getLatitude()),
                firstNonNull(payload.longitude(), request.getLongitude()),
                firstNonNull(payload.locationName(), request.getPlaceName()), customName);
        if (place.latitude() == null || place.longitude() == null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose a place with valid coordinates");
        }
        return payload.toBuilder()
                .favoriteId(place.favoriteId()).geocodingId(place.geocodingId())
                .latitude(place.latitude()).longitude(place.longitude()).locationName(place.locationName())
                .build();
    }

    private SetPlacePayload resolveSetPlace(UUID userId, TimelineEditRequest request, CorrectionPayload supplied) {
        SetPlacePayload payload = supplied instanceof SetPlacePayload place ? place : SetPlacePayload.builder().build();
        PlaceResolved place = resolvePlace(userId,
                firstNonNull(payload.favoriteId(), request.getFavoriteId()),
                firstNonNull(payload.geocodingId(), request.getGeocodingId()),
                firstNonNull(payload.latitude(), request.getLatitude()),
                firstNonNull(payload.longitude(), request.getLongitude()),
                firstNonNull(payload.locationName(), request.getPlaceName()), false);
        if (place.latitude() == null || place.longitude() == null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose a place with valid coordinates");
        }
        return payload.toBuilder()
                .favoriteId(place.favoriteId()).geocodingId(place.geocodingId())
                .latitude(place.latitude()).longitude(place.longitude()).locationName(place.locationName())
                .build();
    }

    private PlaceTripPayload resolvePlaceTrip(UUID userId, CorrectionPayload supplied) {
        PlaceTripPayload payload = supplied instanceof PlaceTripPayload trip ? trip
                : PlaceTripPayload.builder().build();
        PlaceResolved start = resolvePlace(userId, payload.startFavoriteId(), payload.startGeocodingId(),
                payload.startLatitude(), payload.startLongitude(), payload.startLocationName(), false);
        PlaceResolved end = resolvePlace(userId, payload.endFavoriteId(), payload.endGeocodingId(),
                payload.endLatitude(), payload.endLongitude(), payload.endLocationName(), false);
        if (start.latitude() == null || start.longitude() == null
                || end.latitude() == null || end.longitude() == null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose both trip endpoints");
        }
        if (Boolean.TRUE.equals(payload.activityCorrection()) || payload.waypoints() != null) {
            ManualRouteGeometry.validate(payload.waypoints());
        }
        return payload.toBuilder()
                .activityCorrection(Boolean.TRUE.equals(payload.activityCorrection()) || payload.waypoints() != null)
                .startFavoriteId(start.favoriteId()).startGeocodingId(start.geocodingId())
                .startLatitude(start.latitude()).startLongitude(start.longitude())
                .startLocationName(start.locationName())
                .endFavoriteId(end.favoriteId()).endGeocodingId(end.geocodingId())
                .endLatitude(end.latitude()).endLongitude(end.longitude()).endLocationName(end.locationName())
                .build();
    }

    private SplitStayPayload resolveSplitStay(UUID userId, CorrectionPayload supplied) {
        SplitStayPayload payload = supplied instanceof SplitStayPayload split ? split
                : SplitStayPayload.builder().build();
        if (payload.splitAt() == null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose where the visit was split");
        }
        PlaceResolved right = resolvePlace(userId, payload.rightFavoriteId(), payload.rightGeocodingId(),
                payload.rightLatitude(), payload.rightLongitude(), payload.rightLocationName(), false);
        return payload.toBuilder()
                .rightFavoriteId(right.favoriteId()).rightGeocodingId(right.geocodingId())
                .rightLatitude(right.latitude()).rightLongitude(right.longitude())
                .rightLocationName(right.locationName())
                .build();
    }

    private static SetLabelPayload labelPayload(CorrectionPayload supplied) {
        if (!(supplied instanceof SetLabelPayload label) || label.visitName() == null || label.visitName().length() > 500) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Visit name is required");
        }
        return new SetLabelPayload(label.visitName().trim());
    }

    private static SetTripTypePayload tripTypePayload(CorrectionPayload supplied) {
        if (!(supplied instanceof SetTripTypePayload type) || type.movementType() == null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose a movement type");
        }
        return type;
    }

    private static ResizePayload resizePayload(CorrectionPayload supplied) {
        if (!(supplied instanceof ResizePayload resize) || resize.start() == null || resize.end() == null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose a start and an end");
        }
        return resize;
    }

    private static SetRoutePayload routePayload(CorrectionPayload supplied) {
        if (!(supplied instanceof SetRoutePayload route)) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose a route");
        }
        ManualRouteGeometry.validate(route.waypoints());
        return route;
    }

    private static MergePayload mergePayload(CorrectionPayload supplied) {
        if (!(supplied instanceof MergePayload merge) || merge.direction() == null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose a merge direction");
        }
        return merge;
    }

    private static ClearPayload clearPayload(CorrectionPayload supplied) {
        if (!(supplied instanceof ClearPayload clear) || clear.resolution() == null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Choose how the removed time is filled");
        }
        return clear;
    }

    /** Resolves a place reference or coordinate snapshot, validating ownership and coordinates. */
    private PlaceResolved resolvePlace(UUID userId, Long favoriteId, Long geocodingId, Double latitude,
                                       Double longitude, String locationName, boolean customName) {
        if (favoriteId != null && geocodingId != null) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST,
                    "A place edit cannot reference both a favorite and a geocoded place");
        }
        FavoritesEntity favorite = favorite(userId, favoriteId);
        ReverseGeocodingLocationEntity geocoding = geocoding(userId, geocodingId);
        Long resolvedFavorite = null;
        Long resolvedGeocoding = null;
        String resolvedName = locationName;
        Double resolvedLatitude = latitude;
        Double resolvedLongitude = longitude;
        if (favorite != null) {
            resolvedFavorite = favorite.getId();
            if (!customName) resolvedName = favorite.getName();
            if (favorite.getGeometry() != null && !favorite.getGeometry().isEmpty()) {
                var point = favorite.getGeometry().getInteriorPoint();
                resolvedLatitude = point.getY();
                resolvedLongitude = point.getX();
            }
        } else if (geocoding != null) {
            resolvedGeocoding = geocoding.getId();
            if (!customName) resolvedName = geocoding.getDisplayName();
            var point = geocoding.getResultCoordinates() != null
                    ? geocoding.getResultCoordinates() : geocoding.getRequestCoordinates();
            resolvedLatitude = point.getY();
            resolvedLongitude = point.getX();
        }
        if (resolvedLatitude != null || resolvedLongitude != null) {
            if (resolvedLatitude == null || resolvedLongitude == null) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "A place requires both coordinates");
            }
            try {
                GeoUtils.createPoint(resolvedLongitude, resolvedLatitude);
            } catch (IllegalArgumentException invalid) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Place coordinates must be valid", invalid);
            }
        }
        return new PlaceResolved(resolvedFavorite, resolvedGeocoding, resolvedLatitude, resolvedLongitude, resolvedName);
    }

    private static <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }

    private static void validateRequest(TimelineEditRequest request) {
        if (request == null || request.getKind() == null || request.getAnchorStart() == null
                || request.getAnchorEnd() == null || request.getAnchorEnd().isBefore(request.getAnchorStart())) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "An operation and a valid anchor interval are required");
        }
        boolean pointAnchor = request.getAnchorStart().equals(request.getAnchorEnd());
        if (pointAnchor && request.getKind() != TimelineEditKind.SET_MOVEMENT_TYPE
                && request.getKind() != TimelineEditKind.SET_TIMES && request.getKind() != TimelineEditKind.REMOVE_EVENT) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "This edit requires a positive anchor interval");
        }
        if (pointAnchor && request.getKind() == TimelineEditKind.REMOVE_EVENT
                && (!(request.getPayload() instanceof ClearPayload clear)
                || clear.resolution() != ClearPayload.RemovalResolution.DISMISS)) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "A zero-duration transition must be dismissed without filling time");
        }
        if (request.getAffectedFrom() == null) request.setAffectedFrom(request.getAnchorStart());
        if (request.getAffectedTo() == null) request.setAffectedTo(pointAnchor
                ? request.getAnchorEnd().plusSeconds(1) : request.getAnchorEnd());
        if (!request.getAffectedTo().isAfter(request.getAffectedFrom())) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Affected end must be after affected start");
        }
    }

    /**
     * Two structural corrections in one range compose in sequence; a detail correction conflicts only with the same
     * detail on the same event, which it replaces.
     */
    private boolean conflicts(TimelineEditEntity existing, TimelineEditKind kind, Instant from, Instant to) {
        if (existing.getKind().structural() || kind.structural()) {
            return existing.getKind().structural() && kind.structural();
        }
        return existing.getKind() == kind && sameTarget(existing, from, to);
    }

    private boolean restoreConflicts(TimelineEditEntity existing, TimelineEditEntity requested) {
        if (insertsAtAnchor(requested.getKind())) {
            if (insertsAtAnchor(existing.getKind())) {
                if (!existing.getAnchorStart().isBefore(requested.getAnchorEnd())
                        || !existing.getAnchorEnd().isAfter(requested.getAnchorStart())) return false;
                boolean nested = !requested.getAnchorStart().isBefore(existing.getAnchorStart()) && !requested.getAnchorEnd().isAfter(existing.getAnchorEnd());
                boolean contains = !existing.getAnchorStart().isBefore(requested.getAnchorStart()) && !existing.getAnchorEnd().isAfter(requested.getAnchorEnd());
                return !nested && !contains;
            }
            // Fills compose with resizes, clears and merges just as they do when first saved.
            return false;
        }
        return conflicts(existing, requested.getKind(), requested.getAnchorStart(), requested.getAnchorEnd());
    }


    private boolean sameTarget(TimelineEditEntity existing, Instant from, Instant to) {
        Instant first = existing.getAnchorStart().isBefore(from) ? existing.getAnchorStart() : from;
        Instant last = existing.getAnchorEnd().isAfter(to) ? existing.getAnchorEnd() : to;
        var events = loadCurrentEvents(existing.getUser().getId(), first, last.equals(first) ? last.plusSeconds(1) : last);
        Class<? extends TimelineEvent> type = switch (existing.getKind()) {
            case SET_MOVEMENT_TYPE, SET_ROUTE -> Trip.class;
            default -> Stay.class;
        };
        var before = new TimelineEditCommand(existing.getId(), existing.getSeq(), existing.getKind(), existing.getAnchorStart(), existing.getAnchorEnd(), existing.getPayload());
        var after = new TimelineEditCommand(0L, 0, existing.getKind(), from, to, existing.getPayload());
        TimelineEvent target = TimelineEditApplier.bestMatch(events, before, type);
        return target != null && target.equals(TimelineEditApplier.bestMatch(events, after, type));
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

    private static PlaceResolved mainPlace(CorrectionPayload payload) {
        if (payload instanceof PlaceStayPayload p) return new PlaceResolved(p.favoriteId(), p.geocodingId(), p.latitude(), p.longitude(), p.locationName());
        if (payload instanceof SetPlacePayload p) return new PlaceResolved(p.favoriteId(), p.geocodingId(), p.latitude(), p.longitude(), p.locationName());
        return null;
    }

    private record Target(String type, Long id) {
    }

    private record PlaceResolved(Long favoriteId, Long geocodingId, Double latitude, Double longitude,
                                 String locationName) {
    }
}
