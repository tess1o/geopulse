package org.github.tess1o.geopulse.gps.integrations.owntracks.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteAreaDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.model.FavoritePointDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

/**
 * Service for handling OwnTracks POI (Point of Interest) messages.
 * Creates or updates favorite locations based on POI data from OwnTracks.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksPoiService {

    private final FavoriteLocationService favoriteLocationService;

    public OwnTracksPoiService(FavoriteLocationService favoriteLocationService) {
        this.favoriteLocationService = favoriteLocationService;
    }

    /**
     * Handle OwnTracks POI (Point of Interest) by creating or updating favorite locations.
     * This method can be called from MQTT callback threads (non-CDI managed), so it needs
     * both transaction and request context activation.
     *
     * @param message OwnTracks location message with POI information
     * @param userId User ID
     */
    @Transactional
    @ActivateRequestContext
    public void handlePoi(OwnTracksLocationMessage message, UUID userId) {
        String poiName = message.getPoi().trim();
        Double lat = message.getLat();
        Double lon = message.getLon();

        if (lat == null || lon == null) {
            log.warn("POI message missing coordinates: poi={}", poiName);
            return;
        }

        log.debug("Processing OwnTracks POI: {} at [{}, {}]", poiName, lat, lon);

        Point point = GeoUtils.createPoint(lon, lat);

        FavoriteLocationsDto existingFavorite = favoriteLocationService.findByPoint(userId, point);

        if (existingFavorite == null ||
                (existingFavorite.getPoints().isEmpty() && existingFavorite.getAreas().isEmpty())) {
            // Create new favorite
            AddPointToFavoritesDto newFavorite = new AddPointToFavoritesDto();
            newFavorite.setName(poiName);
            newFavorite.setLat(lat);
            newFavorite.setLon(lon);

            favoriteLocationService.addFavorite(userId, newFavorite);

            // Regenerate timeline after adding favorite
            favoriteLocationService.createTimelineRegenerationJob(userId);

            log.info("Created favorite from OwnTracks POI: '{}' at [{}, {}]", poiName, lat, lon);
        } else {
            // Update existing favorite name if different
            // Can be either a point or an area
            if (!existingFavorite.getPoints().isEmpty()) {
                FavoritePointDto existingPoint = existingFavorite.getPoints().get(0);
                if (!poiName.equals(existingPoint.getName())) {
                    favoriteLocationService.updateFavorite(
                            userId,
                            existingPoint.getId(),
                            poiName,
                            existingPoint.getCity(),
                            existingPoint.getCountry()
                    );
                    log.info("Updated favorite point '{}' to '{}' based on OwnTracks POI",
                            existingPoint.getName(), poiName);
                } else {
                    log.debug("Favorite point already exists with same name: {}", poiName);
                }
            } else if (!existingFavorite.getAreas().isEmpty()) {
                FavoriteAreaDto existingArea = existingFavorite.getAreas().getFirst();
                if (!poiName.equals(existingArea.getName())) {
                    favoriteLocationService.updateFavorite(
                            userId,
                            existingArea.getId(),
                            poiName,
                            existingArea.getCity(),
                            existingArea.getCountry()
                    );
                    log.info("Updated favorite area '{}' to '{}' based on OwnTracks POI",
                            existingArea.getName(), poiName);
                } else {
                    log.debug("Favorite area already exists with same name: {}", poiName);
                }
            }
        }
    }
}
