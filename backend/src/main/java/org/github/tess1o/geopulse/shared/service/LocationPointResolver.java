package org.github.tess1o.geopulse.shared.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.locationtech.jts.geom.Point;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class LocationPointResolver {

    private final GeocodingService geocodingService;
    private final FavoriteLocationService favoriteLocationService;
    private final CacheGeocodingService cacheGeocodingService;

    @Inject
    public LocationPointResolver(GeocodingService geocodingService, 
                               FavoriteLocationService favoriteLocationService,
                               CacheGeocodingService cacheGeocodingService) {
        this.geocodingService = geocodingService;
        this.favoriteLocationService = favoriteLocationService;
        this.cacheGeocodingService = cacheGeocodingService;
    }

    /**
     * Enhanced location resolution that returns both the name and source references.
     * This method provides referential integrity for timeline persistence.
     */
    public LocationResolutionResult resolveLocationWithReferences(UUID userId, Point point) {
        FavoriteLocationsDto favorite = favoriteLocationService.findByPoint(userId, point);

        if (favorite != null && (!favorite.getPoints().isEmpty() || !favorite.getAreas().isEmpty())) {
            // Found a favorite location
            if (!favorite.getPoints().isEmpty()) {
                var favoritePoint = favorite.getPoints().getFirst();
                return LocationResolutionResult.fromFavorite(favoritePoint.getName(), favoritePoint.getId());
            } else {
                var favoriteArea = favorite.getAreas().getFirst();
                return LocationResolutionResult.fromFavorite(favoriteArea.getName(), favoriteArea.getId());
            }
        }

        // No favorite found, use geocoding
        FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(point);
        
        // Get the entity ID of the cached result (getLocationName already cached it)
        Long geocodingId = cacheGeocodingService.getCachedGeocodingResultId(point).orElse(null);
        
        return LocationResolutionResult.fromGeocoding(
                geocodingResult.getFormattedDisplayName(), 
                geocodingId
        );
    }

    /**
     * Batch location resolution with rate limiting for external geocoding API.
     * Processes multiple coordinates efficiently while respecting API limits.
     *
     * @param userId User ID for favorite location lookup
     * @param coordinates List of coordinates to resolve
     * @return Map of coordinate string (lon,lat) to LocationResolutionResult
     */
    public Map<String, LocationResolutionResult> resolveLocationsWithReferencesBatch(
            UUID userId, List<Point> coordinates) {
        
        if (coordinates == null || coordinates.isEmpty()) {
            return Map.of();
        }

        log.debug("Batch resolving {} coordinates for user {}", coordinates.size(), userId);

        // Step 1: Check for favorite locations first (these are fast)
        Map<String, LocationResolutionResult> results = new java.util.HashMap<>();
        List<Point> needGeocoding = new java.util.ArrayList<>();

        for (Point point : coordinates) {
            FavoriteLocationsDto favorite = favoriteLocationService.findByPoint(userId, point);
            String coordKey = point.getX() + "," + point.getY();

            if (favorite != null && (!favorite.getPoints().isEmpty() || !favorite.getAreas().isEmpty())) {
                // Found a favorite location
                if (!favorite.getPoints().isEmpty()) {
                    var favoritePoint = favorite.getPoints().getFirst();
                    results.put(coordKey, LocationResolutionResult.fromFavorite(
                        favoritePoint.getName(), favoritePoint.getId()));
                } else if (!favorite.getAreas().isEmpty()) {
                    var favoriteArea = favorite.getAreas().getFirst();
                    results.put(coordKey, LocationResolutionResult.fromFavorite(
                        favoriteArea.getName(), favoriteArea.getId()));
                } else {
                    needGeocoding.add(point);
                }
            } else {
                needGeocoding.add(point);
            }
        }

        if (needGeocoding.isEmpty()) {
            log.debug("All {} coordinates resolved from favorites", coordinates.size());
            return results;
        }

        log.debug("Found {} favorites, {} coordinates need geocoding", results.size(), needGeocoding.size());

        // Step 2: Batch lookup cached geocoding results
        Map<String, FormattableGeocodingResult> cachedResults = 
            cacheGeocodingService.getCachedGeocodingResultsBatch(needGeocoding);
        Map<String, Long> cachedIds = 
            cacheGeocodingService.getCachedGeocodingResultIdsBatch(needGeocoding);

        log.debug("Found {} cached geocoding results", cachedResults.size());

        List<Point> needExternalGeocoding = new java.util.ArrayList<>();

        for (Point point : needGeocoding) {
            String coordKey = point.getX() + "," + point.getY();
            
            if (cachedResults.containsKey(coordKey)) {
                FormattableGeocodingResult cached = cachedResults.get(coordKey);
                Long cachedId = cachedIds.get(coordKey);
                results.put(coordKey, LocationResolutionResult.fromGeocoding(
                    cached.getFormattedDisplayName(), cachedId));
            } else {
                needExternalGeocoding.add(point);
            }
        }

        // Step 3: Process external geocoding with rate limiting (1 req/sec max)
        if (!needExternalGeocoding.isEmpty()) {
            log.debug("Processing {} external geocoding requests with rate limiting", needExternalGeocoding.size());
            processExternalGeocodingWithRateLimit(needExternalGeocoding, results);
        }

        // Step 4: Ensure all coordinates have results (fallback for any missing)
        int fallbackCount = 0;
        for (Point point : coordinates) {
            String coordKey = point.getX() + "," + point.getY();
            if (!results.containsKey(coordKey)) {
                log.warn("No result found for coordinate {}, using fallback", coordKey);
                results.put(coordKey, LocationResolutionResult.fromGeocoding("Unknown Location", null));
                fallbackCount++;
            }
        }

        log.debug("Batch resolution completed: {} favorites, {} cached, {} external, {} fallbacks", 
                  coordinates.size() - needGeocoding.size(),
                  cachedResults.size(),
                  needExternalGeocoding.size() - fallbackCount,
                  fallbackCount);

        return results;
    }

    /**
     * Process external geocoding requests with rate limiting.
     * Respects the 1 request per second limit for geocoding APIs.
     */
    private void processExternalGeocodingWithRateLimit(List<Point> coordinates, 
                                                      Map<String, LocationResolutionResult> results) {
        
        for (int i = 0; i < coordinates.size(); i++) {
            Point point = coordinates.get(i);
            String coordKey = point.getX() + "," + point.getY();
            
            try {
                // Rate limiting: wait 1 second between requests (except for the first one)
                if (i > 0) {
                    Thread.sleep(1000);
                }
                
                FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(point);
                Long geocodingId = cacheGeocodingService.getCachedGeocodingResultId(point).orElse(null);
                
                results.put(coordKey, LocationResolutionResult.fromGeocoding(
                    geocodingResult.getFormattedDisplayName(), geocodingId));
                    
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error but continue processing other coordinates
                log.warn("Failed to geocode coordinates {}: {}", coordKey, e.getMessage());
                results.put(coordKey, LocationResolutionResult.fromGeocoding("Unknown Location", null));
            }
        }
    }
}
