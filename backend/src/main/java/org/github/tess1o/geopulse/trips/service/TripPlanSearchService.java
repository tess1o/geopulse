package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchExternalStatus;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchResponseDto;
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

    public PlanSearchResponseDto search(UUID userId, String query, Double latitude, Double longitude, Integer limit) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return PlanSearchResponseDto.builder()
                    .results(List.of())
                    .externalStatus(PlanSearchExternalStatus.OK)
                    .build();
        }

        int safeLimit = clampLimit(limit);
        int localFetchLimit = Math.min(MAX_LIMIT, safeLimit * LOCAL_FETCH_MULTIPLIER);
        Point biasPoint = createBiasPoint(latitude, longitude);

        List<PlanSearchResultDto> combined = new ArrayList<>();
        combined.addAll(searchFavorites(userId, safeQuery, localFetchLimit));
        combined.addAll(searchGeocoding(userId, safeQuery, localFetchLimit));

        ExternalSearchOutcome external = searchExternal(safeQuery, biasPoint, localFetchLimit);
        combined.addAll(external.results());

        return PlanSearchResponseDto.builder()
                .results(dedupeAndLimit(combined, safeLimit))
                .externalStatus(external.status())
                .externalProvider(external.provider())
                .externalMessage(external.message())
                .build();
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

    /**
     * Outcome of the external leg: the results plus why it produced them (or did not).
     * Kept separate from the merged list so a provider problem never looks like an
     * empty search.
     */
    private record ExternalSearchOutcome(List<PlanSearchResultDto> results,
                                         PlanSearchExternalStatus status,
                                         String provider,
                                         String message) {
    }

    private ExternalSearchOutcome searchExternal(String query, Point biasPoint, int limit) {
        String primaryProvider = geocodingProviderFactory.getPrimaryProvider();
        try {
            List<GeocodingSearchResult> providerResults = geocodingProviderFactory
                    .forwardSearch(query, biasPoint, limit)
                    .await()
                    .indefinitely();

            if (providerResults == null || providerResults.isEmpty()) {
                // The provider answered. An empty list here is a genuine "nothing matched".
                return new ExternalSearchOutcome(List.of(), PlanSearchExternalStatus.OK, primaryProvider, null);
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

            return new ExternalSearchOutcome(results, PlanSearchExternalStatus.OK, primaryProvider, null);
        } catch (Exception e) {
            // Full trace, not just the message: this path used to be completely silent,
            // which is why a misconfigured instance was indistinguishable from no matches.
            log.warn("Forward provider search failed for query '{}' (primary='{}', fallback='{}')",
                    query, primaryProvider, geocodingProviderFactory.getFallbackProvider(), e);
            return classifyForwardSearchFailure(e, primaryProvider);
        }
    }

    /**
     * Maps a forward-search failure onto a user-actionable reason.
     *
     * <p>Message matching is used because {@code GeocodingProviderFactory.callProviderForward}
     * wraps every provider problem in a {@link org.github.tess1o.geopulse.geocoding.exception.GeocodingException}
     * carrying only a string. The cause chain is walked, and the whole chain is considered so a
     * wrapper does not hide the underlying reason.
     */
    private ExternalSearchOutcome classifyForwardSearchFailure(Throwable failure, String primaryProvider) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                chain.append(' ').append(t.getMessage());
            }
            if (t instanceof CircuitBreakerOpenException) {
                return new ExternalSearchOutcome(List.of(), PlanSearchExternalStatus.FAILED, primaryProvider,
                        "The place search provider is temporarily unavailable (circuit open). Try again shortly.");
            }
        }
        String reason = chain.toString().toLowerCase(Locale.ROOT);

        if (reason.contains("disabled for public host")) {
            return new ExternalSearchOutcome(List.of(), PlanSearchExternalStatus.DISABLED, primaryProvider,
                    "Forward search is disabled on the public Nominatim host. Enable "
                            + "'geocoding.nominatim.public-host-forward-search-enabled', or configure a "
                            + "self-hosted or fallback provider in Admin → Settings → Geocoding.");
        }
        if (reason.contains("is disabled") || reason.contains("not configured") || reason.contains("unknown provider")) {
            return new ExternalSearchOutcome(List.of(), PlanSearchExternalStatus.DISABLED, primaryProvider,
                    "No geocoding provider is available for place search. Enable one in "
                            + "Admin → Settings → Geocoding (a fallback provider is recommended).");
        }
        return new ExternalSearchOutcome(List.of(), PlanSearchExternalStatus.FAILED, primaryProvider,
                "Could not reach the place search provider. Check the geocoding provider settings and connectivity.");
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
