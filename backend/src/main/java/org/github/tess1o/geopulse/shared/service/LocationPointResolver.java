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
     * @param userId      User ID for favorite location lookup
     * @param coordinates List of coordinates to resolve
     * @return Map of coordinate string (lon,lat) to LocationResolutionResult
     */
    public Map<String, LocationResolutionResult> resolveLocationsWithReferencesBatch(
            UUID userId, List<Point> coordinates) {

        if (coordinates == null || coordinates.isEmpty()) {
            return Map.of();
        }

        long startTime = System.currentTimeMillis();

        log.debug("Batch resolving {} coordinates for user {}", coordinates.size(), userId);

        // Step 1: Batch check for favorite locations using true batch processing
        Map<String, FavoriteLocationsDto> favoriteResults = favoriteLocationService.findByPointsBatch(userId, coordinates);
        
        Map<String, LocationResolutionResult> results = new java.util.HashMap<>();
        List<Point> needGeocoding = new java.util.ArrayList<>();

        for (Point point : coordinates) {
            String coordKey = point.getX() + "," + point.getY();
            FavoriteLocationsDto favorite = favoriteResults.get(coordKey);

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

        log.debug("Found {} favorites, {} coordinates need geocoding. This process took {} s", results.size(), needGeocoding.size(), (System.currentTimeMillis() - startTime) / 1000.0d);

        long step2StartTime = System.currentTimeMillis();

        // Step 2: Batch lookup cached geocoding results
        Map<String, FormattableGeocodingResult> cachedResults =
                cacheGeocodingService.getCachedGeocodingResultsBatch(needGeocoding);
        Map<String, Long> cachedIds =
                cacheGeocodingService.getCachedGeocodingResultIdsBatch(needGeocoding);

        log.debug("Found {} cached geocoding results in {} s", cachedResults.size(), (System.currentTimeMillis() - step2StartTime) / 1000.0d);

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

        // Step 3: Fallback individual cache lookup for missing coordinates
        // This is a temporary fix while debugging the batch query issue
        List<Point> stillNeedExternal = new java.util.ArrayList<>();
        
        for (Point point : needExternalGeocoding) {
            String coordKey = point.getX() + "," + point.getY();
            
            // Try individual cache lookup as fallback
            java.util.Optional<org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult> individualResult = 
                cacheGeocodingService.getCachedGeocodingResult(point);
            java.util.Optional<Long> individualId = cacheGeocodingService.getCachedGeocodingResultId(point);
            
            if (individualResult.isPresent()) {
                log.debug("Batch missed but individual found cache for: {}", coordKey);
                results.put(coordKey, LocationResolutionResult.fromGeocoding(
                    individualResult.get().getFormattedDisplayName(), individualId.orElse(null)));
            } else {
                stillNeedExternal.add(point);
            }
        }
        
        log.debug("After individual fallback: {} coordinates still need external geocoding (batch missed {})", 
                 stillNeedExternal.size(), needExternalGeocoding.size() - stillNeedExternal.size());

        // Step 4: Process truly external geocoding with rate limiting (1 req/sec max)
        long step3StartTime = System.currentTimeMillis();
        if (!stillNeedExternal.isEmpty()) {
            log.debug("Processing {} external geocoding requests with rate limiting", stillNeedExternal.size());
            processExternalGeocodingWithRateLimit(stillNeedExternal, results);
        }

        log.debug("External geocoding requests processed in {} s", (System.currentTimeMillis() - step3StartTime) / 1000.0d);

        // Step 5: Ensure all coordinates have results (fallback for any missing)
        int fallbackCount = 0;
        for (Point point : coordinates) {
            String coordKey = point.getX() + "," + point.getY();
            if (!results.containsKey(coordKey)) {
                log.warn("No result found for coordinate {}, using fallback", coordKey);
                results.put(coordKey, LocationResolutionResult.fromGeocoding("Unknown Location", null));
                fallbackCount++;
            }
        }

        log.debug("Batch resolution completed: {} favorites, {} cached, {} external, {} fallbacks, took {} s",
                coordinates.size() - needGeocoding.size(),
                cachedResults.size(),
                needExternalGeocoding.size() - fallbackCount,
                fallbackCount,
                (System.currentTimeMillis() - startTime) / 1000.0d);

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

                FormattableGeocodingResult geocodingResult;
                try {
                    geocodingResult = geocodingService.getLocationName(point);
                } catch (Exception geocodingError) {
                    // Geocoding failed - use fallback to prevent transaction rollback
                    log.warn("Geocoding failed for {}, using fallback: {}", coordKey, geocodingError.getMessage());
                    geocodingResult = org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult.builder()
                            .requestCoordinates(point)
                            .resultCoordinates(point)
                            .formattedDisplayName(String.format("Location unavailable (%.6f, %.6f)", point.getY(), point.getX()))
                            .providerName("fallback-error")
                            .build();
                }

                Long geocodingId = cacheGeocodingService.getCachedGeocodingResultId(point).orElse(null);

                results.put(coordKey, LocationResolutionResult.fromGeocoding(
                        geocodingResult.getFormattedDisplayName(), geocodingId));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error but continue processing other coordinates
                log.warn("Failed to process coordinates {}: {}", coordKey, e.getMessage());
                results.put(coordKey, LocationResolutionResult.fromGeocoding("Unknown Location", null));
            }
        }
    }
}
