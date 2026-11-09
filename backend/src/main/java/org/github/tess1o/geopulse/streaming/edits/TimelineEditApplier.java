package org.github.tess1o.geopulse.streaming.edits;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.stream.Stream;
import java.util.function.Consumer;

/**
 * Applies persisted one-off edits as an ordered fold over generated events.
 */
@ApplicationScoped
public class TimelineEditApplier {
    @Inject ObjectMapper objectMapper;
    private static final double MAX_TRIP_ANCHOR_DISTANCE_METERS = 2_000;

    public TimelineEditResult apply(List<TimelineEvent> source, List<TimelineEditCommand> commands) {
        return apply(source, commands, trip -> {
        });
    }

    public TimelineEditResult apply(List<TimelineEvent> source, List<TimelineEditCommand> commands,
                                    Consumer<Trip> recalculateTrip) {
        List<TimelineEvent> events = source.stream().map(TimelineEditApplier::copy).collect(ArrayList::new,
                ArrayList::add, ArrayList::addAll);
        events.sort(Comparator.comparing(TimelineEvent::getStartTime).thenComparing(TimelineEvent::getEndTime));
        Map<Long, TimelineEvent> applications = new LinkedHashMap<>();
        List<Long> unapplied = new ArrayList<>();

        commands.stream().sorted(Comparator.comparingLong(TimelineEditCommand::seq)).forEach(command -> {
            Set<List<Instant>> tripRanges = events.stream().filter(Trip.class::isInstance)
                    .map(event -> List.of(event.getStartTime(), event.getEndTime())).collect(Collectors.toSet());
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
                applications.put(command.id(), applied);
            }
        });
        return new TimelineEditResult(List.copyOf(events),
                Collections.unmodifiableMap(new LinkedHashMap<>(applications)), List.copyOf(unapplied));
    }

    private TimelineEvent applyOne(List<TimelineEvent> events, TimelineEditCommand edit) {
        return switch (edit.kind()) {
            case SET_MOVEMENT_TYPE -> setMovementType(events, edit);
            case SET_PLACE -> setPlace(events, edit);
            case SET_VISIT_NAME -> setVisitName(events, edit);
            case FORCE_STAY -> forceStay(events, edit);
            case FORCE_TRIP -> forceTrip(events, edit);
            case SET_TIMES -> setTimes(events, edit);
            case SPLIT_STAY -> splitStay(events, edit);
            case MERGE -> merge(events, edit);
            case REMOVE_EVENT -> remove(events, edit);
        };
    }

    private TimelineEvent setMovementType(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Trip.class);
        if (!(match instanceof Trip trip)) return null;
        trip.setTripType(TripType.valueOf(requiredText(edit, "movementType")));
        trip.setMovementTypeSource(MovementTypeSource.MANUAL);
        return trip;
    }

    private TimelineEvent setPlace(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Stay.class);
        if (!(match instanceof Stay stay)) return null;
        applyPlace(stay, edit.payload(), "");
        return stay;
    }

    private TimelineEvent setVisitName(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Stay.class);
        if (!(match instanceof Stay stay)) return null;
        String visitName = requiredText(edit, "visitName");
        if (visitName.length() > 500) throw new IllegalArgumentException("Visit name must be at most 500 characters");
        stay.setVisitName(visitName);
        return stay;
    }

    private TimelineEvent forceStay(List<TimelineEvent> events, TimelineEditCommand edit) {
        carve(events, edit.anchorStart(), edit.anchorEnd());
        Stay stay = Stay.builder()
                .startTime(edit.anchorStart())
                .duration(Duration.between(edit.anchorStart(), edit.anchorEnd()))
                .build();
        applyPlace(stay, edit.payload(), "");
        if (Boolean.TRUE.equals(edit.payload().get("reconstruction")) || Boolean.TRUE.equals(edit.payload().get("activityCorrection"))) {
            stay.setExplanation(new TimelineExplanation(TimelineReason.of(TimelineReasons.MANUAL_RECONSTRUCTION), List.of(), null));
        }
        stay.setVisitName(text(edit.payload(), "visitName", null));
        if (stay.getLocationName() == null) stay.setLocationName("Unknown location");
        events.add(stay);
        return stay;
    }

    private TimelineEvent forceTrip(List<TimelineEvent> events, TimelineEditCommand edit) {
        carve(events, edit.anchorStart(), edit.anchorEnd());
        double startLat = number(edit.payload(), "startLatitude", 0);
        double startLon = number(edit.payload(), "startLongitude", 0);
        double endLat = number(edit.payload(), "endLatitude", startLat);
        double endLon = number(edit.payload(), "endLongitude", startLon);
        Trip trip = Trip.builder()
                .startTime(edit.anchorStart())
                .duration(Duration.between(edit.anchorStart(), edit.anchorEnd()))
                .tripType(TripType.valueOf(text(edit.payload(), "movementType", TripType.UNKNOWN.name())))
                .movementTypeSource(MovementTypeSource.MANUAL)
                .startPoint(GPSPoint.builder().timestamp(edit.anchorStart()).latitude(startLat).longitude(startLon).build())
                .endPoint(GPSPoint.builder().timestamp(edit.anchorEnd()).latitude(endLat).longitude(endLon).build())
                .distanceMeters(number(edit.payload(), "distanceMeters", 0))
                .build();
        if (Boolean.TRUE.equals(edit.payload().get("activityCorrection"))) {
            var route = ManualRouteGeometry.read(edit.payload().get("waypoints"), objectMapper);
            trip.setManualRoute(route);
            trip.setDistanceMeters(ManualRouteGeometry.distance(route));
            var first = route.getFirst();
            var last = route.getLast();
            trip.setStartPoint(GPSPoint.builder().timestamp(edit.anchorStart()).latitude(first.getLatitude()).longitude(first.getLongitude()).build());
            trip.setEndPoint(GPSPoint.builder().timestamp(edit.anchorEnd()).latitude(last.getLatitude()).longitude(last.getLongitude()).build());
            trip.setExplanation(new TimelineExplanation(TimelineReason.of(TimelineReasons.MANUAL_RECONSTRUCTION,
                    "startLocationName", text(edit.payload(), "startLocationName", null),
                    "endLocationName", text(edit.payload(), "endLocationName", null)), List.of(), null));
        }
        events.add(trip);
        return trip;
    }

    private TimelineEvent setTimes(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, TimelineEvent.class);
        if (match == null) return null;
        int index = events.indexOf(match);
        Instant start = Instant.parse(requiredText(edit, "start"));
        Instant end = Instant.parse(requiredText(edit, "end"));
        if (!end.isAfter(start)) throw new IllegalArgumentException("Edit end must be after start");
        if ((index > 0 && !start.isAfter(events.get(index - 1).getStartTime()))
                || (index + 1 < events.size() && !end.isBefore(events.get(index + 1).getEndTime()))) return null;
        if (index > 0) resizeEnd(events.get(index - 1), start);
        if (index + 1 < events.size()) resizeStart(events.get(index + 1), end);
        resize(match, start, end);
        return match;
    }

    private TimelineEvent splitStay(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent match = bestMatch(events, edit, Stay.class);
        if (!(match instanceof Stay stay)) return null;
        Instant splitAt = Instant.parse(requiredText(edit, "splitAt"));
        if (!splitAt.isAfter(stay.getStartTime()) || !splitAt.isBefore(stay.getEndTime())) {
            return null;
        }
        Stay right = (Stay) copy(stay);
        resizeEnd(stay, splitAt);
        resizeStart(right, splitAt);
        applyPlace(right, edit.payload(), "right");
        events.add(right);
        return right;
    }

    private TimelineEvent merge(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent current = bestMatch(events, edit, TimelineEvent.class);
        if (current == null) return null;
        int index = events.indexOf(current);
        boolean previous = "PREVIOUS".equals(requiredText(edit, "direction"));
        int step = previous ? -1 : 1;
        int otherIndex = index + step;
        if (otherIndex >= 0 && otherIndex < events.size()
                && !events.get(otherIndex).getClass().equals(current.getClass())) {
            otherIndex += step;
        }
        if (otherIndex < 0 || otherIndex >= events.size()) return null;
        TimelineEvent other = events.get(otherIndex);
        if (!canMerge(current, other)) return null;
        int from = Math.min(index, otherIndex);
        int to = Math.max(index, otherIndex);
        TimelineEvent merged = mergeEvents(events.get(from), events.get(to));
        events.subList(from, to + 1).clear();
        events.add(from, merged);
        return merged;
    }

    private TimelineEvent remove(List<TimelineEvent> events, TimelineEditCommand edit) {
        TimelineEvent current = bestMatch(events, edit, TimelineEvent.class);
        if (current == null) return null;
        int index = events.indexOf(current);
        Instant start = current.getStartTime();
        Instant end = current.getEndTime();
        String resolution = requiredText(edit, "resolution");
        if ("DISMISS".equals(resolution)) {
            if (!(current instanceof Trip) || !current.getDuration().isZero() || events.size() == 1) return null;
            events.remove(index);
            // Attach the removal to a surviving neighbour so it can be found and undone after regeneration.
            return events.get(index > 0 ? index - 1 : 0);
        }
        // Preferences can remove a required neighbour; an unapplied edit must leave every event intact.
        if (("EXTEND_PREVIOUS".equals(resolution) && index == 0)
                || ("EXTEND_NEXT".equals(resolution) && index + 1 == events.size())
                || ("MERGE".equals(resolution) && (index == 0 || index + 1 == events.size()
                || !canMerge(events.get(index - 1), events.get(index + 1))))) return null;
        events.remove(index);
        return switch (resolution) {
            case "GAP" -> {
                DataGap gap = DataGap.fromTimeRange(start, end);
                events.add(index, gap);
                yield gap;
            }
            case "EXTEND_PREVIOUS" -> {
                TimelineEvent previous = events.get(index - 1);
                resizeEnd(previous, end);
                yield previous;
            }
            case "EXTEND_NEXT" -> {
                TimelineEvent next = events.get(index);
                resizeStart(next, start);
                yield next;
            }
            case "MERGE" -> {
                TimelineEvent merged = mergeEvents(events.get(index - 1), events.get(index));
                events.remove(index);
                events.set(index - 1, merged);
                yield merged;
            }
            default -> throw new IllegalArgumentException("Unknown removal resolution: " + resolution);
        };
    }

    private static TimelineEvent bestMatch(List<TimelineEvent> events, TimelineEditCommand edit,
                                           Class<? extends TimelineEvent> type) {
        long anchorSeconds = Duration.between(edit.anchorStart(), edit.anchorEnd()).getSeconds();
        TimelineEvent best = null;
        long bestShared = 0;
        for (TimelineEvent event : events) {
            if (!type.isInstance(event)) continue;
            if (edit.payload().get("sourceType") != null
                    && !event.getType().name().equals(edit.payload().get("sourceType"))) continue;
            if (event instanceof Trip trip && !tripAnchorMatches(edit.payload(), trip)) continue;
            if (edit.anchorStart().equals(edit.anchorEnd())) {
                if (event instanceof Trip && event.getDuration().isZero()
                        && event.getStartTime().equals(edit.anchorStart())) return event;
                continue;
            }
            long shared = overlapSeconds(edit.anchorStart(), edit.anchorEnd(), event.getStartTime(), event.getEndTime());
            long eventSeconds = event.getDuration().getSeconds();
            if (shared * 2 > anchorSeconds && shared * 2 > eventSeconds && shared > bestShared) {
                best = event;
                bestShared = shared;
            }
        }
        return best;
    }

    private static boolean tripAnchorMatches(Map<String, Object> payload, Trip trip) {
        if (!(payload.get("sourceStartLatitude") instanceof Number startLat)
                || !(payload.get("sourceStartLongitude") instanceof Number startLon)
                || !(payload.get("sourceEndLatitude") instanceof Number endLat)
                || !(payload.get("sourceEndLongitude") instanceof Number endLon)) {
            return true;
        }
        return trip.getStartPoint() != null && trip.getEndPoint() != null
                && GeoUtils.haversine(startLat.doubleValue(), startLon.doubleValue(),
                trip.getStartPoint().getLatitude(), trip.getStartPoint().getLongitude()) <= MAX_TRIP_ANCHOR_DISTANCE_METERS
                && GeoUtils.haversine(endLat.doubleValue(), endLon.doubleValue(),
                trip.getEndPoint().getLatitude(), trip.getEndPoint().getLongitude()) <= MAX_TRIP_ANCHOR_DISTANCE_METERS;
    }

    private static void carve(List<TimelineEvent> events, Instant from, Instant to) {
        List<TimelineEvent> replacement = new ArrayList<>();
        for (TimelineEvent event : events) {
            if (!event.getStartTime().isBefore(to) || !event.getEndTime().isAfter(from)) {
                replacement.add(event);
                continue;
            }
            if (event.getStartTime().isBefore(from)) replacement.add(copyRange(event, event.getStartTime(), from));
            if (event.getEndTime().isAfter(to)) replacement.add(copyRange(event, to, event.getEndTime()));
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

    private static TimelineEvent copyRange(TimelineEvent event, Instant start, Instant end) {
        TimelineEvent copy = copy(event);
        resize(copy, start, end);
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
        if (!end.isAfter(start)) throw new IllegalArgumentException("An event must have positive duration");
        Duration duration = Duration.between(start, end);
        if (event instanceof Stay stay) {
            stay.setStartTime(start);
            stay.setDuration(duration);
        } else if (event instanceof Trip trip) {
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

    private static void applyPlace(Stay stay, Map<String, Object> payload, String prefix) {
        String nameKey = prefix.isEmpty() ? "locationName" : prefix + "LocationName";
        String latitudeKey = prefix.isEmpty() ? "latitude" : prefix + "Latitude";
        String longitudeKey = prefix.isEmpty() ? "longitude" : prefix + "Longitude";
        String favoriteKey = prefix.isEmpty() ? "favoriteId" : prefix + "FavoriteId";
        String geocodingKey = prefix.isEmpty() ? "geocodingId" : prefix + "GeocodingId";
        boolean placeGiven = Stream.of(nameKey, latitudeKey, longitudeKey, favoriteKey, geocodingKey)
                .anyMatch(payload::containsKey);
        if (!placeGiven) return;
        if (payload.containsKey(nameKey)) stay.setLocationName(text(payload, nameKey, stay.getLocationName()));
        if (payload.containsKey(latitudeKey)) stay.setLatitude(number(payload, latitudeKey, stay.getLatitude()));
        if (payload.containsKey(longitudeKey)) stay.setLongitude(number(payload, longitudeKey, stay.getLongitude()));
        // A chosen place replaces the whole identity: a reference left over from the previous place would group the
        // stay under a place its name and coordinates no longer describe.
        stay.setFavoriteId(longNumber(payload, favoriteKey));
        stay.setGeocodingId(longNumber(payload, geocodingKey));
    }

    private static long overlapSeconds(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        Instant start = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        Instant end = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        return end.isAfter(start) ? Duration.between(start, end).getSeconds() : 0;
    }

    private static String requiredText(TimelineEditCommand edit, String key) {
        String value = text(edit.payload(), key, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String text(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value == null ? fallback : value.toString();
    }

    private static double number(Map<String, Object> payload, String key, double fallback) {
        Object value = payload.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static Long longNumber(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }
}
