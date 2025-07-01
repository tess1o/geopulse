package org.github.tess1o.geopulse.shared.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@ApplicationScoped
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
}
