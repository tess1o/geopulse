package org.github.tess1o.geopulse.favorites.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.context.ManagedExecutor;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.favorites.mapper.FavoriteLocationMapper;
import org.github.tess1o.geopulse.favorites.model.*;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.github.tess1o.geopulse.geocoding.service.ReconciliationJobProgressService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.events.FavoriteDeletedEvent;
import org.github.tess1o.geopulse.streaming.events.FavoriteRenamedEvent;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class FavoriteLocationService {

    @ConfigProperty(name = "geopulse.favorites.max-distance-from-point", defaultValue = "75")
    @StaticInitSafe
    int maxDistanceFromPoint;

    @ConfigProperty(name = "geopulse.favorites.max-distance-from-area", defaultValue = "15")
    @StaticInitSafe
    int maxDistanceFromArea;

    private final FavoritesRepository repository;
    private final FavoriteLocationMapper mapper;
    private final GeocodingService geocodingService;
    private final Event<FavoriteDeletedEvent> favoriteDeletedEvent;
    private final Event<FavoriteRenamedEvent> favoriteRenamedEvent;
    private final org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService asyncTimelineGenerationService;
    private final ReconciliationJobProgressService reconciliationProgressService;
    private final ManagedExecutor managedExecutor;

    public FavoriteLocationService(FavoritesRepository repository,
                                   FavoriteLocationMapper mapper,
                                   GeocodingService geocodingService,
                                   Event<FavoriteDeletedEvent> favoriteDeletedEvent,
                                   Event<FavoriteRenamedEvent> favoriteRenamedEvent,
                                   org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService asyncTimelineGenerationService,
                                   ReconciliationJobProgressService reconciliationProgressService,
                                   ManagedExecutor managedExecutor) {
        this.repository = repository;
        this.mapper = mapper;
        this.geocodingService = geocodingService;
        this.favoriteDeletedEvent = favoriteDeletedEvent;
        this.favoriteRenamedEvent = favoriteRenamedEvent;
        this.asyncTimelineGenerationService = asyncTimelineGenerationService;
        this.reconciliationProgressService = reconciliationProgressService;
        this.managedExecutor = managedExecutor;
        this.maxDistanceFromPoint = maxDistanceFromPoint;
        this.maxDistanceFromArea = maxDistanceFromArea;
    }

    public FavoriteLocationsDto getFavorites(UUID userId) {
        var favorites = repository.findByUserId(userId);
        return mapper.toFavoriteLocationDto(favorites);
    }

    @Transactional
    public void addFavorite(UUID userId, AddPointToFavoritesDto favorite) {
        validatePointFavorite(favorite);

        log.debug("Adding point favorite for user {}: {} at [{}, {}]",
                userId, favorite.getName(), favorite.getLat(), favorite.getLon());

        FavoritesEntity entity = mapper.toEntity(favorite, userId);

        // Get geocoding data to populate city and country
        try {
            Point point = GeoUtils.createPoint(favorite.getLon(), favorite.getLat());
            FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(point);
            entity.setCity(geocodingResult.getCity());
            entity.setCountry(geocodingResult.getCountry());

            log.debug("Populated geocoding data for favorite: city={}, country={}",
                    geocodingResult.getCity(), geocodingResult.getCountry());
        } catch (Exception e) {
            log.warn("Failed to get geocoding data for favorite at [{}, {}]: {}",
                    favorite.getLat(), favorite.getLon(), e.getMessage());
            // Continue without geocoding data - city and country will be null
        }

        repository.persist(entity);

        log.info("Successfully added point favorite {} for user {}", favorite.getName(), userId);
    }

    @Transactional
    public void addFavorite(UUID userId, AddAreaToFavoritesDto favorite) {
        validateAreaFavorite(favorite);

        log.debug("Adding area favorite for user {}: {} with bounds NE[{}, {}] SW[{}, {}]",
                userId, favorite.getName(),
                favorite.getNorthEastLat(), favorite.getNorthEastLon(),
                favorite.getSouthWestLat(), favorite.getSouthWestLon());

        FavoritesEntity entity = mapper.toEntity(favorite, userId);

        // Get geocoding data using the center point of the area
        try {
            double centerLat = (favorite.getNorthEastLat() + favorite.getSouthWestLat()) / 2.0;
            double centerLon = (favorite.getNorthEastLon() + favorite.getSouthWestLon()) / 2.0;
            Point centerPoint = GeoUtils.createPoint(centerLon, centerLat);

            FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(centerPoint);
            entity.setCity(geocodingResult.getCity());
            entity.setCountry(geocodingResult.getCountry());

            log.debug("Populated geocoding data for area favorite using center point [{}, {}]: city={}, country={}",
                    centerLat, centerLon, geocodingResult.getCity(), geocodingResult.getCountry());
        } catch (Exception e) {
            log.warn("Failed to get geocoding data for area favorite: {}", e.getMessage());
            // Continue without geocoding data - city and country will be null
        }

        repository.persist(entity);

        log.info("Successfully added area favorite {} for user {}", favorite.getName(), userId);
    }

    @Transactional
    public BulkAddFavoritesResult bulkAddFavorites(UUID userId, BulkAddFavoritesDto bulkDto) {
        log.info("Starting bulk add favorites for user {}: {} points, {} areas",
                userId, bulkDto.getPoints().size(), bulkDto.getAreas().size());

        List<Long> createdFavoriteIds = new java.util.ArrayList<>();
        Map<Integer, String> failures = new java.util.HashMap<>();
        int index = 0;
        int totalRequested = bulkDto.getPoints().size() + bulkDto.getAreas().size();

        // Process points
        for (AddPointToFavoritesDto pointDto : bulkDto.getPoints()) {
            try {
                validatePointFavorite(pointDto);

                FavoritesEntity entity = mapper.toEntity(pointDto, userId);

                // Get geocoding data to populate city and country
                try {
                    Point point = GeoUtils.createPoint(pointDto.getLon(), pointDto.getLat());
                    FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(point);
                    entity.setCity(geocodingResult.getCity());
                    entity.setCountry(geocodingResult.getCountry());
                } catch (Exception e) {
                    log.warn("Failed to get geocoding data for point favorite '{}' at [{}, {}]: {}",
                            pointDto.getName(), pointDto.getLat(), pointDto.getLon(), e.getMessage());
                    // Continue without geocoding data
                }

                repository.persist(entity);

                createdFavoriteIds.add(entity.getId());
                log.debug("Successfully added point favorite '{}' (ID: {}) in bulk operation",
                        pointDto.getName(), entity.getId());

            } catch (Exception e) {
                log.warn("Failed to add point favorite at index {}: {}", index, e.getMessage());
                failures.put(index, e.getMessage());
            }
            index++;
        }

        // Process areas
        for (AddAreaToFavoritesDto areaDto : bulkDto.getAreas()) {
            try {
                validateAreaFavorite(areaDto);

                FavoritesEntity entity = mapper.toEntity(areaDto, userId);

                // Get geocoding data using the center point of the area
                try {
                    double centerLat = (areaDto.getNorthEastLat() + areaDto.getSouthWestLat()) / 2.0;
                    double centerLon = (areaDto.getNorthEastLon() + areaDto.getSouthWestLon()) / 2.0;
                    Point centerPoint = GeoUtils.createPoint(centerLon, centerLat);

                    FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(centerPoint);
                    entity.setCity(geocodingResult.getCity());
                    entity.setCountry(geocodingResult.getCountry());
                } catch (Exception e) {
                    log.warn("Failed to get geocoding data for area favorite '{}': {}",
                            areaDto.getName(), e.getMessage());
                    // Continue without geocoding data
                }

                repository.persist(entity);

                createdFavoriteIds.add(entity.getId());
                log.debug("Successfully added area favorite '{}' (ID: {}) in bulk operation",
                        areaDto.getName(), entity.getId());

            } catch (Exception e) {
                log.warn("Failed to add area favorite at index {}: {}", index, e.getMessage());
                failures.put(index, e.getMessage());
            }
            index++;
        }

        int successCount = createdFavoriteIds.size();
        int failedCount = failures.size();

        log.info("Bulk add favorites completed for user {}: {} successful, {} failed out of {} total",
                userId, successCount, failedCount, totalRequested);

        return BulkAddFavoritesResult.builder()
                .totalRequested(totalRequested)
                .successCount(successCount)
                .failedCount(failedCount)
                .createdFavoriteIds(createdFavoriteIds)
                .failures(failures)
                .build();
    }

    public FavoriteLocationsDto findByPoint(UUID userId, Point point) {
        Optional<FavoritesEntity> favorite = repository.findByPoint(userId, point, maxDistanceFromPoint, maxDistanceFromArea);
        if (favorite.isEmpty()) {
            return null;
        }
        List<FavoritesEntity> favorites = List.of(favorite.get());
        return mapper.toFavoriteLocationDto(favorites);
    }

    /**
     * Batch find favorites by multiple points with spatial matching.
     * Uses true batch processing to avoid N+1 query problem.
     *
     * @param userId User ID for favorite location lookup
     * @param points List of points to search for
     * @return Map of coordinate string (lon,lat) to FavoriteLocationsDto
     */
    public Map<String, FavoriteLocationsDto> findByPointsBatch(UUID userId, List<Point> points) {
        Map<String, FavoritesEntity> entities = repository.findByPointsBatch(userId, points, maxDistanceFromPoint, maxDistanceFromArea);

        Map<String, FavoriteLocationsDto> results = new java.util.HashMap<>();
        for (Map.Entry<String, FavoritesEntity> entry : entities.entrySet()) {
            String coordKey = entry.getKey();
            FavoritesEntity entity = entry.getValue();

            // Convert single entity to DTO format (same as findByPoint)
            List<FavoritesEntity> favorites = List.of(entity);
            FavoriteLocationsDto dto = mapper.toFavoriteLocationDto(favorites);
            results.put(coordKey, dto);
        }

        return results;
    }

    @Transactional
    public void renameFavorite(UUID userId, long favoriteId, String newName) {
        EditFavoriteDto dto = new EditFavoriteDto();
        dto.setName(newName);
        updateFavorite(userId, favoriteId, dto);
    }

    @Transactional
    public BulkUpdateFavoritesResult bulkUpdateFavorites(UUID userId, BulkUpdateFavoritesDto bulkDto) {
        log.info("Starting bulk update favorites for user {}: {} favorites, updateCity={}, updateCountry={}",
                userId, bulkDto.getFavoriteIds().size(), bulkDto.getUpdateCity(), bulkDto.getUpdateCountry());

        Map<Long, String> failures = new java.util.HashMap<>();
        int successCount = 0;

        for (Long favoriteId : bulkDto.getFavoriteIds()) {
            try {
                FavoritesEntity favoritesEntity = repository.findById(favoriteId);
                if (favoritesEntity == null) {
                    failures.put(favoriteId, "Favorite not found");
                    continue;
                }

                if (!favoritesEntity.getUser().getId().equals(userId)) {
                    failures.put(favoriteId, "Not authorized to edit this favorite");
                    continue;
                }

                // Update only the selected fields
                if (Boolean.TRUE.equals(bulkDto.getUpdateCity())) {
                    favoritesEntity.setCity(bulkDto.getCity().trim());
                }
                if (Boolean.TRUE.equals(bulkDto.getUpdateCountry())) {
                    favoritesEntity.setCountry(bulkDto.getCountry().trim());
                }

                repository.persistAndFlush(favoritesEntity);
                successCount++;

                log.debug("Successfully updated favorite {} for user {}", favoriteId, userId);

            } catch (Exception e) {
                log.warn("Failed to update favorite {} in bulk operation: {}", favoriteId, e.getMessage());
                failures.put(favoriteId, e.getMessage());
            }
        }

        int failedCount = failures.size();
        log.info("Bulk update favorites completed for user {}: {} successful, {} failed out of {} total",
                userId, successCount, failedCount, bulkDto.getFavoriteIds().size());

        return BulkUpdateFavoritesResult.builder()
                .totalRequested(bulkDto.getFavoriteIds().size())
                .successCount(successCount)
                .failedCount(failedCount)
                .failures(failures)
                .build();
    }

    public DistinctValuesDto getDistinctValues(UUID userId) {
        List<FavoritesEntity> favorites = repository.findByUserId(userId);

        List<String> cities = favorites.stream()
                .map(FavoritesEntity::getCity)
                .filter(city -> city != null && !city.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<String> countries = favorites.stream()
                .map(FavoritesEntity::getCountry)
                .filter(country -> country != null && !country.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        return DistinctValuesDto.builder()
                .cities(cities)
                .countries(countries)
                .build();
    }

    @Transactional
    public boolean updateFavorite(UUID userId, long favoriteId, EditFavoriteDto dto) {
        validateName(dto.getName());

        log.debug("User {} attempting to update favorite {}: name='{}', city='{}', country='{}'",
                userId, favoriteId, dto.getName(), dto.getCity(), dto.getCountry());

        FavoritesEntity favoritesEntity = repository.findById(favoriteId);
        if (favoritesEntity == null) {
            log.warn("Favorite with ID {} not found", favoriteId);
            throw new IllegalArgumentException("Favorite not found");
        }

        if (!favoritesEntity.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to edit favorite {} owned by user {}",
                    userId, favoriteId, favoritesEntity.getUser().getId());
            throw new SecurityException("User is not authorized to edit this favorite");
        }

        boolean boundsChanged = false;

        // Update basic fields
        String oldName = favoritesEntity.getName();
        favoritesEntity.setName(dto.getName());
        favoritesEntity.setCity(dto.getCity());
        favoritesEntity.setCountry(dto.getCountry());

        // Update bounds if provided (for area favorites)
        if (dto.getNorthEastLat() != null && dto.getNorthEastLon() != null &&
            dto.getSouthWestLat() != null && dto.getSouthWestLon() != null) {

            if (favoritesEntity.getType() != FavoriteLocationType.AREA) {
                log.warn("Attempted to update bounds for non-area favorite {}", favoriteId);
                throw new IllegalArgumentException("Can only update bounds for area favorites");
            }

            // Validate bounds
            validateCoordinate("northEastLat", dto.getNorthEastLat(), -90.0, 90.0);
            validateCoordinate("northEastLon", dto.getNorthEastLon(), -180.0, 180.0);
            validateCoordinate("southWestLat", dto.getSouthWestLat(), -90.0, 90.0);
            validateCoordinate("southWestLon", dto.getSouthWestLon(), -180.0, 180.0);

            if (dto.getNorthEastLat() <= dto.getSouthWestLat()) {
                throw new IllegalArgumentException("North-East latitude must be greater than South-West latitude");
            }

            if (dto.getNorthEastLon() <= dto.getSouthWestLon()) {
                throw new IllegalArgumentException("North-East longitude must be greater than South-West longitude");
            }

            // Create new polygon with updated bounds
            // buildBoundingBoxPolygon expects: (south lat, north lat, west lon, east lon)
            Polygon newPolygon = GeoUtils.buildBoundingBoxPolygon(
                    dto.getSouthWestLat(), dto.getNorthEastLat(),
                    dto.getSouthWestLon(), dto.getNorthEastLon()
            );

            // Check if bounds actually changed
            if (!favoritesEntity.getGeometry().equals(newPolygon)) {
                favoritesEntity.setGeometry(newPolygon);
                boundsChanged = true;
                log.info("User {} updated bounds for area favorite {}", userId, favoriteId);
            }
        }

        repository.persistAndFlush(favoritesEntity);

        // Fire event for timeline system (only if name changed)
        if (!oldName.equals(dto.getName())) {
            favoriteRenamedEvent.fire(FavoriteRenamedEvent.builder()
                    .favoriteId(favoriteId)
                    .userId(userId)
                    .oldName(oldName)
                    .newName(dto.getName())
                    .favoriteType(favoritesEntity.getType())
                    .geometry(favoritesEntity.getGeometry())
                    .build());
        }

        log.info("User {} successfully updated favorite {}: name='{}', city='{}', country='{}', boundsChanged={}",
                userId, favoriteId, dto.getName(), dto.getCity(), dto.getCountry(), boundsChanged);

        return boundsChanged;
    }

    @Transactional
    public void deleteFavorite(UUID userId, long id) {
        log.debug("User {} attempting to delete favorite {}", userId, id);

        FavoritesEntity favoritesEntity = repository.findById(id);
        if (favoritesEntity == null) {
            log.warn("Favorite with ID {} not found", id);
            throw new IllegalArgumentException("Favorite not found");
        }

        if (!favoritesEntity.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to delete favorite {} owned by user {}",
                    userId, id, favoritesEntity.getUser().getId());
            throw new SecurityException("User is not authorized to delete this favorite");
        }

        String favoriteName = favoritesEntity.getName();
        repository.deleteById(id);
        favoriteDeletedEvent.fire(FavoriteDeletedEvent.builder()
                .favoriteId(id)
                .userId(userId)
                .favoriteName(favoriteName)
                .favoriteType(favoritesEntity.getType())
                .geometry(favoritesEntity.getGeometry())
                .build());

        log.info("User {} successfully deleted favorite {} ('{}')", userId, id, favoriteName);
    }

    /**
     * Create async job for timeline regeneration after favorite add/delete.
     * This method is NOT transactional and should be called after the transaction commits.
     */
    public UUID createTimelineRegenerationJob(UUID userId) {
        try {
            UUID jobId = asyncTimelineGenerationService.regenerateTimelineAsync(userId);
            log.info("Created async timeline regeneration job {} for user {}", jobId, userId);
            return jobId;
        } catch (IllegalStateException e) {
            log.warn("Could not create regeneration job for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    // Reconciliation methods

    /**
     * Start async bulk reconciliation job and return job ID immediately.
     *
     * @param currentUserId Current user ID
     * @param request       Reconciliation request
     * @return Job ID for progress tracking
     */
    public UUID reconcileWithProviderAsync(UUID currentUserId, FavoriteReconcileRequest request) {
        List<Long> idsToReconcile = determineIdsToReconcile(currentUserId, request);

        // Create job
        UUID jobId = reconciliationProgressService.createJob(
                currentUserId, request.getProviderName(), idsToReconcile.size());

        log.info("Starting async favorite reconciliation job {} for user {} ({} items)",
                jobId, currentUserId, idsToReconcile.size());

        // Run reconciliation asynchronously using ManagedExecutor
        CompletableFuture.runAsync(() -> {
            try {
                processFavoriteReconciliationJob(jobId, currentUserId, idsToReconcile);
            } catch (Exception e) {
                log.error("Failed to process favorite reconciliation job {}", jobId, e);
                reconciliationProgressService.failJob(jobId, e.getMessage());
            }
        }, managedExecutor);

        return jobId;
    }

    /**
     * Process reconciliation job with progress tracking.
     * This method runs in a separate thread and updates progress after each item.
     *
     * @param jobId       Job ID
     * @param userId      User ID
     * @param favoriteIds List of favorite IDs to reconcile
     */
    @Transactional
    @ActivateRequestContext
    void processFavoriteReconciliationJob(UUID jobId, UUID userId, List<Long> favoriteIds) {
        int successCount = 0;
        int failedCount = 0;

        log.info("Processing favorite reconciliation job {} with {} items", jobId, favoriteIds.size());

        for (int i = 0; i < favoriteIds.size(); i++) {
            Long favoriteId = favoriteIds.get(i);

            try {
                reconcileSingleFavorite(userId, favoriteId);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                log.warn("Failed to reconcile favorite {} in job {}: {}", favoriteId, jobId, e.getMessage());
            }

            // Update progress after each item
            reconciliationProgressService.updateProgress(jobId, i + 1, successCount, failedCount);
        }

        // Mark complete
        reconciliationProgressService.completeJob(jobId);
        log.info("Favorite reconciliation job {} completed: {} success, {} failed",
                jobId, successCount, failedCount);
    }

    /**
     * Reconcile a single favorite with reverse geocoding.
     * Updates ONLY city and country - all other fields unchanged.
     *
     * @param userId     User ID
     * @param favoriteId Favorite ID to reconcile
     */
    @Transactional
    void reconcileSingleFavorite(UUID userId, Long favoriteId) {
        FavoritesEntity favorite = repository.findById(favoriteId);
        if (favorite == null) {
            throw new IllegalArgumentException("Favorite not found: " + favoriteId);
        }

        // Security check
        if (!favorite.getUser().getId().equals(userId)) {
            throw new SecurityException("Cannot reconcile another user's favorite");
        }

        try {
            Point point;
            if (favorite.getType() == FavoriteLocationType.POINT) {
                // For points, use the point coordinates directly
                org.locationtech.jts.geom.Point geomPoint = (org.locationtech.jts.geom.Point) favorite.getGeometry();
                point = GeoUtils.createPoint(geomPoint.getX(), geomPoint.getY());
            } else {
                // For areas, use the center point
                Polygon polygon = (Polygon) favorite.getGeometry();
                org.locationtech.jts.geom.Coordinate centroid = polygon.getCentroid().getCoordinate();
                point = GeoUtils.createPoint(centroid.x, centroid.y);
            }

            // Fetch fresh geocoding data
            FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(point);

            // Update ONLY city and country
            favorite.setCity(geocodingResult.getCity());
            favorite.setCountry(geocodingResult.getCountry());
            repository.persistAndFlush(favorite);

            log.debug("Reconciled favorite {}: city='{}', country='{}'",
                    favoriteId, geocodingResult.getCity(), geocodingResult.getCountry());

        } catch (Exception e) {
            log.error("Failed to reconcile favorite {}: {}", favoriteId, e.getMessage(), e);
            throw new RuntimeException("Reconciliation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Determine which IDs to reconcile based on request.
     *
     * @param userId  User ID
     * @param request Reconciliation request
     * @return List of favorite IDs to reconcile
     */
    private List<Long> determineIdsToReconcile(UUID userId, FavoriteReconcileRequest request) {
        if (Boolean.TRUE.equals(request.getReconcileAll())) {
            // Get all favorites for user with filters
            List<FavoritesEntity> favorites = repository.findByUserId(userId);

            return favorites.stream()
                    .filter(f -> {
                        // Filter by type if specified
                        if (request.getFilterByType() != null && f.getType() != request.getFilterByType()) {
                            return false;
                        }
                        // Filter by search text if specified
                        if (request.getFilterBySearchText() != null && !request.getFilterBySearchText().trim().isEmpty()) {
                            String searchLower = request.getFilterBySearchText().toLowerCase();
                            return f.getName().toLowerCase().contains(searchLower) ||
                                    (f.getCity() != null && f.getCity().toLowerCase().contains(searchLower)) ||
                                    (f.getCountry() != null && f.getCountry().toLowerCase().contains(searchLower));
                        }
                        return true;
                    })
                    .map(FavoritesEntity::getId)
                    .collect(Collectors.toList());
        } else {
            // Reconcile only specified IDs
            return request.getFavoriteIds();
        }
    }

    // Validation methods
    private void validatePointFavorite(AddPointToFavoritesDto favorite) {
        if (favorite == null) {
            throw new IllegalArgumentException("Favorite data cannot be null");
        }

        validateName(favorite.getName());
        validateCoordinate("latitude", favorite.getLat(), -90.0, 90.0);
        validateCoordinate("longitude", favorite.getLon(), -180.0, 180.0);
    }

    private void validateAreaFavorite(AddAreaToFavoritesDto favorite) {
        if (favorite == null) {
            throw new IllegalArgumentException("Favorite data cannot be null");
        }

        validateName(favorite.getName());

        // Validate individual coordinates
        validateCoordinate("northEastLat", favorite.getNorthEastLat(), -90.0, 90.0);
        validateCoordinate("northEastLon", favorite.getNorthEastLon(), -180.0, 180.0);
        validateCoordinate("southWestLat", favorite.getSouthWestLat(), -90.0, 90.0);
        validateCoordinate("southWestLon", favorite.getSouthWestLon(), -180.0, 180.0);

        // Validate that NE is actually northeast of SW
        if (favorite.getNorthEastLat() <= favorite.getSouthWestLat()) {
            throw new IllegalArgumentException("North-East latitude must be greater than South-West latitude");
        }

        if (favorite.getNorthEastLon() <= favorite.getSouthWestLon()) {
            throw new IllegalArgumentException("North-East longitude must be greater than South-West longitude");
        }
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Favorite name cannot be empty");
        }

        if (name.length() > 100) {
            throw new IllegalArgumentException("Favorite name cannot exceed 100 characters");
        }
    }

    private void validateCoordinate(String coordinateName, double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(coordinateName + " must be a valid number");
        }

        if (value < min || value > max) {
            throw new IllegalArgumentException(coordinateName + " must be between " + min + " and " + max);
        }
    }
}
