package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.locationtech.jts.geom.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for fetching place details and statistics.
 * Provides comprehensive information about specific locations from timeline stays.
 */
@ApplicationScoped
@Slf4j
public class PlaceDetailsService {

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    ReverseGeocodingLocationRepository geocodingRepository;

    /**
     * Get comprehensive details for a place including statistics.
     *
     * @param type   place type ("favorite" or "geocoding")
     * @param id     place ID
     * @param userId user ID
     * @return place details with statistics
     */
    public Optional<PlaceDetailsDTO> getPlaceDetails(String type, Long id, UUID userId) {
        if ("favorite".equalsIgnoreCase(type)) {
            return getFavoritePlaceDetails(id, userId);
        } else if ("geocoding".equalsIgnoreCase(type)) {
            return getGeocodingPlaceDetails(id, userId);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get paginated visits for a specific place.
     *
     * @param type          place type ("favorite" or "geocoding")
     * @param id            place ID
     * @param userId        user ID
     * @param page          zero-based page number
     * @param pageSize      number of items per page (max 200)
     * @param sortBy        field to sort by
     * @param sortDirection "asc" or "desc"
     * @return paginated visits
     */
    public PagedPlaceVisitsDTO getPlaceVisits(
            String type, Long id, UUID userId,
            int page, int pageSize, String sortBy, String sortDirection) {

        // Validate and limit page size
        int validPageSize = Math.min(Math.max(pageSize, 1), 200);
        boolean ascending = "asc".equalsIgnoreCase(sortDirection);

        // Validate sort field
        String validSortBy = validateSortField(sortBy);

        List<TimelineStayEntity> stays;
        long totalCount;

        if ("favorite".equalsIgnoreCase(type)) {
            stays = timelineStayRepository.findByFavoriteLocationIdPaginated(
                    id, userId, page, validPageSize, validSortBy, ascending);
            totalCount = timelineStayRepository.countByFavoriteLocationId(id, userId);
        } else if ("geocoding".equalsIgnoreCase(type)) {
            stays = timelineStayRepository.findByGeocodingLocationIdPaginated(
                    id, userId, page, validPageSize, validSortBy, ascending);
            totalCount = timelineStayRepository.countByGeocodingLocationId(id, userId);
        } else {
            return PagedPlaceVisitsDTO.builder()
                    .visits(List.of())
                    .currentPage(page)
                    .pageSize(validPageSize)
                    .totalCount(0)
                    .totalPages(0)
                    .build();
        }

        List<PlaceVisitDTO> visits = stays.stream()
                .map(this::convertToPlaceVisitDTO)
                .toList();

        int totalPages = (int) Math.ceil((double) totalCount / validPageSize);

        return PagedPlaceVisitsDTO.builder()
                .visits(visits)
                .currentPage(page)
                .pageSize(validPageSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    /**
     * Get all visits for a specific place (for CSV export).
     * Returns all visits without pagination.
     *
     * @param type          place type ("favorite" or "geocoding")
     * @param id            place ID
     * @param userId        user ID
     * @param sortBy        field to sort by
     * @param sortDirection "asc" or "desc"
     * @return all visits with place details
     */
    public List<PlaceVisitDTO> getAllPlaceVisits(
            String type, Long id, UUID userId, String sortBy, String sortDirection) {

        boolean ascending = "asc".equalsIgnoreCase(sortDirection);
        String validSortBy = validateSortField(sortBy);

        List<TimelineStayEntity> stays;

        if ("favorite".equalsIgnoreCase(type)) {
            stays = timelineStayRepository.findByFavoriteLocationId(id, userId, validSortBy, ascending);
        } else if ("geocoding".equalsIgnoreCase(type)) {
            stays = timelineStayRepository.findByGeocodingLocationId(id, userId, validSortBy, ascending);
        } else {
            return List.of();
        }

        return stays.stream()
                .map(this::convertToPlaceVisitDTO)
                .toList();
    }

    /**
     * Update place name (for favorites only, geocoding is not editable by users).
     * Also updates all timeline stays that reference this favorite to maintain data consistency.
     *
     * @param type     place type
     * @param id       place ID
     * @param userId   user ID
     * @param newName  new name for the place
     * @return true if updated successfully
     */
    @Transactional
    public boolean updatePlaceName(String type, Long id, UUID userId, String newName) {
        if (!"favorite".equalsIgnoreCase(type)) {
            log.warn("Cannot update name for type: {}", type);
            return false;
        }

        Optional<FavoritesEntity> favoriteOpt = favoritesRepository.findByIdAndUserId(id, userId);
        if (favoriteOpt.isEmpty()) {
            log.warn("Favorite not found: id={}, userId={}", id, userId);
            return false;
        }

        FavoritesEntity favorite = favoriteOpt.get();
        favorite.setName(newName);
        favoritesRepository.persist(favorite);

        // Update all timeline stays that reference this favorite to maintain data consistency
        int updatedStays = timelineStayRepository.updateLocationNameByFavoriteId(id, userId, newName);

        log.info("Updated favorite name: id={}, userId={}, newName={}, updatedStays={}",
                id, userId, newName, updatedStays);
        return true;
    }

    // Private helper methods

    private Optional<PlaceDetailsDTO> getFavoritePlaceDetails(Long favoriteId, UUID userId) {
        Optional<FavoritesEntity> favoriteOpt = favoritesRepository.findByIdAndUserId(favoriteId, userId);
        if (favoriteOpt.isEmpty()) {
            return Optional.empty();
        }

        FavoritesEntity favorite = favoriteOpt.get();
        PlaceStatisticsDTO statistics = calculateFavoriteStatistics(favoriteId, userId);
        PlaceGeometryDTO geometry = extractFavoriteGeometry(favorite);

        return Optional.of(PlaceDetailsDTO.builder()
                .type("favorite")
                .id(favoriteId)
                .locationName(favorite.getName())
                .canEdit(true) // Favorites can be edited
                .geometry(geometry)
                .statistics(statistics)
                .build());
    }

    private Optional<PlaceDetailsDTO> getGeocodingPlaceDetails(Long geocodingId, UUID userId) {
        Optional<ReverseGeocodingLocationEntity> geocodingOpt =
                geocodingRepository.findByIdOptional(geocodingId);
        if (geocodingOpt.isEmpty()) {
            return Optional.empty();
        }

        ReverseGeocodingLocationEntity geocoding = geocodingOpt.get();
        PlaceStatisticsDTO statistics = calculateGeocodingStatistics(geocodingId, userId);
        PlaceGeometryDTO geometry = extractGeocodingGeometry(geocoding);

        // Check for related favorite if this geocoding location has no visits
        FavoriteRelationDTO relatedFavorite = null;
        if (statistics.getTotalVisits() == 0) {
            relatedFavorite = findRelatedFavorite(geocoding, userId);
        }

        return Optional.of(PlaceDetailsDTO.builder()
                .type("geocoding")
                .id(geocodingId)
                .locationName(geocoding.getDisplayName())
                .canEdit(false) // Geocoding locations cannot be edited by users
                .geometry(geometry)
                .statistics(statistics)
                .relatedFavorite(relatedFavorite)
                .build());
    }

    /**
     * Extract geometry from a favorite entity.
     * Handles both POINT and AREA types.
     */
    private PlaceGeometryDTO extractFavoriteGeometry(FavoritesEntity favorite) {
        Geometry geometry = favorite.getGeometry();

        if (favorite.getType() == FavoriteLocationType.POINT) {
            // Extract single point
            Point point = (Point) geometry;
            return PlaceGeometryDTO.builder()
                    .type("point")
                    .latitude(point.getY())
                    .longitude(point.getX())
                    .build();
        } else {
            // Extract rectangle/polygon bounds
            Polygon polygon = (Polygon) geometry;
            Envelope envelope = polygon.getEnvelopeInternal();

            // Get all coordinates for the polygon
            List<double[]> coordinates = new ArrayList<>();
            Coordinate[] coords = polygon.getCoordinates();
            for (Coordinate coord : coords) {
                coordinates.add(new double[]{coord.y, coord.x}); // [lat, lon]
            }

            // Calculate center for convenience
            Point centroid = polygon.getCentroid();

            return PlaceGeometryDTO.builder()
                    .type("area")
                    .latitude(centroid.getY()) // Center latitude
                    .longitude(centroid.getX()) // Center longitude
                    .northEast(new double[]{envelope.getMaxY(), envelope.getMaxX()})
                    .southWest(new double[]{envelope.getMinY(), envelope.getMinX()})
                    .coordinates(coordinates)
                    .build();
        }
    }

    /**
     * Extract geometry from a geocoding entity.
     * Geocoding is always a point, but may have a bounding box.
     */
    private PlaceGeometryDTO extractGeocodingGeometry(ReverseGeocodingLocationEntity geocoding) {
        // Use result coordinates if available, otherwise use request coordinates
        Point point = geocoding.getResultCoordinates() != null
                ? geocoding.getResultCoordinates()
                : geocoding.getRequestCoordinates();

        PlaceGeometryDTO.PlaceGeometryDTOBuilder builder = PlaceGeometryDTO.builder()
                .type("point")
                .latitude(point.getY())
                .longitude(point.getX());

        // If there's a bounding box, include it as area information
        if (geocoding.getBoundingBox() != null) {
            Polygon bbox = geocoding.getBoundingBox();
            Envelope envelope = bbox.getEnvelopeInternal();

            builder.northEast(new double[]{envelope.getMaxY(), envelope.getMaxX()})
                   .southWest(new double[]{envelope.getMinY(), envelope.getMinX()});
        }

        return builder.build();
    }

    private PlaceStatisticsDTO calculateFavoriteStatistics(Long favoriteId, UUID userId) {
        long totalVisits = timelineStayRepository.countByFavoriteLocationId(favoriteId, userId);
        Object[] stats = timelineStayRepository.getStatisticsByFavoriteLocationId(favoriteId, userId);
        Object[] visitCounts = timelineStayRepository.getVisitCountsByFavoriteLocationId(favoriteId, userId);

        return buildStatisticsDTO(totalVisits, stats, visitCounts);
    }

    private PlaceStatisticsDTO calculateGeocodingStatistics(Long geocodingId, UUID userId) {
        long totalVisits = timelineStayRepository.countByGeocodingLocationId(geocodingId, userId);
        Object[] stats = timelineStayRepository.getStatisticsByGeocodingLocationId(geocodingId, userId);
        Object[] visitCounts = timelineStayRepository.getVisitCountsByGeocodingLocationId(geocodingId, userId);

        return buildStatisticsDTO(totalVisits, stats, visitCounts);
    }

    private PlaceStatisticsDTO buildStatisticsDTO(long totalVisits, Object[] stats, Object[] visitCounts) {
        // Handle case where there are no visits
        if (stats[0] == null) {
            return PlaceStatisticsDTO.builder()
                    .totalVisits(0)
                    .visitsThisWeek(0)
                    .visitsThisMonth(0)
                    .visitsThisYear(0)
                    .totalDuration(0)
                    .averageDuration(0)
                    .minDuration(0)
                    .maxDuration(0)
                    .firstVisit(null)
                    .lastVisit(null)
                    .build();
        }

        return PlaceStatisticsDTO.builder()
                .totalVisits(totalVisits)
                .visitsThisWeek(((Number) visitCounts[0]).longValue())
                .visitsThisMonth(((Number) visitCounts[1]).longValue())
                .visitsThisYear(((Number) visitCounts[2]).longValue())
                .totalDuration(((Number) stats[0]).longValue())
                .averageDuration(((Number) stats[1]).longValue())
                .minDuration(((Number) stats[2]).longValue())
                .maxDuration(((Number) stats[3]).longValue())
                .firstVisit((Instant) stats[4])
                .lastVisit((Instant) stats[5])
                .build();
    }

    private PlaceVisitDTO convertToPlaceVisitDTO(TimelineStayEntity entity) {
        return PlaceVisitDTO.builder()
                .id(entity.getId())
                .timestamp(entity.getTimestamp())
                .stayDuration(entity.getStayDuration())
                .latitude(entity.getLocation().getY())
                .longitude(entity.getLocation().getX())
                .locationName(entity.getLocationName())
                .build();
    }

    /**
     * Find a related favorite location for a geocoding point that has no visits.
     * Checks if there's a favorite within 10 meters or an area containing the point.
     *
     * @param geocoding the geocoding location entity
     * @param userId    the user ID
     * @return FavoriteRelationDTO if a related favorite is found, null otherwise
     */
    private FavoriteRelationDTO findRelatedFavorite(ReverseGeocodingLocationEntity geocoding, UUID userId) {
        // Get the point coordinates (prefer result coordinates over request)
        Point point = geocoding.getResultCoordinates() != null
                ? geocoding.getResultCoordinates()
                : geocoding.getRequestCoordinates();

        if (point == null) {
            return null;
        }

        // Look for favorites within 10 meters for points, or containing this point for areas
        Optional<FavoritesEntity> favoriteOpt = favoritesRepository.findByPoint(userId, point, 10, 10);

        if (favoriteOpt.isEmpty()) {
            return null;
        }

        FavoritesEntity favorite = favoriteOpt.get();

        // Calculate distance and determine reason
        double distance = calculateDistance(point, favorite);
        String reason = determineRelationReason(point, favorite);

        // Get total visits for this favorite
        long totalVisits = timelineStayRepository.countByFavoriteLocationId(favorite.getId(), userId);

        return FavoriteRelationDTO.builder()
                .id(favorite.getId())
                .name(favorite.getName())
                .distanceMeters(distance)
                .reason(reason)
                .totalVisits(totalVisits)
                .build();
    }

    /**
     * Calculate distance between a point and a favorite location.
     */
    private double calculateDistance(Point point, FavoritesEntity favorite) {
        Geometry favoriteGeom = favorite.getGeometry();

        if (favorite.getType() == org.github.tess1o.geopulse.favorites.model.FavoriteLocationType.POINT) {
            // For point favorites, calculate direct distance
            Point favoritePoint = (Point) favoriteGeom;
            return calculateHaversineDistance(
                    point.getY(), point.getX(),
                    favoritePoint.getY(), favoritePoint.getX()
            );
        } else {
            // For area favorites, distance to the boundary (or 0 if contained)
            if (favoriteGeom.contains(point)) {
                return 0.0;
            }
            // Calculate distance to nearest point on boundary
            return favoriteGeom.distance(point) * 111320; // Convert degrees to meters (approximate)
        }
    }

    /**
     * Determine the relationship reason between a point and favorite.
     */
    private String determineRelationReason(Point point, FavoritesEntity favorite) {
        if (favorite.getType() == org.github.tess1o.geopulse.favorites.model.FavoriteLocationType.AREA
                && favorite.getGeometry().contains(point)) {
            return "contains_point";
        }
        return "nearby";
    }

    /**
     * Calculate distance between two lat/lon points using Haversine formula.
     * Returns distance in meters.
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth's radius in meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private String validateSortField(String sortBy) {
        // Only allow specific fields to be sorted
        return switch (sortBy != null ? sortBy.toLowerCase() : "") {
            case "timestamp", "stayduration", "stayDuration" -> sortBy;
            default -> "timestamp"; // Default sort field
        };
    }
}
