package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.dto.LocationLookupFavoriteDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationLookupMatchDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationLookupResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationLookupVisitDTO;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a user-selected map point against historical stays and saved
 * favorites. This deliberately keeps the matching policy separate from the
 * timeline generation algorithm: a map click is a user-facing recall query,
 * not a new timeline classification.
 */
@ApplicationScoped
public class TimelineLocationLookupService {
    public static final int MATCH_RADIUS_METERS = 100;
    public static final int AREA_BOUNDARY_TOLERANCE_METERS = 15;
    public static final int FALLBACK_LIMIT = 5;
    private static final int RECENT_VISIT_LIMIT = 5;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Transactional(Transactional.TxType.SUPPORTS)
    public LocationLookupResponseDTO lookup(java.util.UUID userId, double latitude, double longitude) {
        validateCoordinates(latitude, longitude);
        Point targetPoint = GeoUtils.createPoint(longitude, latitude);

        List<FavoritesEntity> favoriteEntities = favoritesRepository.findAllByPoint(
                userId,
                targetPoint,
                MATCH_RADIUS_METERS,
                AREA_BOUNDARY_TOLERANCE_METERS
        );

        Map<Long, FavoritesEntity> favoritesById = new LinkedHashMap<>();
        List<LocationLookupFavoriteDTO> favoriteMatches = new ArrayList<>();
        for (FavoritesEntity favorite : favoriteEntities) {
            favoritesById.put(favorite.getId(), favorite);
            favoriteMatches.add(toFavoriteMatch(favorite, latitude, longitude));
        }

        List<Object[]> stayRows = stayRepository.findLocationLookupStays(
                userId,
                targetPoint,
                MATCH_RADIUS_METERS,
                AREA_BOUNDARY_TOLERANCE_METERS
        );

        Set<Long> missingAreaIds = stayRows.stream()
                .map(row -> row.length > 9 ? LookupRow.numberAsLong(row[9]) : null)
                .filter(areaId -> areaId != null && !favoritesById.containsKey(areaId))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!missingAreaIds.isEmpty()) {
            for (FavoritesEntity favorite : favoritesRepository.findByIdsAndUserId(missingAreaIds, userId)) {
                favoritesById.put(favorite.getId(), favorite);
            }
        }

        Map<String, MatchAccumulator> accumulators = new LinkedHashMap<>();
        for (Object[] row : stayRows) {
            LookupRow lookupRow = LookupRow.from(row);
            String key;
            String sourceType;
            Long favoriteId = lookupRow.favoriteId;
            Long geocodingId = lookupRow.geocodingId;
            String name = lookupRow.locationName;
            String reason;

            if (lookupRow.matchedAreaId != null) {
                key = "favorite:" + lookupRow.matchedAreaId;
                sourceType = "favorite";
                favoriteId = lookupRow.matchedAreaId;
                geocodingId = null;
                FavoritesEntity favorite = favoritesById.get(favoriteId);
                if (favorite != null && favorite.getName() != null) {
                    name = favorite.getName();
                }
                reason = "Inside favorite area";
            } else if (favoriteId != null) {
                key = "favorite:" + favoriteId;
                sourceType = "favorite";
                FavoritesEntity favorite = favoritesById.get(favoriteId);
                if (favorite != null && favorite.getName() != null) {
                    name = favorite.getName();
                }
                reason = "Nearby recorded stay";
            } else if (geocodingId != null) {
                key = "geocoding:" + geocodingId;
                sourceType = "geocoding";
                reason = "Nearby recorded stay";
            } else {
                key = "stay:" + lookupRow.id;
                sourceType = "stay";
                reason = "Nearby recorded stay";
            }

            String matchSourceType = sourceType;
            Long matchFavoriteId = favoriteId;
            Long matchGeocodingId = geocodingId;
            String matchName = name;
            String matchReason = reason;
            MatchAccumulator accumulator = accumulators.computeIfAbsent(key, ignored ->
                    new MatchAccumulator(matchSourceType, matchFavoriteId, matchGeocodingId, matchName, matchReason));
            accumulator.add(lookupRow.toVisit());
        }

        List<LocationLookupMatchDTO> visitMatches = accumulators.values().stream()
                .sorted(Comparator.comparing(
                        MatchAccumulator::nearestDistance,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(MatchAccumulator::toDto)
                .toList();

        List<LocationLookupVisitDTO> nearestStays = List.of();
        if (visitMatches.isEmpty()) {
            nearestStays = stayRepository.findNearestLocationLookupStays(userId, targetPoint, FALLBACK_LIMIT)
                    .stream()
                    .map(row -> LookupRow.from(row).toVisit())
                    .sorted(Comparator.comparing(LocationLookupVisitDTO::getDistanceMeters))
                    .toList();
        }

        return LocationLookupResponseDTO.builder()
                .latitude(latitude)
                .longitude(longitude)
                .matchRadiusMeters(MATCH_RADIUS_METERS)
                .favoriteMatches(favoriteMatches)
                .visitMatches(visitMatches)
                .nearestStays(nearestStays)
                .build();
    }

    private LocationLookupFavoriteDTO toFavoriteMatch(FavoritesEntity favorite, double latitude, double longitude) {
        boolean area = favorite.getType() == FavoriteLocationType.AREA;
        double distance = 0;
        if (!area && favorite.getGeometry() instanceof Point point) {
            distance = GeoUtils.haversine(latitude, longitude, point.getY(), point.getX());
        }

        return LocationLookupFavoriteDTO.builder()
                .id(favorite.getId())
                .name(favorite.getName())
                .type(favorite.getType() != null ? favorite.getType().name().toLowerCase() : null)
                .relation(area ? "inside area" : "nearby point")
                .distanceMeters(distance)
                .build();
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("latitude and longitude must be valid geographic coordinates");
        }
    }

