package org.github.tess1o.geopulse.shared.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
    org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService jobProgressService;

    @ConfigProperty(name = "geocoding.provider.delay.ms", defaultValue = "1000")
    private long geocodingProviderDelayMs;

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
        Long geocodingId = cacheGeocodingService.getCachedGeocodingResultId(userId, point).orElse(null);

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
        return resolveLocationsWithReferencesBatch(userId, coordinates, null);
    }

    /**
     * Batch location resolution with rate limiting for external geocoding API and progress tracking.
     * Processes multiple coordinates efficiently while respecting API limits.
     *
     * @param userId      User ID for favorite location lookup
     * @param coordinates List of coordinates to resolve
     * @param jobId       Optional job ID for progress tracking
     * @return Map of coordinate string (lon,lat) to LocationResolutionResult
     */
    public Map<String, LocationResolutionResult> resolveLocationsWithReferencesBatch(
            UUID userId, List<Point> coordinates, UUID jobId) {

        if (coordinates == null || coordinates.isEmpty()) {
            return Map.of();
        }

        long startTime = System.currentTimeMillis();

        // Deduplicate coordinates while preserving order (LinkedHashSet)
        // Multiple stays can have the same centroid coordinates
        List<Point> uniqueCoordinates = new java.util.ArrayList<>(
            new java.util.LinkedHashSet<>(coordinates)
        );

        int inputCount = coordinates.size();
        int uniqueCount = uniqueCoordinates.size();

        if (inputCount > uniqueCount) {
            log.debug("Deduplicated {} coordinates to {} unique locations", inputCount, uniqueCount);
        }

        log.debug("Batch resolving {} unique coordinates for user {}", uniqueCount, userId);

        int totalLocations = uniqueCount;
        updateGeocodingProgress(jobId, "Starting location resolution", totalLocations, 0, 0, 0, 0, 0);

        // Step 1: Batch check for favorite locations using true batch processing
        Map<String, FavoriteLocationsDto> favoriteResults = favoriteLocationService.findByPointsBatch(userId, uniqueCoordinates);

        Map<String, LocationResolutionResult> results = new java.util.HashMap<>();
        List<Point> needGeocoding = new java.util.ArrayList<>();

        for (Point point : uniqueCoordinates) {
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
            log.debug("All {} unique coordinates resolved from favorites", uniqueCount);
            updateGeocodingProgress(jobId, "All locations resolved from favorites",
                totalLocations, results.size(), 0, 0, 0, totalLocations);
            return results;
        }

        int favoritesResolved = results.size();
        log.debug("Found {} favorites, {} coordinates need geocoding. This process took {} s", favoritesResolved, needGeocoding.size(), (System.currentTimeMillis() - startTime) / 1000.0d);

        updateGeocodingProgress(jobId, "Resolved " + favoritesResolved + " locations from favorites",
            totalLocations, favoritesResolved, 0, needGeocoding.size(), 0, favoritesResolved);

        long step2StartTime = System.currentTimeMillis();

        // Step 2: Batch lookup cached geocoding results with user filtering
        Map<String, FormattableGeocodingResult> cachedResults =
                cacheGeocodingService.getCachedGeocodingResultsBatch(userId, needGeocoding);
        Map<String, Long> cachedIds =
                cacheGeocodingService.getCachedGeocodingResultIdsBatch(userId, needGeocoding);

        log.debug("Found {} cached geocoding results for user {} in {} s",
                cachedResults.size(), userId, (System.currentTimeMillis() - step2StartTime) / 1000.0d);

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

            // Try individual cache lookup as fallback with user filtering
            java.util.Optional<org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult> individualResult =
                cacheGeocodingService.getCachedGeocodingResult(userId, point);
            java.util.Optional<Long> individualId = cacheGeocodingService.getCachedGeocodingResultId(userId, point);

            if (individualResult.isPresent()) {
                log.debug("Batch missed but individual found cache for user {} at: {}", userId, coordKey);
                results.put(coordKey, LocationResolutionResult.fromGeocoding(
                    individualResult.get().getFormattedDisplayName(), individualId.orElse(null)));
            } else {
                stillNeedExternal.add(point);
            }
        }
        
        int cachedResolved = results.size() - favoritesResolved;
        int batchCacheMisses = needExternalGeocoding.size() - stillNeedExternal.size();
        log.debug("After individual fallback: {} coordinates still need external geocoding (batch missed {})",
                 stillNeedExternal.size(), batchCacheMisses);

        updateGeocodingProgress(jobId,
            String.format("Resolved %d from cache (%d from batch, %d from individual lookup)",
                cachedResolved, cachedResults.size(), batchCacheMisses),
            totalLocations, favoritesResolved, cachedResolved, stillNeedExternal.size(), 0, results.size());

        // Step 4: Process truly external geocoding with rate limiting (1 req/sec max)
        long step3StartTime = System.currentTimeMillis();
        if (!stillNeedExternal.isEmpty()) {
            log.debug("Processing {} external geocoding requests with rate limiting for user {}", stillNeedExternal.size(), userId);
            processExternalGeocodingWithRateLimit(stillNeedExternal, results, userId, jobId, totalLocations, favoritesResolved, cachedResolved);
        }

        log.debug("External geocoding requests processed in {} s", (System.currentTimeMillis() - step3StartTime) / 1000.0d);

        // Step 5: Ensure all coordinates have results (fallback for any missing)
        int fallbackCount = 0;
        for (Point point : uniqueCoordinates) {
            String coordKey = point.getX() + "," + point.getY();
            if (!results.containsKey(coordKey)) {
                log.warn("No result found for coordinate {}, using fallback", coordKey);
                results.put(coordKey, LocationResolutionResult.fromGeocoding("Unknown Location", null));
                fallbackCount++;
            }
        }

        // Calculate final counts
        int finalCachedResolved = results.size() - favoritesResolved - fallbackCount;
        int externalCompleted = results.size() - favoritesResolved - finalCachedResolved - fallbackCount;

        // Ensure counts are accurate (externalCompleted might be negative due to duplicates being filtered)
        if (externalCompleted < 0) {
            finalCachedResolved += externalCompleted;
            externalCompleted = 0;
        }

        log.debug("Batch resolution completed: {} favorites, {} cached, {} external, {} fallbacks, took {} s",
                favoritesResolved,
                finalCachedResolved,
                externalCompleted,
                fallbackCount,
                (System.currentTimeMillis() - startTime) / 1000.0d);

        // Final progress update - all locations resolved
        updateGeocodingProgress(jobId,
            String.format("Geocoding complete: %d favorites, %d cached, %d external API calls",
                favoritesResolved, finalCachedResolved, externalCompleted),
            totalLocations, favoritesResolved, finalCachedResolved, 0, externalCompleted, results.size());

        return results;
    }

    /**
     * Process external geocoding requests with rate limiting.
     * Respects the 1 request per second limit for geocoding APIs.
     */
    private void processExternalGeocodingWithRateLimit(List<Point> coordinates,
                                                       Map<String, LocationResolutionResult> results,
                                                       UUID userId, UUID jobId, int totalLocations,
                                                       int favoritesResolved, int cachedResolved) {

        long delayMs = geocodingProviderDelayMs < 0 ? 0 : geocodingProviderDelayMs;

        for (int i = 0; i < coordinates.size(); i++) {
            Point point = coordinates.get(i);
            String coordKey = point.getX() + "," + point.getY();

            try {
                // Rate limiting: wait 1 second between requests (except for the first one)
                if (i > 0) {
                    Thread.sleep(delayMs);
                }

                // Report progress for each external geocoding request
                int remaining = coordinates.size() - i;
                int currentExternalCompleted = results.size() - favoritesResolved - cachedResolved;
                updateGeocodingProgress(jobId,
                    String.format("Geocoding location %d/%d", i + 1, coordinates.size()),
                    totalLocations, favoritesResolved, cachedResolved, remaining, currentExternalCompleted, results.size());

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

                    // Cache the fallback result to prevent retry loops on subsequent runs
                    try {
                        cacheGeocodingService.cacheGeocodingResult(geocodingResult);
                        log.debug("Cached fallback result for {}", coordKey);
                    } catch (Exception cacheError) {
                        log.warn("Failed to cache fallback result for {}: {}", coordKey, cacheError.getMessage());
                    }
                }

                Long geocodingId = cacheGeocodingService.getCachedGeocodingResultId(userId, point).orElse(null);

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

    /**
     * Helper method to update geocoding progress if job tracking is enabled
     */
    private void updateGeocodingProgress(UUID jobId, String message, int totalLocations,
                                        int favoritesResolved, int cachedResolved,
                                        int externalPending, int externalCompleted, int totalResolved) {
        if (jobId != null) {
            // Progress from 40% to 65% during geocoding (happens in step 4, not step 7!)
            int progress = 40 + (int)((double)totalResolved / totalLocations * 25);

            Map<String, Object> details = new java.util.HashMap<>();
            details.put("totalLocations", totalLocations);
            details.put("favoritesResolved", favoritesResolved);
            details.put("cachedResolved", cachedResolved);
            details.put("externalPending", externalPending);
            details.put("externalCompleted", Math.max(0, externalCompleted));
            details.put("totalResolved", totalResolved);

            jobProgressService.updateProgress(jobId, message, 4, progress, details);
        }
    }
}
