package org.github.tess1o.geopulse.shared.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingBatchService;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.locationtech.jts.geom.Point;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class LocationPointResolver {

    private final GeocodingService geocodingService;
    private final FavoriteLocationService favoriteLocationService;
    private final CacheGeocodingService cacheGeocodingService;
    private final CacheGeocodingBatchService batchService;
    private final ReverseGeocodingManagementService geocodingManagementService;
    private final GeocodingConfigurationService geocodingConfigurationService;

    @Inject
    org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService jobProgressService;

    @Inject
    public LocationPointResolver(GeocodingService geocodingService,
                                 FavoriteLocationService favoriteLocationService,
                                 CacheGeocodingService cacheGeocodingService,
                                 CacheGeocodingBatchService batchService,
                                 ReverseGeocodingManagementService geocodingManagementService,
                                 GeocodingConfigurationService geocodingConfigurationService) {
        this.geocodingService = geocodingService;
        this.favoriteLocationService = favoriteLocationService;
        this.cacheGeocodingService = cacheGeocodingService;
        this.batchService = batchService;
        this.geocodingManagementService = geocodingManagementService;
        this.geocodingConfigurationService = geocodingConfigurationService;
    }

    /**
     * Enhanced location resolution that returns both the name and source references.
     * This method provides referential integrity for timeline persistence.
     */
    public LocationResolutionResult resolveLocationWithReferences(UUID userId, Point point) {
        FavoriteLocationsDto favorite = favoriteLocationService.findByPoint(userId, point);

        if (favorite != null && (!favorite.getPoints().isEmpty() || !favorite.getAreas().isEmpty())) {
            // Found a favorite location. Prefer an area when both kinds are
            // returned: the area expresses the user's broader place intent.
            if (!favorite.getAreas().isEmpty()) {
                var favoriteArea = favorite.getAreas().getFirst();
                return LocationResolutionResult.fromFavorite(favoriteArea.getName(), favoriteArea.getId());
            } else if (!favorite.getPoints().isEmpty()) {
                var favoritePoint = favorite.getPoints().getFirst();
                return LocationResolutionResult.fromFavorite(
                        favoritePoint.getName(),
                        favoritePoint.getId(),
                        favoritePoint.getLatitude(),
                        favoritePoint.getLongitude()
                );
            }
        }

        // No favorite found, reuse cached geocoding before making an external provider call.
        var cachedPlace = cacheGeocodingService.getCachedPlace(userId, point);
        if (cachedPlace.isPresent()) {
            return LocationResolutionResult.fromGeocoding(
                    cachedPlace.get().displayName(),
                    normalizeGeocodingReference(userId, cachedPlace.get().id())
            );
        }

        // No favorite or cache hit found, use geocoding provider fallback.
        FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(point);

        // Get the entity ID of the cached result (getLocationName already cached it)
        Long geocodingId = cacheGeocodingService.getCachedGeocodingResultId(userId, point).orElse(null);
        geocodingId = normalizeGeocodingReference(userId, geocodingId);

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
     * Batch location resolution with rate limiting for external geocoding API and progress tracking, in one chunk.
     * For many coordinates, resolve them chunk by chunk ({@link #startBatch}, {@link #resolveChunk},
     * {@link #finishBatch}) so the lookups and results of only one chunk are in memory at a time.
     *
     * @param userId      User ID for favorite location lookup
     * @param coordinates List of coordinates to resolve
     * @param jobId       Optional job ID for progress tracking
     * @return Map of {@link #coordinateKey} to LocationResolutionResult
     */
    public Map<String, LocationResolutionResult> resolveLocationsWithReferencesBatch(
            UUID userId, List<Point> coordinates, UUID jobId) {
        if (coordinates == null || coordinates.isEmpty()) {
            return Map.of();
        }
        List<Point> uniqueCoordinates = new ArrayList<>(new LinkedHashSet<>(coordinates));
        LocationBatch batch = startBatch(userId, jobId, uniqueCoordinates.size());
        Map<String, LocationResolutionResult> results = resolveChunk(batch, uniqueCoordinates);
        finishBatch(batch);
        return results;
    }

    /** The key of a coordinate in the maps {@link #resolveChunk} returns: "longitude,latitude". */
    public static String coordinateKey(Point point) {
        return point.getX() + "," + point.getY();
    }

    /**
     * Begins resolving {@code totalLocations} coordinates in chunks.
     *
     * @param jobId optional job ID for progress tracking; progress runs from 55% to 70% over all chunks
     */
    public LocationBatch startBatch(UUID userId, UUID jobId, int totalLocations) {
        LocationBatch batch = new LocationBatch(userId, jobId, totalLocations);
        updateGeocodingProgress(batch, "startingLocationResolution", "Starting location resolution", 0);
        return batch;
    }

    /**
     * Resolves one chunk of a batch: favorites first, then the geocoding cache, then the external provider.
     * Coordinates repeated within the chunk are resolved once.
     *
     * @return the chunk's results, keyed by {@link #coordinateKey}
     */
    public Map<String, LocationResolutionResult> resolveChunk(LocationBatch batch, List<Point> coordinates) {
        if (coordinates.isEmpty()) {
            return Map.of();
        }
        long startTime = System.currentTimeMillis();
        UUID userId = batch.userId;
        List<Point> uniqueCoordinates = new ArrayList<>(new LinkedHashSet<>(coordinates));
        log.debug("Resolving {} unique coordinates for user {}", uniqueCoordinates.size(), userId);

        // Step 1: Batch check for favorite locations
        Map<String, FavoriteLocationsDto> favoriteResults = favoriteLocationService.findByPointsBatch(userId, uniqueCoordinates);

        Map<String, LocationResolutionResult> results = new HashMap<>();
        List<Point> needGeocoding = new ArrayList<>();

        for (Point point : uniqueCoordinates) {
            String coordKey = coordinateKey(point);
            FavoriteLocationsDto favorite = favoriteResults.get(coordKey);

            // Areas take precedence over point favorites because a stay can be near a saved point while still
            // belonging to a larger saved area.
            if (favorite != null && !favorite.getAreas().isEmpty()) {
                var favoriteArea = favorite.getAreas().getFirst();
                results.put(coordKey, LocationResolutionResult.fromFavorite(favoriteArea.getName(), favoriteArea.getId()));
            } else if (favorite != null && !favorite.getPoints().isEmpty()) {
                var favoritePoint = favorite.getPoints().getFirst();
                results.put(coordKey, LocationResolutionResult.fromFavorite(
                        favoritePoint.getName(),
                        favoritePoint.getId(),
                        favoritePoint.getLatitude(),
                        favoritePoint.getLongitude()));
            } else {
                needGeocoding.add(point);
            }
        }
        batch.favoritesResolved += results.size();

        if (needGeocoding.isEmpty()) {
            return results;
        }

        updateGeocodingProgress(batch, "resolvedFromFavorites", "Resolved " + batch.favoritesResolved + " locations from favorites",
                needGeocoding.size());

        // Step 2: Batch lookup cached places (id and display name) with user filtering
        CacheGeocodingBatchService.CachedPlaces cached = batchService.getCachedPlacesBatch(userId, needGeocoding);
        Set<String> lookupFailed = new HashSet<>();
        for (Point point : cached.failed()) {
            lookupFailed.add(coordinateKey(point));
        }

        // Normalization without rules returns the same id; skip its per-location transaction then.
        if (batch.normalize == null) {
            batch.normalize = geocodingManagementService.hasNormalizationRules(userId);
        }
        boolean normalize = batch.normalize;

        List<Point> stillNeedExternal = new ArrayList<>();
        int individualResolved = 0;
        for (Point point : needGeocoding) {
            String coordKey = coordinateKey(point);
            ReverseGeocodingLocationRepository.CachedPlace place = cached.places().get(coordKey);
            // Step 3: Individual lookup only where the batch lookup failed: one that ran and found nothing would
            // find nothing alone either, as both match the same way.
            if (place == null && lookupFailed.contains(coordKey)) {
                place = cacheGeocodingService.getCachedPlace(userId, point).orElse(null);
                if (place != null) {
                    individualResolved++;
                }
            }
            if (place != null) {
                Long placeId = normalize ? normalizeGeocodingReference(userId, place.id()) : place.id();
                results.put(coordKey, LocationResolutionResult.fromGeocoding(place.displayName(), placeId));
            } else {
                stillNeedExternal.add(point);
            }
        }
        int batchResolved = cached.places().size();
        batch.batchCacheResolved += batchResolved;
        batch.individualCacheResolved += individualResolved;
        batch.cachedResolved += batchResolved + individualResolved;

        updateGeocodingProgress(batch, "resolvedFromCache",
                "Resolved " + batch.cachedResolved + " from cache (" + batch.batchCacheResolved + " from batch, "
                        + batch.individualCacheResolved + " from individual lookup)",
                stillNeedExternal.size());

        // Step 4: Process truly external geocoding with rate limiting
        if (!stillNeedExternal.isEmpty()) {
            processExternalGeocodingWithRateLimit(stillNeedExternal, results, batch);
        }

        // Step 5: Ensure all coordinates have results (fallback for any missing)
        for (Point point : uniqueCoordinates) {
            if (results.putIfAbsent(coordinateKey(point), LocationResolutionResult.fromGeocoding("Unknown Location", null)) == null) {
                log.warn("No geocoding result found; using fallback");
                batch.fallbacks++;
            }
        }

        log.debug("Chunk resolved in {} s; batch so far: {} favorites, {} cached, {} external, {} fallbacks",
                (System.currentTimeMillis() - startTime) / 1000.0d,
                batch.favoritesResolved, batch.cachedResolved, batch.externalCompleted, batch.fallbacks);
        return results;
    }

    /** Reports the batch as complete. */
    public void finishBatch(LocationBatch batch) {
        if (batch.cachedResolved == 0 && batch.externalCompleted == 0 && batch.fallbacks == 0) {
            updateGeocodingProgress(batch, "allResolvedFromFavorites", "All locations resolved from favorites", 0,
                    batch.totalLocations);
            return;
        }
        updateGeocodingProgress(batch, "geocodingComplete",
                "Geocoding complete: " + batch.favoritesResolved + " favorites, " + batch.cachedResolved + " cached, "
                        + batch.externalCompleted + " external API calls",
                0, batch.totalLocations);
    }

    /**
     * Process external geocoding requests with rate limiting: the provider delay is kept between all requests of a
     * batch, including the last one of a chunk and the first one of the next.
     */
    private void processExternalGeocodingWithRateLimit(List<Point> coordinates,
                                                       Map<String, LocationResolutionResult> results,
                                                       LocationBatch batch) {
        UUID userId = batch.userId;
        long delayMs = Math.max(0, geocodingConfigurationService.getPrimaryProviderDelayMs());
        int externalBefore = batch.externalCompleted;
        int externalTotal = externalBefore + coordinates.size();

        for (int i = 0; i < coordinates.size(); i++) {
            Point point = coordinates.get(i);
            String coordKey = coordinateKey(point);

            try {
                if (batch.externalRequests > 0) {
                    Thread.sleep(delayMs);
                }

                // Report progress for each external geocoding request; the total grows as chunks find more
                int current = externalBefore + i + 1;
                updateGeocodingProgress(batch, "geocodingLocationProgress",
                        "Geocoding location " + current + "/" + externalTotal,
                        coordinates.size() - i,
                        Map.of("current", current, "total", externalTotal));

                batch.externalRequests++;
                FormattableGeocodingResult geocodingResult;
                try {
                    geocodingResult = geocodingService.getLocationName(point);
                } catch (Exception geocodingError) {
                    // Geocoding failed - use fallback to prevent transaction rollback
                    log.warn("Geocoding failed; using fallback", geocodingError);
                    geocodingResult = org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult.builder()
                            .requestCoordinates(point)
                            .resultCoordinates(point)
                            .formattedDisplayName(String.format("Location unavailable (%.6f, %.6f)", point.getY(), point.getX()))
                            .providerName("fallback-error")
                            .build();

                    // Cache the fallback result to prevent retry loops on subsequent runs
                    try {
                        cacheGeocodingService.cacheGeocodingResult(geocodingResult);
                        log.debug("Cached fallback geocoding result");
                    } catch (Exception cacheError) {
                        log.warn("Failed to cache fallback geocoding result", cacheError);
                    }
                }

                Long geocodingId = cacheGeocodingService.getCachedGeocodingResultId(userId, point).orElse(null);
                geocodingId = normalizeGeocodingReference(userId, geocodingId);

                results.put(coordKey, LocationResolutionResult.fromGeocoding(
                        geocodingResult.getFormattedDisplayName(), geocodingId));
                batch.externalCompleted++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error but continue processing other coordinates
                log.warn("Failed to process geocoding location", e);
                results.put(coordKey, LocationResolutionResult.fromGeocoding("Unknown Location", null));
                batch.fallbacks++;
            }
        }
    }

    /**
     * One location resolution split into chunks: the progress counts and per-user lookups carried from one chunk to
     * the next. Created by {@link #startBatch}.
     */
    public static final class LocationBatch {
        private final UUID userId;
        private final UUID jobId;
        private final int totalLocations;
        private Boolean normalize;
        private int favoritesResolved;
        private int cachedResolved;
        private int batchCacheResolved;
        private int individualCacheResolved;
        private int externalCompleted;
        private int externalRequests;
        private int fallbacks;

        private LocationBatch(UUID userId, UUID jobId, int totalLocations) {
            this.userId = userId;
            this.jobId = jobId;
            this.totalLocations = totalLocations;
        }

        private int resolved() {
            return favoritesResolved + cachedResolved + externalCompleted + fallbacks;
        }
    }

    private void updateGeocodingProgress(LocationBatch batch, String stepKey, String fallback, int externalPending) {
        updateGeocodingProgress(batch, stepKey, fallback, externalPending, batch.resolved(), Map.of());
    }

    private void updateGeocodingProgress(LocationBatch batch, String stepKey, String fallback, int externalPending,
                                         Map<String, Object> stepExtras) {
        updateGeocodingProgress(batch, stepKey, fallback, externalPending, batch.resolved(), stepExtras);
    }

    private void updateGeocodingProgress(LocationBatch batch, String stepKey, String fallback, int externalPending,
                                         int totalResolved) {
        updateGeocodingProgress(batch, stepKey, fallback, externalPending, totalResolved, Map.of());
    }

    /**
     * Helper method to update geocoding progress if job tracking is enabled.
     *
     * @param stepKey short suffix appended to the {@code timelineJobs.progressMessages.} catalog
     *                namespace (see {@link TimelineJobProgressService#step})
     * @param fallback English text shown when the active locale has no translation for {@code stepKey}
     *                 yet
     */
    private void updateGeocodingProgress(LocationBatch batch, String stepKey, String fallback, int externalPending,
                                         int totalResolved, Map<String, Object> stepExtras) {
        if (batch.jobId != null) {
            // Progress from 55% to 70% during geocoding, after GPS state-machine processing.
            int progress = batch.totalLocations == 0 ? 70
                    : 55 + (int) ((double) Math.min(totalResolved, batch.totalLocations) / batch.totalLocations * 15);

            Map<String, Object> details = new HashMap<>();
            details.put("totalLocations", batch.totalLocations);
            details.put("favoritesResolved", batch.favoritesResolved);
            details.put("cachedResolved", batch.cachedResolved);
            details.put("batchResolved", batch.batchCacheResolved);
            details.put("individualResolved", batch.individualCacheResolved);
            details.put("externalPending", externalPending);
            details.put("externalCompleted", batch.externalCompleted);
            details.put("totalResolved", totalResolved);

            Map<String, Object> stepParams = new HashMap<>(details);
            stepParams.putAll(stepExtras);

            jobProgressService.updateProgress(batch.jobId, TimelineJobProgressService.step(stepKey, fallback, stepParams), 4, progress, details);
        }
    }

    private Long normalizeGeocodingReference(UUID userId, Long geocodingId) {
        if (geocodingId == null) {
            return null;
        }
        try {
            return geocodingManagementService.normalizeGeocodingForResolution(userId, geocodingId).getId();
        } catch (Exception e) {
            log.debug("Failed to normalize geocoding {} for user {}: {}", geocodingId, userId, e.getMessage());
            return geocodingId;
        }
    }
}
