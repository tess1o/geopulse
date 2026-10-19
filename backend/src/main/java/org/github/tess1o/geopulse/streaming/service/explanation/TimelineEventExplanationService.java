package org.github.tess1o.geopulse.streaming.service.explanation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.Coordinates;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.IngestionFilter;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.Point;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.Settings;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineExplanationDTO;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.explanation.TimelineExplanationPointClassifier.Kind;
import org.github.tess1o.geopulse.streaming.service.explanation.TimelineExplanationPointClassifier.RawPoint;
import org.github.tess1o.geopulse.streaming.util.TimelineGpsAccuracyFilter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Why a stay, trip or data gap is as it is: its stored explanation with the GPS points it was decided from, a few
 * points before and after it, and the settings and GPS source filters that shaped it.
 */
@ApplicationScoped
public class TimelineEventExplanationService {

    /** Points shown before and after the event. */
    static final int CONTEXT_POINTS = 10;
    /** Context points are looked for no farther than this from the event. */
    static final Duration CONTEXT_WINDOW = Duration.ofHours(2);
    /** More event points than this are thinned out, keeping every left-out or deciding point. */
    static final int MAX_EVENT_POINTS = 3000;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    TimelineConfigurationProvider configProvider;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @ConfigProperty(name = "geopulse.gps.duplicate-detection.location-time-threshold-minutes", defaultValue = "2")
    int defaultDuplicateThresholdMinutes;

    public Optional<TimelineEventExplanationDetailsDTO> explainStay(Long stayId, UUID userId) {
        return stayRepository.findByIdOptional(stayId)
                .filter(stay -> stay.getUser().getId().equals(userId))
                .map(stay -> explain(Kind.STAY, stay.getId(), userId, stay.getTimestamp(),
                        stay.getTimestamp().plusSeconds(stay.getStayDuration()), stay.getExplanation(),
                        coordinates(stay.getLocation()), null, null));
    }

    public Optional<TimelineEventExplanationDetailsDTO> explainTrip(Long tripId, UUID userId) {
        return tripRepository.findByIdOptional(tripId)
                .filter(trip -> trip.getUser().getId().equals(userId))
                .map(trip -> explain(Kind.TRIP, trip.getId(), userId, trip.getTimestamp(),
                        trip.getTimestamp().plusSeconds(trip.getTripDuration()), trip.getExplanation(), null,
                        coordinates(trip.getStartPoint()), coordinates(trip.getEndPoint())));
    }

    public Optional<TimelineEventExplanationDetailsDTO> explainDataGap(Long gapId, UUID userId) {
        return dataGapRepository.findByIdOptional(gapId)
                .filter(gap -> gap.getUser().getId().equals(userId))
                .map(gap -> explain(Kind.DATA_GAP, gap.getId(), userId, gap.getStartTime(), gap.getEndTime(),
                        gap.getExplanation(), null, null, null));
    }

    private TimelineEventExplanationDetailsDTO explain(Kind kind, Long id, UUID userId, Instant start, Instant end,
                                                       Map<String, Object> storedExplanation, Coordinates location,
                                                       Coordinates startPoint, Coordinates endPoint) {
        TimelineConfig config = configProvider.getConfigurationForUser(userId);
        TimelineExplanation explanation = TimelineExplanation.fromMap(storedExplanation);

        List<RawPoint> eventPoints = gpsPointRepository.findExplanationPoints(userId, start, end, true, false, null);
        List<RawPoint> before = new ArrayList<>(contextPoints(gpsPointRepository.findExplanationPoints(
                userId, start.minus(CONTEXT_WINDOW), start, false, true, CONTEXT_POINTS * 5), config, 1));
        Collections.reverse(before);
        List<RawPoint> after = contextPoints(gpsPointRepository.findExplanationPoints(
                userId, end, end.plus(CONTEXT_WINDOW), false, false, CONTEXT_POINTS * 5), config,
                arrivalPointsAfter(kind, explanation));

        List<RawPoint> all = new ArrayList<>(before.size() + eventPoints.size() + after.size());
        all.addAll(before);
        all.addAll(eventPoints);
        all.addAll(after);

        TimelineExplanationPointClassifier.Result result = TimelineExplanationPointClassifier.classify(
                kind, start, end, explanation, config, all, location);
        List<Point> points = thin(result.points());
        boolean truncated = points.size() < result.points().size();

        return new TimelineEventExplanationDetailsDTO(
                kind.name(), id, start, end,
                TimelineExplanationDTO.from(storedExplanation),
                location,
                result.centre(),
                kind == Kind.STAY ? config.getStaypointRadiusMeters() : null,
                startPoint,
                endPoint,
                settings(config),
                TimelineExplanationPointClassifier.settingsChanged(explanation, config),
                points,
                result.filtered(),
                ingestionFilters(userId),
                truncated,
                eventPoints.size());
    }

