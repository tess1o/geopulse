package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchResultDto;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class TripPlanSearchService {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final int LOCAL_FETCH_MULTIPLIER = 2;
    private static final double DUPLICATE_DISTANCE_METERS = 75.0;

    private final FavoritesRepository favoritesRepository;
    private final ReverseGeocodingLocationRepository geocodingRepository;
    private final GeocodingProviderFactory geocodingProviderFactory;

    public TripPlanSearchService(FavoritesRepository favoritesRepository,
                                 ReverseGeocodingLocationRepository geocodingRepository,
                                 GeocodingProviderFactory geocodingProviderFactory) {
        this.favoritesRepository = favoritesRepository;
        this.geocodingRepository = geocodingRepository;
        this.geocodingProviderFactory = geocodingProviderFactory;
    }

    public List<PlanSearchResultDto> search(UUID userId, String query, Double latitude, Double longitude, Integer limit) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return List.of();
        }

        int safeLimit = clampLimit(limit);
        int localFetchLimit = Math.min(MAX_LIMIT, safeLimit * LOCAL_FETCH_MULTIPLIER);
        Point biasPoint = createBiasPoint(latitude, longitude);

        List<PlanSearchResultDto> combined = new ArrayList<>();
        combined.addAll(searchFavorites(userId, safeQuery, localFetchLimit));
        combined.addAll(searchGeocoding(userId, safeQuery, localFetchLimit));
        combined.addAll(searchExternal(safeQuery, biasPoint, localFetchLimit));

        return dedupeAndLimit(combined, safeLimit);
    }

    private List<PlanSearchResultDto> searchFavorites(UUID userId, String query, int limit) {
        List<FavoritesEntity> favorites = favoritesRepository.findByUserIdAndNameContaining(userId, query, limit);
        List<PlanSearchResultDto> results = new ArrayList<>();

        for (FavoritesEntity favorite : favorites) {
            Point point = extractFavoritePoint(favorite);
            if (point == null) {
                continue;
            }

            boolean isArea = favorite.getType() == FavoriteLocationType.AREA;
            String sourceType = isArea ? "favorite-area" : "favorite-point";
            String favoriteType = isArea ? "area" : "point";
            String title = normalizeTitle(favorite.getName(), point);

            results.add(PlanSearchResultDto.builder()
                    .sourceType(sourceType)
                    .title(title)
                    .subtitle(joinParts(favorite.getCity(), favorite.getCountry()))
                    .latitude(point.getY())
                    .longitude(point.getX())
                    .favoriteId(favorite.getId())
                    .favoriteType(favoriteType)
                    .build());
        }

        return results;
    }

    private List<PlanSearchResultDto> searchGeocoding(UUID userId, String query, int limit) {
        List<ReverseGeocodingLocationEntity> geocoding = geocodingRepository.findByDisplayNameContaining(userId, query, limit);
        List<PlanSearchResultDto> results = new ArrayList<>();

        for (ReverseGeocodingLocationEntity entity : geocoding) {
            Point point = entity.getResultCoordinates() != null
                    ? entity.getResultCoordinates()
                    : entity.getRequestCoordinates();
            if (point == null) {
                continue;
            }

            results.add(PlanSearchResultDto.builder()
                    .sourceType("geocoding")
                    .title(normalizeTitle(entity.getDisplayName(), point))
                    .subtitle(joinParts(entity.getCity(), entity.getCountry(), entity.getProviderName()))
                    .latitude(point.getY())
                    .longitude(point.getX())
                    .geocodingId(entity.getId())
                    .providerName(entity.getProviderName())
                    .build());
        }

        return results;
    }

    private List<PlanSearchResultDto> searchExternal(String query, Point biasPoint, int limit) {
        try {
            List<GeocodingSearchResult> providerResults = geocodingProviderFactory
                    .forwardSearch(query, biasPoint, limit)
                    .await()
                    .indefinitely();

            if (providerResults == null || providerResults.isEmpty()) {
                return List.of();
            }

            List<PlanSearchResultDto> results = new ArrayList<>();
            for (GeocodingSearchResult providerResult : providerResults) {
                if (!hasCoordinates(providerResult.getLatitude(), providerResult.getLongitude())) {
                    continue;
                }

                results.add(PlanSearchResultDto.builder()
                        .sourceType("external-search")
                        .title(normalizeTitle(providerResult.getTitle(), providerResult.getLatitude(), providerResult.getLongitude()))
                        .subtitle(joinParts(providerResult.getCity(), providerResult.getCountry(), providerResult.getProviderName()))
                        .latitude(providerResult.getLatitude())
                        .longitude(providerResult.getLongitude())
                        .providerName(providerResult.getProviderName())
                        .build());
            }

            return results;
        } catch (Exception e) {
            log.warn("Forward provider search failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private List<PlanSearchResultDto> dedupeAndLimit(List<PlanSearchResultDto> candidates, int limit) {
        List<PlanSearchResultDto> unique = new ArrayList<>();
        for (PlanSearchResultDto candidate : candidates) {
            if (candidate == null) {
                continue;
            }

            if (isDuplicate(candidate, unique)) {
                continue;
            }

            unique.add(candidate);
            if (unique.size() >= limit) {
                break;
            }
        }
        return unique;
    }

    private boolean isDuplicate(PlanSearchResultDto candidate, List<PlanSearchResultDto> existing) {
        for (PlanSearchResultDto item : existing) {
            if (sameEntity(candidate, item)) {
                return true;
            }

            String leftName = normalizeName(candidate.getTitle());
            String rightName = normalizeName(item.getTitle());
            if (!leftName.equals(rightName) || leftName.isEmpty()) {
                continue;
            }

            if (hasCoordinates(candidate.getLatitude(), candidate.getLongitude())
                    && hasCoordinates(item.getLatitude(), item.getLongitude())) {
                double distance = GeoUtils.haversine(
                        candidate.getLatitude(),
                        candidate.getLongitude(),
                        item.getLatitude(),
                        item.getLongitude()
                );
                if (distance <= DUPLICATE_DISTANCE_METERS) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean sameEntity(PlanSearchResultDto left, PlanSearchResultDto right) {
        if (left.getFavoriteId() != null && right.getFavoriteId() != null) {
            return left.getFavoriteId().equals(right.getFavoriteId());
        }
        if (left.getGeocodingId() != null && right.getGeocodingId() != null) {
            return left.getGeocodingId().equals(right.getGeocodingId());
        }
        return false;
    }

    private Point createBiasPoint(Double latitude, Double longitude) {
        if (!hasCoordinates(latitude, longitude)) {
            return null;
        }
        try {
            return GeoUtils.createPoint(longitude, latitude);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Point extractFavoritePoint(FavoritesEntity favorite) {
        Geometry geometry = favorite.getGeometry();
        if (geometry == null) {
            return null;
        }

        if (geometry instanceof Point point) {
            return point;
        }

        Point centroid = geometry.getCentroid();
        return centroid == null || centroid.isEmpty() ? null : centroid;
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private String normalizeTitle(String title, Point point) {
        return normalizeTitle(title, point.getY(), point.getX());
    }

    private String normalizeTitle(String title, Double latitude, Double longitude) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return String.format(Locale.US, "Planned place (%.5f, %.5f)", latitude, longitude);
    }

    private boolean hasCoordinates(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String joinParts(String... values) {
        List<String> parts = new ArrayList<>();
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            parts.add(value.trim());
        }

        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" • ", parts);
    }
}
