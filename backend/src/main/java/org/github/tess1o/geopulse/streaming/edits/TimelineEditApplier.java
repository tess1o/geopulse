package org.github.tess1o.geopulse.streaming.edits;


import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
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
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/**
 * Applies persisted one-off edits as an ordered fold over generated events.
 */
@ApplicationScoped
public class TimelineEditApplier {

    public TimelineEditResult apply(List<TimelineEvent> source, List<TimelineEditCommand> commands) {
        return apply(source, commands, trip -> {
        });
    }

    public TimelineEditResult apply(List<TimelineEvent> source, List<TimelineEditCommand> commands,
                                    Consumer<Trip> recalculateTrip) {
        List<TimelineEvent> events = source.stream().map(TimelineEditApplier::copy).collect(ArrayList::new,
                ArrayList::add, ArrayList::addAll);
        events.sort(Comparator.comparing(TimelineEvent::getStartTime).thenComparing(TimelineEvent::getEndTime));
        Map<Long, List<TimelineEvent>> effects = new LinkedHashMap<>();
        List<Long> unapplied = new ArrayList<>();

        Map<Long, Long> appliedSequences = new HashMap<>();
        commands.stream().sorted(Comparator.comparingLong(TimelineEditCommand::seq)).forEach(command -> {
            // Structural edits need their prerequisites applied first (a split before a split of its leg). A detail
            // edit only uses the dependency for undo order; its target is resolved against the current fold, so it
            // may still apply while the correction it was made on is dormant.
            if (command.kind().structural()
                    && !appliedSequences.keySet().containsAll(command.prerequisiteSequences())) {
                unapplied.add(command.id());
                return;
            }
            Set<List<Instant>> tripRanges = events.stream().filter(Trip.class::isInstance)
                    .map(event -> List.of(event.getStartTime(), event.getEndTime())).collect(Collectors.toSet());
            Set<TimelineEvent> existing = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            existing.addAll(events);
            TimelineEvent applied = applyOne(events, command);
            // Recalculate before the next edit can match the changed trip's endpoints.
            for (TimelineEvent event : events) {
                if (event instanceof Trip trip && !(command.kind() == TimelineEditKind.FORCE_TRIP && event.equals(applied))
                        && !tripRanges.contains(List.of(trip.getStartTime(), trip.getEndTime()))) {
                    recalculateTrip.accept(trip);
                }
            }
            events.sort(Comparator.comparing(TimelineEvent::getStartTime).thenComparing(TimelineEvent::getEndTime));
            if (applied == null) {
                unapplied.add(command.id());
            } else {
                effects.replaceAll((id, prior) -> prior.stream().flatMap(event -> {
                    if (events.contains(event)) return java.util.stream.Stream.of(event);
                    if (command.kind() == TimelineEditKind.MERGE) return java.util.stream.Stream.of(applied);
                    if (command.kind() == TimelineEditKind.FORCE_STAY || command.kind() == TimelineEditKind.FORCE_TRIP) {
                        return events.stream().filter(fragment -> !fragment.equals(applied) && fragment.getClass() == event.getClass()
                                && !fragment.getStartTime().isBefore(event.getStartTime())
                                && !fragment.getEndTime().isAfter(event.getEndTime()));
                    }
                    return java.util.stream.Stream.empty();
                }).distinct().toList());
                effects.put(command.id(), switch (command.kind()) {
                    case SET_TIMES -> withNeighbours(events, applied);
                    case FORCE_STAY, FORCE_TRIP -> withCarvedPieces(events, existing, applied, effects);
                    default -> List.of(applied);
                });
                appliedSequences.put(command.seq(), command.id());
            }
        });
        effects.entrySet().removeIf(entry -> {
            if (!entry.getValue().isEmpty()) return false;
            unapplied.add(entry.getKey());
            return true;
        });
        return new TimelineEditResult(List.copyOf(events),
                Collections.unmodifiableMap(new LinkedHashMap<>(effects)), List.copyOf(unapplied));
    }