    /**
     * The nearest {@link #CONTEXT_POINTS} points, or more until {@code usedNeeded} of them are points the engine
     * used, so the point that ended a stay is always there even after left-out ones.
     */
    private static List<RawPoint> contextPoints(List<RawPoint> nearestFirst, TimelineConfig config, int usedNeeded) {
        List<RawPoint> kept = new ArrayList<>();
        int used = 0;
        for (RawPoint point : nearestFirst) {
            if (kept.size() >= CONTEXT_POINTS && used >= usedNeeded) {
                break;
            }
            kept.add(point);
            if (TimelineGpsAccuracyFilter.shouldIncludeAccuracy(point.accuracyMeters(), config)) {
                used++;
            }
        }
        return kept;
    }

    /** A trip that ends with an arrival: the stop's points after the trip's last point belong to the next stay. */
    private static int arrivalPointsAfter(Kind kind, TimelineExplanation explanation) {
        if (kind != Kind.TRIP || explanation == null || explanation.getEnd() == null) {
            return 1;
        }
        TimelineReason end = explanation.getEnd();
        if (TimelineReasons.ARRIVAL.equals(end.code()) && end.params().get("points") instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 1;
    }

    /** Keeps every point outside the event, every left-out or deciding point, and evenly spaced others. */
    static List<Point> thin(List<Point> points) {
        List<Integer> ordinary = new ArrayList<>();
        int kept = 0;
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            if ("EVENT".equals(point.part()) && point.used() && point.reasons().isEmpty()) {
                ordinary.add(i);
            } else {
                kept++;
            }
        }
        int eventPoints = (int) points.stream().filter(point -> "EVENT".equals(point.part())).count();
        if (eventPoints <= MAX_EVENT_POINTS || ordinary.isEmpty()) {
            return points;
        }
        int room = Math.max(2, MAX_EVENT_POINTS - (kept - (points.size() - eventPoints)));
        // Evenly spaced, from the first ordinary point to the last
        Set<Integer> keep = new HashSet<>();
        for (int i = 0; i < room; i++) {
            keep.add(ordinary.get((int) Math.round((double) i * (ordinary.size() - 1) / (room - 1))));
        }
        Set<Integer> ordinarySet = new HashSet<>(ordinary);
        List<Point> thinned = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (!ordinarySet.contains(i) || keep.contains(i)) {
                thinned.add(points.get(i));
            }
        }
        return thinned;
    }

    private static Settings settings(TimelineConfig config) {
        return new Settings(
                config.getStaypointRadiusMeters(),
                config.getStaypointMinDurationMinutes(),
                TimelineGpsAccuracyFilter.getActiveMaxAccuracyThreshold(config),
                config.getStaypointVelocityThreshold(),
                config.getTripArrivalDetectionMinDurationSeconds(),
                config.getTripArrivalMinPoints(),
                config.getDataGapThresholdSeconds(),
                config.getDataGapMinDurationSeconds(),
                config.getIsMergeEnabled(),
                config.getMergeMaxDistanceMeters(),
                config.getMergeMaxTimeGapMinutes());
    }

    private List<IngestionFilter> ingestionFilters(UUID userId) {
        List<IngestionFilter> filters = new ArrayList<>();
        for (GpsSourceConfigEntity source : gpsSourceRepository.findActiveByUserId(userId)) {
            boolean accuracyOrSpeed = source.isFilterInaccurateData()
                    && (source.getMaxAllowedAccuracy() != null || source.getMaxAllowedSpeed() != null);
            boolean duplicates = source.isEnableDuplicateDetection();
            if (!accuracyOrSpeed && !duplicates) {
                continue;
            }
            filters.add(new IngestionFilter(
                    source.getSourceType() != null ? source.getSourceType().name() : null,
                    source.getUsername() != null ? source.getUsername() : source.getDeviceId(),
                    accuracyOrSpeed ? source.getMaxAllowedAccuracy() : null,
                    accuracyOrSpeed ? source.getMaxAllowedSpeed() : null,
                    duplicates
                            ? (source.getDuplicateDetectionThresholdMinutes() != null
                            ? source.getDuplicateDetectionThresholdMinutes()
                            : Integer.valueOf(defaultDuplicateThresholdMinutes))
                            : null));
        }
        return filters;
    }

    private static Coordinates coordinates(org.locationtech.jts.geom.Point point) {
        return point == null ? null : new Coordinates(point.getY(), point.getX());
    }
}
