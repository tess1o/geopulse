package org.github.tess1o.geopulse.favorites.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.favorites.mapper.FavoriteLocationMapper;
import org.github.tess1o.geopulse.favorites.model.*;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.events.FavoriteAddedEvent;
import org.github.tess1o.geopulse.streaming.events.FavoriteDeletedEvent;
import org.github.tess1o.geopulse.streaming.events.FavoriteRenamedEvent;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final Event<FavoriteAddedEvent> favoriteAddedEvent;
    private final Event<FavoriteDeletedEvent> favoriteDeletedEvent;
    private final Event<FavoriteRenamedEvent> favoriteRenamedEvent;

    public FavoriteLocationService(FavoritesRepository repository,
                                   FavoriteLocationMapper mapper,
                                   GeocodingService geocodingService,
                                   Event<FavoriteAddedEvent> favoriteAddedEvent,
                                   Event<FavoriteDeletedEvent> favoriteDeletedEvent,
                                   Event<FavoriteRenamedEvent> favoriteRenamedEvent) {
        this.repository = repository;
        this.mapper = mapper;
        this.geocodingService = geocodingService;
        this.favoriteAddedEvent = favoriteAddedEvent;
        this.favoriteDeletedEvent = favoriteDeletedEvent;
        this.favoriteRenamedEvent = favoriteRenamedEvent;
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
        
        // Fire event for timeline system
        favoriteAddedEvent.fire(FavoriteAddedEvent.builder()
                .favoriteId(entity.getId())
                .userId(userId)
                .favoriteName(entity.getName())
                .favoriteType(entity.getType())
                .geometry(entity.getGeometry())
                .build());

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
        
        // Fire event for timeline system
        favoriteAddedEvent.fire(FavoriteAddedEvent.builder()
                .favoriteId(entity.getId())
                .userId(userId)
                .favoriteName(entity.getName())
                .favoriteType(entity.getType())
                .geometry(entity.getGeometry())
                .build());

        log.info("Successfully added area favorite {} for user {}", favorite.getName(), userId);
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
        validateName(newName);

        log.debug("User {} attempting to rename favorite {} to '{}'", userId, favoriteId, newName);

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

        String oldName = favoritesEntity.getName();
        favoritesEntity.setName(newName);
        repository.persistAndFlush(favoritesEntity);
        
        // Fire event for timeline system
        favoriteRenamedEvent.fire(FavoriteRenamedEvent.builder()
                .favoriteId(favoriteId)
                .userId(userId)
                .oldName(oldName)
                .newName(newName)
                .favoriteType(favoritesEntity.getType())
                .geometry(favoritesEntity.getGeometry())
                .build());

        log.info("User {} successfully renamed favorite {} from '{}' to '{}'",
                userId, favoriteId, oldName, newName);
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