    /**
     * A resize changes its event and trims the events on either side; all of them are its effects, so a later
     * correction on a trimmed neighbour depends on the resize.
     */
    private static List<TimelineEvent> withNeighbours(List<TimelineEvent> events, TimelineEvent resized) {
        int index = events.indexOf(resized);
        var changed = new ArrayList<TimelineEvent>();
        changed.add(resized);
        if (index > 0) changed.add(events.get(index - 1));
        if (index >= 0 && index + 1 < events.size()) changed.add(events.get(index + 1));
        return List.copyOf(changed);
    }

    /**
     * A fill produces its own event and the stay or trip pieces it leaves of detected events it cut, such as the two
     * legs of a trip a stop was inserted into: a later edit of such a leg depends on the fill, because undoing the fill
     * removes the leg. Pieces of an event another correction produced stay that correction's effects (a fill nested in
     * an earlier fill does not own the earlier fill's remainder). Gap pieces are not tracked; nothing is edited on them.
     */
    private static List<TimelineEvent> withCarvedPieces(List<TimelineEvent> events, Set<TimelineEvent> existing,
                                                        TimelineEvent inserted,
                                                        Map<Long, List<TimelineEvent>> effects) {
        Set<TimelineEvent> claimed = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        effects.values().forEach(claimed::addAll);
        var produced = new ArrayList<TimelineEvent>();
        produced.add(inserted);
        for (TimelineEvent event : events) {
            if (event != inserted && !existing.contains(event) && !claimed.contains(event)
                    && !(event instanceof DataGap)) {
                produced.add(event);
            }
        }
        return List.copyOf(produced);
    }

    private TimelineEvent applyOne(List<TimelineEvent> events, TimelineEditCommand edit) {
        return switch (edit.kind()) {
            case SET_MOVEMENT_TYPE -> setMovementType(events, edit);
            case SET_PLACE -> setPlace(events, edit);
            case SET_VISIT_NAME -> setVisitName(events, edit);
            case FORCE_STAY -> forceStay(events, edit);
            case FORCE_TRIP -> forceTrip(events, edit);
            case SET_TIMES -> setTimes(events, edit);
            case SET_ROUTE -> setRoute(events, edit);
            case SPLIT_STAY -> splitStay(events, edit);
            case MERGE -> merge(events, edit);
            case REMOVE_EVENT -> remove(events, edit);
        };
    }