    private static final class MatchAccumulator {
        private final String sourceType;
        private final Long favoriteId;
        private final Long geocodingId;
        private final String name;
        private final String matchReason;
        private final Map<Long, LocationLookupVisitDTO> visitsById = new LinkedHashMap<>();
        private Double nearestDistanceMeters;

        private MatchAccumulator(String sourceType, Long favoriteId, Long geocodingId,
                                  String name, String matchReason) {
            this.sourceType = sourceType;
            this.favoriteId = favoriteId;
            this.geocodingId = geocodingId;
            this.name = name;
            this.matchReason = matchReason;
        }

        private void add(LocationLookupVisitDTO visit) {
            if (visit == null || visit.getId() == null || visitsById.containsKey(visit.getId())) {
                return;
            }
            visitsById.put(visit.getId(), visit);
            if (visit.getDistanceMeters() != null
                    && (nearestDistanceMeters == null || visit.getDistanceMeters() < nearestDistanceMeters)) {
                nearestDistanceMeters = visit.getDistanceMeters();
            }
        }

        private Double nearestDistance() {
            return nearestDistanceMeters;
        }

        private LocationLookupMatchDTO toDto() {
            List<LocationLookupVisitDTO> recentVisits = visitsById.values().stream()
                    .sorted(Comparator.comparing(LocationLookupVisitDTO::getTimestamp,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(RECENT_VISIT_LIMIT)
                    .toList();
            List<LocationLookupVisitDTO> allVisits = visitsById.values().stream()
                    .sorted(Comparator.comparing(LocationLookupVisitDTO::getTimestamp,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            return LocationLookupMatchDTO.builder()
                    .sourceType(sourceType)
                    .favoriteId(favoriteId)
                    .geocodingId(geocodingId)
                    .name(name)
                    .matchReason(matchReason)
                    .nearestDistanceMeters(nearestDistanceMeters)
                    .visitCount(allVisits.size())
                    .firstVisit(allVisits.isEmpty() ? null : allVisits.getFirst().getTimestamp())
                    .lastVisit(allVisits.isEmpty() ? null : allVisits.getLast().getTimestamp())
                    .visits(recentVisits)
                    .build();
        }
    }

    private static final class LookupRow {
        private final Long id;
        private final Instant timestamp;
        private final long stayDuration;
        private final String locationName;
        private final double latitude;
        private final double longitude;
        private final Long favoriteId;
        private final Long geocodingId;
        private final Double distanceMeters;
        private final Long matchedAreaId;

        private LookupRow(Long id, Instant timestamp, long stayDuration, String locationName,
                          double latitude, double longitude, Long favoriteId, Long geocodingId,
                          Double distanceMeters, Long matchedAreaId) {
            this.id = id;
            this.timestamp = timestamp;
            this.stayDuration = stayDuration;
            this.locationName = locationName;
            this.latitude = latitude;
            this.longitude = longitude;
            this.favoriteId = favoriteId;
            this.geocodingId = geocodingId;
            this.distanceMeters = distanceMeters;
            this.matchedAreaId = matchedAreaId;
        }

        private static LookupRow from(Object[] row) {
            return new LookupRow(
                    numberAsLong(row[0]),
                    TimestampUtils.getInstantSafe(row[1]),
                    numberAsLong(row[2]) != null ? numberAsLong(row[2]) : 0,
                    (String) row[3],
                    numberAsDouble(row[4]),
                    numberAsDouble(row[5]),
                    numberAsLong(row[6]),
                    numberAsLong(row[7]),
                    numberAsDoubleOrNull(row[8]),
                    row.length > 9 ? numberAsLong(row[9]) : null
            );
        }

        private LocationLookupVisitDTO toVisit() {
            return LocationLookupVisitDTO.builder()
                    .id(id)
                    .timestamp(timestamp)
                    .stayDuration(stayDuration)
                    .latitude(latitude)
                    .longitude(longitude)
                    .locationName(locationName)
                    .distanceMeters(distanceMeters)
                    .favoriteId(favoriteId)
                    .geocodingId(geocodingId)
                    .build();
        }

        static Long numberAsLong(Object value) {
            return value instanceof Number number ? number.longValue() : null;
        }

        private static double numberAsDouble(Object value) {
            return value instanceof Number number ? number.doubleValue() : 0;
        }

        private static Double numberAsDoubleOrNull(Object value) {
            return value instanceof Number number ? number.doubleValue() : null;
        }
    }
}
