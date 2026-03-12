package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitSuggestionDto;
import org.github.tess1o.geopulse.trips.model.entity.*;
import org.github.tess1o.geopulse.trips.repository.TripPlaceVisitMatchRepository;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;

import java.time.Instant;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class TripVisitAutoMatchService {

    private static final String DECISION_AUTO_MATCHED = "AUTO_MATCHED";
    private static final String DECISION_SUGGESTED = "SUGGESTED";
    private static final String DECISION_NO_MATCH = "NO_MATCH";
    private static final String DECISION_MANUAL_OVERRIDE = "MANUAL_OVERRIDE";
    private static final String DECISION_NO_COORDINATES = "NO_COORDINATES";
    private static final double DWELL_NORMALIZATION_SECONDS = 1800.0;
    private static final double DISTANCE_WEIGHT = 0.7;
    private static final double DWELL_WEIGHT = 0.3;
    private static final double EXACT_NAME_MIN_CONFIDENCE = 0.99;

    @ConfigProperty(name = "geopulse.trip.visit-matching.max-distance-meters", defaultValue = "400")
    double maxDistanceMeters;

    @ConfigProperty(name = "geopulse.trip.visit-matching.auto-threshold", defaultValue = "0.85")
    double autoThreshold;

    @ConfigProperty(name = "geopulse.trip.visit-matching.suggest-threshold", defaultValue = "0.55")
    double suggestThreshold;

    @ConfigProperty(name = "geopulse.trip.visit-matching.exact-name-boost-distance-meters", defaultValue = "120")
    double exactNameBoostDistanceMeters;

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final TripPlanItemRepository tripPlanItemRepository;
    private final TimelineStayRepository timelineStayRepository;
    private final TripPlaceVisitMatchRepository tripPlaceVisitMatchRepository;

    public TripVisitAutoMatchService(TripService tripService,
                                     TripRepository tripRepository,
                                     TripPlanItemRepository tripPlanItemRepository,
                                     TimelineStayRepository timelineStayRepository,
                                     TripPlaceVisitMatchRepository tripPlaceVisitMatchRepository) {
        this.tripService = tripService;
        this.tripRepository = tripRepository;
        this.tripPlanItemRepository = tripPlanItemRepository;
        this.timelineStayRepository = timelineStayRepository;
        this.tripPlaceVisitMatchRepository = tripPlaceVisitMatchRepository;
    }

    @Transactional
    public List<TripVisitSuggestionDto> evaluate(UUID userId, Long tripId, boolean applyAutoMatches) {
        TripEntity trip = tripService.getTripEntityOrThrow(userId, tripId);
        List<TripPlanItemEntity> planItems = tripPlanItemRepository.findByTripId(tripId);
        List<TimelineStayEntity> stays = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(
                userId, trip.getStartTime(), trip.getEndTime()
        );

        List<TripVisitSuggestionDto> suggestions = new ArrayList<>();

        for (TripPlanItemEntity item : planItems) {
            suggestions.add(matchItem(trip, item, stays, applyAutoMatches));
        }

        return suggestions;
    }

    @Transactional
    public void evaluateAllTrips(UUID userId, boolean applyAutoMatches) {
        List<TripEntity> trips = tripRepository.findByUserId(userId);
        for (TripEntity trip : trips) {
            evaluate(userId, trip.getId(), applyAutoMatches);
        }
    }

    @Transactional
    public List<TripVisitSuggestionDto> getStoredSuggestions(UUID userId, Long tripId) {
        tripService.getTripEntityOrThrow(userId, tripId);

        List<TripPlanItemEntity> planItems = tripPlanItemRepository.findByTripId(tripId);
        List<TripPlaceVisitMatchEntity> storedMatches = tripPlaceVisitMatchRepository.findByTripId(tripId);

        Map<Long, TripPlaceVisitMatchEntity> latestMatchByPlanItem = new HashMap<>();
        for (TripPlaceVisitMatchEntity match : storedMatches) {
            Long planItemId = match.getPlanItem() != null ? match.getPlanItem().getId() : null;
            if (planItemId != null && !latestMatchByPlanItem.containsKey(planItemId)) {
                latestMatchByPlanItem.put(planItemId, match);
            }
        }

        List<TripVisitSuggestionDto> suggestions = new ArrayList<>();
        for (TripPlanItemEntity item : planItems) {
            suggestions.add(toStoredSuggestion(item, latestMatchByPlanItem.get(item.getId())));
        }
        return suggestions;
    }

    private TripVisitSuggestionDto toStoredSuggestion(TripPlanItemEntity item, TripPlaceVisitMatchEntity match) {
        if (item.getLatitude() == null || item.getLongitude() == null) {
            return TripVisitSuggestionDto.builder()
                    .planItemId(item.getId())
                    .planItemTitle(item.getTitle())
                    .decision(DECISION_NO_COORDINATES)
                    .applied(false)
                    .reason("Plan item has no coordinates")
                    .build();
        }

        if (item.getManualOverrideState() != null) {
            return TripVisitSuggestionDto.builder()
                    .planItemId(item.getId())
                    .planItemTitle(item.getTitle())
                    .decision(DECISION_MANUAL_OVERRIDE)
                    .applied(false)
                    .reason("Manual override is set")
                    .build();
        }

        if (match == null) {
            return TripVisitSuggestionDto.builder()
                    .planItemId(item.getId())
                    .planItemTitle(item.getTitle())
                    .decision(DECISION_NO_MATCH)
                    .applied(false)
                    .reason("No matching stay found")
                    .build();
        }

        boolean applied = DECISION_AUTO_MATCHED.equals(match.getDecision())
                && Boolean.TRUE.equals(item.getIsVisited())
                && item.getVisitSource() == TripPlanItemVisitSource.AUTO;

        return TripVisitSuggestionDto.builder()
                .planItemId(item.getId())
                .planItemTitle(item.getTitle())
                .matchedStayId(match.getStay() != null ? match.getStay().getId() : null)
                .matchedLocationName(match.getStay() != null ? match.getStay().getLocationName() : null)
                .matchedStayStart(match.getStay() != null ? match.getStay().getTimestamp() : null)
                .matchedStayDurationSeconds(match.getDwellSeconds())
                .distanceMeters(match.getDistanceMeters())
                .confidence(match.getConfidence())
                .decision(match.getDecision())
                .applied(applied)
                .reason(DECISION_NO_MATCH.equals(match.getDecision()) ? "No matching stay found" : null)
                .build();
    }

    private TripVisitSuggestionDto matchItem(TripEntity trip,
                                             TripPlanItemEntity item,
                                             List<TimelineStayEntity> stays,
                                             boolean applyAutoMatches) {
        if (item.getLatitude() == null || item.getLongitude() == null) {
            return TripVisitSuggestionDto.builder()
                    .planItemId(item.getId())
                    .planItemTitle(item.getTitle())
                    .decision(DECISION_NO_COORDINATES)
                    .applied(false)
                    .reason("Plan item has no coordinates")
                    .build();
        }

        if (item.getManualOverrideState() != null) {
            return TripVisitSuggestionDto.builder()
                    .planItemId(item.getId())
                    .planItemTitle(item.getTitle())
                    .decision(DECISION_MANUAL_OVERRIDE)
                    .applied(false)
                    .reason("Manual override is set")
                    .build();
        }

        TimelineStayEntity bestStay = null;
        double bestDistance = Double.MAX_VALUE;
        double bestConfidence = 0.0;

        for (TimelineStayEntity stay : stays) {
            if (stay.getLocation() == null) {
                continue;
            }

            double stayLat = stay.getLocation().getY();
            double stayLon = stay.getLocation().getX();
            double distance = GeoUtils.haversine(item.getLatitude(), item.getLongitude(), stayLat, stayLon);
            if (distance > maxDistanceMeters) {
                continue;
            }

            double confidence = confidence(distance, stay.getStayDuration(), item.getTitle(), stay.getLocationName());
            if (confidence > bestConfidence || (confidence == bestConfidence && distance < bestDistance)) {
                bestStay = stay;
                bestConfidence = confidence;
                bestDistance = distance;
            }
        }

        if (bestStay == null || bestConfidence < suggestThreshold) {
            tripPlaceVisitMatchRepository.deleteByTripIdAndPlanItemId(trip.getId(), item.getId());
            tripPlaceVisitMatchRepository.persist(TripPlaceVisitMatchEntity.builder()
                    .trip(trip)
                    .planItem(item)
                    .stay(null)
                    .distanceMeters(null)
                    .dwellSeconds(null)
                    .confidence(0.0)
                    .decision(DECISION_NO_MATCH)
                    .build());

            return TripVisitSuggestionDto.builder()
                    .planItemId(item.getId())
                    .planItemTitle(item.getTitle())
                    .decision(DECISION_NO_MATCH)
                    .applied(false)
                    .reason("No matching stay found")
                    .build();
        }

        String decision = bestConfidence >= autoThreshold ? DECISION_AUTO_MATCHED : DECISION_SUGGESTED;
        boolean applied = false;

        tripPlaceVisitMatchRepository.deleteByTripIdAndPlanItemId(trip.getId(), item.getId());
        tripPlaceVisitMatchRepository.persist(TripPlaceVisitMatchEntity.builder()
                .trip(trip)
                .planItem(item)
                .stay(bestStay)
                .distanceMeters(bestDistance)
                .dwellSeconds(bestStay.getStayDuration())
                .confidence(bestConfidence)
                .decision(decision)
                .build());

        if (applyAutoMatches && DECISION_AUTO_MATCHED.equals(decision) && canAutoApply(item)) {
            item.setIsVisited(true);
            item.setVisitSource(TripPlanItemVisitSource.AUTO);
            item.setVisitConfidence(bestConfidence);
            item.setVisitedAt(bestStay.getTimestamp());
            applied = true;
        }

        return TripVisitSuggestionDto.builder()
                .planItemId(item.getId())
                .planItemTitle(item.getTitle())
                .matchedStayId(bestStay.getId())
                .matchedLocationName(bestStay.getLocationName())
                .matchedStayStart(bestStay.getTimestamp())
                .matchedStayDurationSeconds(bestStay.getStayDuration())
                .distanceMeters(bestDistance)
                .confidence(bestConfidence)
                .decision(decision)
                .applied(applied)
                .build();
    }

    private boolean canAutoApply(TripPlanItemEntity item) {
        return item.getManualOverrideState() == null &&
                item.getVisitSource() != TripPlanItemVisitSource.MANUAL;
    }

    private double confidence(double distanceMeters,
                              long stayDurationSeconds,
                              String planTitle,
                              String stayLocationName) {
        double distanceScore = Math.max(0.0, 1.0 - (distanceMeters / maxDistanceMeters));
        double dwellScore = Math.min(1.0, stayDurationSeconds / DWELL_NORMALIZATION_SECONDS);

        double normalizedDistanceWeight = DISTANCE_WEIGHT;
        double normalizedDwellWeight = DWELL_WEIGHT;
        double weightsSum = normalizedDistanceWeight + normalizedDwellWeight;
        if (weightsSum <= 0.0) {
            return (distanceScore + dwellScore) / 2.0;
        }

        double base = ((distanceScore * normalizedDistanceWeight)
                + (dwellScore * normalizedDwellWeight)) / weightsSum;

        if (isExactTitleMatch(planTitle, stayLocationName) && distanceMeters <= exactNameBoostDistanceMeters) {
            return Math.max(base, clamp01(EXACT_NAME_MIN_CONFIDENCE));
        }

        return clamp01(base);
    }

    private boolean isExactTitleMatch(String planTitle, String stayLocationName) {
        String plan = normalizeTitle(planTitle);
        String stay = normalizeTitle(stayLocationName);
        if (plan.isEmpty() || stay.isEmpty()) {
            return false;
        }
        return plan.equals(stay);
    }

    private String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }
        String withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String normalized = withoutDiacritics
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