    private TimelineEvent setMovementType(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Trip.class);
        if (!(match instanceof Trip trip)) return null;
        SetTripTypePayload payload = payload(edit, SetTripTypePayload.class);
        if (payload.movementType() == null) throw new IllegalArgumentException("movementType is required");
        trip.setTripType(payload.movementType());
        trip.setMovementTypeSource(MovementTypeSource.MANUAL);
        return trip;
    }

    private TimelineEvent setPlace(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Stay.class);
        if (!(match instanceof Stay stay)) return null;
        SetPlacePayload payload = payload(edit, SetPlacePayload.class);
        applyPlace(stay, payload.favoriteId(), payload.geocodingId(),
                payload.latitude(), payload.longitude(), payload.locationName());
        return stay;
    }

    private TimelineEvent setVisitName(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Stay.class);
        if (!(match instanceof Stay stay)) return null;
        String visitName = payload(edit, SetLabelPayload.class).visitName();
        if (visitName == null) throw new IllegalArgumentException("visitName is required");
        if (visitName.length() > 500) throw new IllegalArgumentException("Visit name must be at most 500 characters");
        stay.setVisitName(visitName.isBlank() ? null : visitName);
        return stay;
    }

    private TimelineEvent forceStay(List<TimelineEvent> events, TimelineEditCommand edit) {
        PlaceStayPayload payload = payload(edit, PlaceStayPayload.class);
        carve(events, edit.anchorStart(), edit.anchorEnd(), payload.carveExplanation());
        Stay stay = Stay.builder()
                .startTime(edit.anchorStart())
                .duration(Duration.between(edit.anchorStart(), edit.anchorEnd()))
                .build();
        applyPlace(stay, payload.favoriteId(), payload.geocodingId(),
                payload.latitude(), payload.longitude(), payload.locationName());
        if (payload.explanation() != null) {
            stay.setExplanation(new TimelineExplanation(TimelineReason.of(payload.explanation()), List.of(), null));
        } else if (Boolean.TRUE.equals(payload.reconstruction())
                || Boolean.TRUE.equals(payload.activityCorrection())) {
            stay.setExplanation(new TimelineExplanation(TimelineReason.of(TimelineReasons.MANUAL_RECONSTRUCTION), List.of(), null));
        }
        stay.setVisitName(payload.visitName());
        // A migrated LATEST_POINT gap stay has no name yet; the post-edit geocoding pass fills it.
        if (stay.getLocationName() == null && !Boolean.TRUE.equals(payload.resolveLocationName())) {
            stay.setLocationName("Unknown location");
        }
        events.add(stay);
        return stay;
    }

    private TimelineEvent forceTrip(List<TimelineEvent> events, TimelineEditCommand edit) {
        PlaceTripPayload payload = payload(edit, PlaceTripPayload.class);
        carve(events, edit.anchorStart(), edit.anchorEnd());
        double startLat = payload.startLatitude() == null ? 0 : payload.startLatitude();
        double startLon = payload.startLongitude() == null ? 0 : payload.startLongitude();
        double endLat = payload.endLatitude() == null ? startLat : payload.endLatitude();
        double endLon = payload.endLongitude() == null ? startLon : payload.endLongitude();
        Trip trip = Trip.builder()
                .startTime(edit.anchorStart())
                .duration(Duration.between(edit.anchorStart(), edit.anchorEnd()))
                .tripType(payload.movementType() == null ? TripType.UNKNOWN : payload.movementType())
                .movementTypeSource(MovementTypeSource.MANUAL)
                .startPoint(GPSPoint.builder().timestamp(edit.anchorStart()).latitude(startLat).longitude(startLon).build())
                .endPoint(GPSPoint.builder().timestamp(edit.anchorEnd()).latitude(endLat).longitude(endLon).build())
                .distanceMeters(payload.distanceMeters() == null ? 0 : payload.distanceMeters())
                .build();
        if (Boolean.TRUE.equals(payload.activityCorrection())) {
            ManualRouteGeometry.validate(payload.waypoints());
            var route = payload.waypoints();
            trip.setManualRoute(route);
            trip.setDistanceMeters(ManualRouteGeometry.distance(route));
            var first = route.getFirst();
            var last = route.getLast();
            trip.setStartPoint(GPSPoint.builder().timestamp(edit.anchorStart()).latitude(first.getLatitude()).longitude(first.getLongitude()).build());
            trip.setEndPoint(GPSPoint.builder().timestamp(edit.anchorEnd()).latitude(last.getLatitude()).longitude(last.getLongitude()).build());
            trip.setExplanation(new TimelineExplanation(TimelineReason.of(TimelineReasons.MANUAL_RECONSTRUCTION,
                    "startLocationName", payload.startLocationName(),
                    "endLocationName", payload.endLocationName()), List.of(), null));
        }
        events.add(trip);
        return trip;
    }

    private TimelineEvent setTimes(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, TimelineEvent.class);
        if (match == null) return null;
        int index = events.indexOf(match);
        ResizePayload payload = payload(edit, ResizePayload.class);
        Instant start = payload.start();
        Instant end = payload.end();
        if (start == null || end == null) throw new IllegalArgumentException("start and end are required");
        if (!end.isAfter(start)) throw new IllegalArgumentException("Edit end must be after start");
        if ((index > 0 && !start.isAfter(events.get(index - 1).getStartTime()))
                || (index + 1 < events.size() && !end.isBefore(events.get(index + 1).getEndTime()))) return null;
        if (index > 0) resizeEnd(events.get(index - 1), start);
        if (index + 1 < events.size()) resizeStart(events.get(index + 1), end);
        resize(match, start, end, false);
        return match;
    }

    private TimelineEvent setRoute(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Trip.class);
        if (!(match instanceof Trip trip)) return null;
        var route = payload(edit, SetRoutePayload.class).waypoints();
        ManualRouteGeometry.validate(route);
        trip.setManualRoute(route);
        trip.setDistanceMeters(ManualRouteGeometry.distance(route));
        var first = route.getFirst();
        var last = route.getLast();
        trip.setStartPoint(GPSPoint.builder().timestamp(trip.getStartTime())
                .latitude(first.getLatitude()).longitude(first.getLongitude()).build());
        trip.setEndPoint(GPSPoint.builder().timestamp(trip.getEndTime())
                .latitude(last.getLatitude()).longitude(last.getLongitude()).build());
        return trip;
    }

    private TimelineEvent splitStay(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Stay.class);
        if (!(match instanceof Stay stay)) return null;
        SplitStayPayload payload = payload(edit, SplitStayPayload.class);
        Instant splitAt = payload.splitAt();
        if (splitAt == null || !splitAt.isAfter(stay.getStartTime()) || !splitAt.isBefore(stay.getEndTime())) {
            return null;
        }
        Stay right = (Stay) copy(stay);
        resizeEnd(stay, splitAt);
        resizeStart(right, splitAt);
        applyPlace(right, payload.rightFavoriteId(), payload.rightGeocodingId(),
                payload.rightLatitude(), payload.rightLongitude(), payload.rightLocationName());
        events.add(right);
        return right;
    }

    private TimelineEvent merge(List<TimelineEvent> events, TimelineEditCommand edit) {
        int[] range = mergeRange(events, edit);
        if (range == null) return null;
        int from = range[0];
        int to = range[1];
        TimelineEvent merged = mergeEvents(events.get(from), events.get(to));
        events.subList(from, to + 1).clear();
        events.add(from, merged);
        return merged;
    }

    /**
     * The index range a merge joins: the target and the next/previous event of its type, swallowing at most one
     * event of another type in between. Null when there is nothing compatible to merge with.
     */
    private static int[] mergeRange(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent current = bestMatch(events, edit, TimelineEvent.class);
        if (current == null) return null;
        int index = events.indexOf(current);
        boolean previous = payload(edit, MergePayload.class).direction() == MergePayload.MergeDirection.PREVIOUS;
        int step = previous ? -1 : 1;
        int otherIndex = index + step;
        if (otherIndex >= 0 && otherIndex < events.size()
                && !events.get(otherIndex).getClass().equals(current.getClass())) {
            otherIndex += step;
        }
        if (otherIndex < 0 || otherIndex >= events.size()) return null;
        if (!canMerge(current, events.get(otherIndex))) return null;
        return new int[]{Math.min(index, otherIndex), Math.max(index, otherIndex)};
    }

    /**
     * The existing events a correction would change or consume, resolved exactly as the fold resolves them. A
     * correction depends on the corrections that produced these events, and on nothing else nearby. Fills and
     * removals consume nothing: they act on time, not on an earlier correction's output.
     */
    static List<TimelineEvent> consumedEvents(List<TimelineEvent> source, TimelineEditCommand edit) {
        List<TimelineEvent> events = new ArrayList<>(source);
        events.sort(Comparator.comparing(TimelineEvent::getStartTime).thenComparing(TimelineEvent::getEndTime));
        if (edit.kind() == TimelineEditKind.MERGE) {
            int[] range = mergeRange(events, edit);
            return range == null ? List.of() : List.copyOf(events.subList(range[0], range[1] + 1));
        }
        TimelineEvent target = switch (edit.kind()) {
            case SET_MOVEMENT_TYPE, SET_ROUTE -> bestMatch(events, edit, Trip.class);
            case SET_PLACE, SET_VISIT_NAME, SPLIT_STAY -> bestMatch(events, edit, Stay.class);
            case SET_TIMES -> bestMatch(events, edit, TimelineEvent.class);
            case FORCE_STAY, FORCE_TRIP, REMOVE_EVENT, MERGE -> null;
        };
        return target == null ? List.of() : List.of(target);
    }

    private TimelineEvent remove(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent current = bestMatch(events, edit, TimelineEvent.class);
        if (current == null) return null;
        int index = events.indexOf(current);
        Instant start = current.getStartTime();
        Instant end = current.getEndTime();
        ClearPayload.RemovalResolution resolution = payload(edit, ClearPayload.class).resolution();
        if (resolution == null) throw new IllegalArgumentException("resolution is required");
        if (resolution == ClearPayload.RemovalResolution.DISMISS) {
            if (!(current instanceof Trip) || !current.getDuration().isZero() || events.size() == 1) return null;
            events.remove(index);
            // Attach the removal to a surviving neighbour so it can be found and undone after regeneration.
            return events.get(index > 0 ? index - 1 : 0);
        }
        // Preferences can remove a required neighbour; an unapplied edit must leave every event intact.
        if ((resolution == ClearPayload.RemovalResolution.EXTEND_PREVIOUS && index == 0)
                || (resolution == ClearPayload.RemovalResolution.EXTEND_NEXT && index + 1 == events.size())
                || (resolution == ClearPayload.RemovalResolution.MERGE && (index == 0 || index + 1 == events.size()
                || !canMerge(events.get(index - 1), events.get(index + 1))))) return null;
        events.remove(index);
        return switch (resolution) {
            case GAP -> {
                DataGap gap = DataGap.fromTimeRange(start, end);
                events.add(index, gap);
                yield gap;
            }
            case EXTEND_PREVIOUS -> {
                TimelineEvent previous = events.get(index - 1);
                resizeEnd(previous, end);
                yield previous;
            }
            case EXTEND_NEXT -> {
                TimelineEvent next = events.get(index);
                resizeStart(next, start);
                yield next;
            }
            case MERGE -> {
                TimelineEvent merged = mergeEvents(events.get(index - 1), events.get(index));
                events.remove(index);
                events.set(index - 1, merged);
                yield merged;
            }
            default -> throw new IllegalArgumentException("Unknown removal resolution: " + resolution);
        };
    }

    static TimelineEvent bestMatch(List<TimelineEvent> events, TimelineEditCommand edit,
                                           Class<? extends TimelineEvent> type) {
        List<TimelineEvent> candidates = events.stream()
                .filter(type::isInstance)
                .filter(event -> edit.sourceType() == null || event.getType() == edit.sourceType())
                .toList();
        // Deterministic resolution: the event the user actually pointed at contains the anchor start. A point anchor
        // (zero-duration transition) matches the event that starts at that exact instant. If regeneration moved the
        // event past the anchor, the event sharing the most time with the anchor wins. No overlap means the edit has
        // no target and is reported as dormant; it is never silently attached to a different event.
        TimelineEvent containing = null;
        for (TimelineEvent event : candidates) {
            if (event.getDuration().isZero()) {
                if (event.getStartTime().equals(edit.anchorStart())
                        && (containing == null || event.getStartTime().isBefore(containing.getStartTime()))) {
                    containing = event;
                }
            } else if (!event.getStartTime().isAfter(edit.anchorStart())
                    && event.getStartTime().isBefore(edit.anchorEnd())
                    && event.getEndTime().isAfter(edit.anchorStart())
                    && (containing == null || event.getStartTime().isBefore(containing.getStartTime()))) {
                containing = event;
            } else if (edit.anchorStart().equals(edit.anchorEnd())
                    && !event.getStartTime().isAfter(edit.anchorStart())
                    && event.getEndTime().isAfter(edit.anchorStart())
                    && (containing == null || event.getStartTime().isBefore(containing.getStartTime()))) {
                // A point anchor targets the event that covers that instant, including a positive-duration trip.
                containing = event;
            }
        }
        if (containing != null) {
            return containing;
        }
        TimelineEvent best = null;
        long bestShared = 0;
        for (TimelineEvent event : candidates) {
            long shared = overlapSeconds(edit.anchorStart(), edit.anchorEnd(), event.getStartTime(), event.getEndTime());
            if (shared > bestShared || (shared == bestShared && shared > 0 && best != null
                    && event.getStartTime().isBefore(best.getStartTime()))) {
                best = event;
                bestShared = shared;
            }
        }
        return best;
    }

    private static void carve(List<TimelineEvent> events, Instant from, Instant to) {
        carve(events, from, to, null);
    }

    private static void carve(List<TimelineEvent> events, Instant from, Instant to, String carveExplanation) {
        List<TimelineEvent> replacement = new ArrayList<>();
        for (TimelineEvent event : events) {
            if (event.getDuration().isZero()) {
                if (event.getStartTime().isBefore(from) || !event.getStartTime().isBefore(to)) replacement.add(event);
                continue;
            }
            if (!event.getStartTime().isBefore(to) || !event.getEndTime().isAfter(from)) {
                replacement.add(event);
                continue;
            }
            if (event.getStartTime().isBefore(from)) {
                replacement.add(copyRange(event, event.getStartTime(), from, carveExplanation));
            }
            if (event.getEndTime().isAfter(to)) {
                replacement.add(copyRange(event, to, event.getEndTime(), carveExplanation));
            }
        }
        events.clear();
        events.addAll(replacement);
    }

    private static TimelineEvent mergeEvents(TimelineEvent first, TimelineEvent last) {
        TimelineEvent merged = copy(first);
        resize(merged, first.getStartTime(), last.getEndTime());
        if (merged instanceof Trip trip && last instanceof Trip other) {
            if (other.getMovementTypeSource() == MovementTypeSource.MANUAL)
                trip.setMovementTypeSource(MovementTypeSource.MANUAL);
            trip.setEndPoint(other.getEndPoint());
            trip.setDistanceMeters(trip.getDistanceMeters() + other.getDistanceMeters());
        }
        return merged;
    }

    private static boolean canMerge(TimelineEvent first, TimelineEvent second) {
        if (!first.getClass().equals(second.getClass())) return false;
        if (first instanceof Stay left && second instanceof Stay right) {
            if (left.getFavoriteId() != null || right.getFavoriteId() != null) {
                return left.getFavoriteId() != null && left.getFavoriteId().equals(right.getFavoriteId());
            }
            if (left.getGeocodingId() != null || right.getGeocodingId() != null) {
                return left.getGeocodingId() != null && left.getGeocodingId().equals(right.getGeocodingId());
            }
            return GeoUtils.haversine(left.getLatitude(), left.getLongitude(),
                    right.getLatitude(), right.getLongitude()) <= 200;
        }
        return !(first instanceof Trip left) || (left.getManualRoute() == null && ((Trip) second).getManualRoute() == null
                && left.getTripType() == ((Trip) second).getTripType());
    }

    private static TimelineEvent copyRange(TimelineEvent event, Instant start, Instant end, String explanation) {
        TimelineEvent copy = copy(event);
        resize(copy, start, end);
        if (explanation != null) {
            copy.setExplanation(new TimelineExplanation(null, List.of(TimelineReason.of(explanation)), null));
        }
        return copy;
    }

    private static TimelineEvent copy(TimelineEvent event) {
        if (event instanceof Stay stay) {
            return Stay.builder().startTime(stay.getStartTime()).duration(stay.getDuration())
                    .latitude(stay.getLatitude()).longitude(stay.getLongitude()).locationName(stay.getLocationName())
                    .visitName(stay.getVisitName()).favoriteId(stay.getFavoriteId()).geocodingId(stay.getGeocodingId())
                    .explanation(stay.getExplanation()).build();
        }
        if (event instanceof Trip trip) {
            return Trip.builder().startTime(trip.getStartTime()).duration(trip.getDuration()).tripType(trip.getTripType())
                    .movementTypeSource(trip.getMovementTypeSource())
                    .distanceMeters(trip.getDistanceMeters()).manualRoute(trip.getManualRoute()).startPoint(trip.getStartPoint()).endPoint(trip.getEndPoint())
                    .statistics(trip.getStatistics()).waterStatistics(trip.getWaterStatistics())
                    .altitudeStatistics(trip.getAltitudeStatistics()).explanation(trip.getExplanation()).build();
        }
        DataGap gap = (DataGap) event;
        return DataGap.builder().startTime(gap.getStartTime()).duration(gap.getDuration())
                .explanation(gap.getExplanation()).build();
    }

    private static void resize(TimelineEvent event, Instant start, Instant end) {
        resize(event, start, end, true);
    }

    private static void resize(TimelineEvent event, Instant start, Instant end, boolean clipRoute) {
        if (!end.isAfter(start)) throw new IllegalArgumentException("An event must have positive duration");
        Duration duration = Duration.between(start, end);
        if (event instanceof Stay stay) {
            stay.setStartTime(start);
            stay.setDuration(duration);
        } else if (event instanceof Trip trip) {
            if (trip.getManualRoute() != null && !trip.getDuration().isZero()) {
                double seconds = trip.getDuration().toMillis();
                var route = clipRoute ? ManualRouteGeometry.slice(trip.getManualRoute(),
                        Duration.between(trip.getStartTime(), start).toMillis() / seconds,
                        Duration.between(trip.getStartTime(), end).toMillis() / seconds) : trip.getManualRoute();
                trip.setManualRoute(route);
                trip.setDistanceMeters(ManualRouteGeometry.distance(route));
                trip.setStartPoint(GPSPoint.builder().timestamp(start).latitude(route.getFirst().getLatitude()).longitude(route.getFirst().getLongitude()).build());
                trip.setEndPoint(GPSPoint.builder().timestamp(end).latitude(route.getLast().getLatitude()).longitude(route.getLast().getLongitude()).build());
            }
            trip.setStartTime(start);
            trip.setDuration(duration);
        } else if (event instanceof DataGap gap) {
            gap.setStartTime(start);
            gap.setDuration(duration);
        }
    }

    private static void resizeStart(TimelineEvent event, Instant start) {
        resize(event, start, event.getEndTime());
    }

    private static void resizeEnd(TimelineEvent event, Instant end) {
        resize(event, event.getStartTime(), end);
    }

    private static void applyPlace(Stay stay, Long favoriteId, Long geocodingId,
                                   Double latitude, Double longitude, String locationName) {
        boolean placeGiven = locationName != null || latitude != null || longitude != null
                || favoriteId != null || geocodingId != null;
        if (!placeGiven) return;
        if (locationName != null) stay.setLocationName(locationName);
        if (latitude != null) stay.setLatitude(latitude);
        if (longitude != null) stay.setLongitude(longitude);
        // A chosen place replaces the whole identity: a reference left over from the previous place would group the
        // stay under a place its name and coordinates no longer describe.
        stay.setFavoriteId(favoriteId);
        stay.setGeocodingId(geocodingId);
    }

    private static long overlapSeconds(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        Instant start = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        Instant end = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        return end.isAfter(start) ? Duration.between(start, end).getSeconds() : 0;
    }

    private static <T extends CorrectionPayload> T payload(TimelineEditCommand edit, Class<T> type) {
        if (!type.isInstance(edit.payload())) {
            throw new IllegalArgumentException("Correction " + edit.kind() + " has a payload of the wrong type");
        }
        return type.cast(edit.payload());
    }

}
