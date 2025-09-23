package org.github.tess1o.geopulse.streaming.service.converters;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.model.domain.*;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.service.trips.GpsStatisticsCalculator;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Coordinate;

import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Service responsible for converting between streaming timeline models and existing DTOs.
 * This ensures compatibility with the existing API response format while using
 * the new streaming algorithm internally.
 */
@Slf4j
@ApplicationScoped
public class StreamingTimelineConverter {

    private final EntityManager entityManager;
    private final GpsStatisticsCalculator gpsStatisticsCalculator;

    public StreamingTimelineConverter(EntityManager entityManager, 
                                    GpsStatisticsCalculator gpsStatisticsCalculator) {
        this.entityManager = entityManager;
        this.gpsStatisticsCalculator = gpsStatisticsCalculator;
    }


    /**
     * Convert domain GPS point to shared GPS point interface for LineString conversion.
     */
    private GpsPoint convertDomainToSharedGpsPoint(GPSPoint domainPoint) {
        return new org.github.tess1o.geopulse.shared.geo.GpsPoint() {
            @Override
            public double getLatitude() {
                return domainPoint.getLatitude();
            }

            @Override
            public double getLongitude() {
                return domainPoint.getLongitude();
            }

            @Override
            public java.time.Instant getTimestamp() {
                return domainPoint.getTimestamp();
            }
        };
    }

    /**
     * Convert TimelineStayLocationDTO to TimelineStayEntity using pre-loaded entity maps
     * to eliminate N+1 queries. This is an optimized version for batch processing.
     *
     * @param stay         DTO to convert
     * @param userRef      user entity reference for the stay
     * @param favoriteMap  pre-loaded map of favorite ID to FavoritesEntity
     * @param geocodingMap pre-loaded map of geocoding ID to ReverseGeocodingLocationEntity
     * @return converted entity
     */
    public TimelineStayEntity convertStayToEntityWithBatchData(
            Stay stay,
            UserEntity userRef,
            java.util.Map<Long, FavoritesEntity> favoriteMap,
            java.util.Map<Long, ReverseGeocodingLocationEntity> geocodingMap) {
        
        if (stay == null) return null;

        TimelineStayEntity entity = new TimelineStayEntity();
        entity.setUser(userRef);
        entity.setTimestamp(stay.getStartTime());
        entity.setLocation(GeoUtils.createPoint(stay.getLongitude(), stay.getLatitude()));
        entity.setStayDuration(stay.getDuration().toSeconds()); // Already in seconds
        entity.setLocationName(stay.getLocationName());
        entity.setLocationSource(getLocationSource(stay));

        // Set favorite location reference using pre-loaded map (O(1) lookup)
        if (stay.getFavoriteId() != null && stay.getFavoriteId() != 0) {
            FavoritesEntity favorite = favoriteMap.get(stay.getFavoriteId());
            if (favorite != null) {
                entity.setFavoriteLocation(favorite);
            } else {
                log.warn("Favorite entity with ID {} not found in batch-loaded map", stay.getFavoriteId());
            }
        }

        // Set geocoding location reference using pre-loaded map (O(1) lookup)
        if (stay.getGeocodingId() != null && stay.getGeocodingId() != 0) {
            ReverseGeocodingLocationEntity geocodingEntity = geocodingMap.get(stay.getGeocodingId());
            if (geocodingEntity != null) {
                entity.setGeocodingLocation(geocodingEntity);
            } else {
                log.warn("Geocoding entity with ID {} not found in batch-loaded map", stay.getGeocodingId());
            }
        }

        return entity;
    }

    /**
     * Convert streaming Trip domain object to TimelineTripEntity with GPS statistics.
     * This method calculates and stores GPS-based speed statistics for enhanced classification.
     *
     * @param trip    streaming Trip domain object to convert
     * @param userRef user entity reference for the trip
     * @return converted entity with GPS statistics
     */
    public TimelineTripEntity convertStreamingTripToEntity(Trip trip, UserEntity userRef) {
        if (trip == null) return null;

        TimelineTripEntity entity = new TimelineTripEntity();
        entity.setUser(userRef);
        entity.setTimestamp(trip.getStartTime());
        entity.setTripDuration(trip.getDuration().toSeconds());
        entity.setDistanceMeters((long) trip.getDistanceMeters());
        entity.setMovementType(trip.getTripType() != null ? trip.getTripType().name() : "UNKNOWN");

        // Set start and end points
        GPSPoint startLocation = trip.getStartLocation();
        GPSPoint endLocation = trip.getEndLocation();
        if (startLocation != null) {
            entity.setStartPoint(GeoUtils.createPoint(startLocation.getLongitude(), startLocation.getLatitude()));
        }
        if (endLocation != null) {
            entity.setEndPoint(GeoUtils.createPoint(endLocation.getLongitude(), endLocation.getLatitude()));
        }

        // Convert path and calculate GPS statistics
        List<GPSPoint> tripPath = trip.getPath();
        if (tripPath != null && !tripPath.isEmpty()) {
            // Convert domain GPS points to shared GPS points for LineString conversion
            List<GpsPoint> sharedGpsPoints = tripPath.stream()
                    .map(this::convertDomainToSharedGpsPoint)
                    .toList();
            entity.setPath(convertPathToLineString(sharedGpsPoints));

            // Calculate and store GPS statistics
            GpsStatisticsCalculator.GpsStatistics stats = gpsStatisticsCalculator.calculateStatistics(tripPath);
            if (stats.hasValidData()) {
                entity.setAvgGpsSpeed(stats.avgGpsSpeed());
                entity.setMaxGpsSpeed(stats.maxGpsSpeed());
                entity.setSpeedVariance(stats.speedVariance());
                entity.setLowAccuracyPointsCount(stats.lowAccuracyPointsCount());
            } else {
                log.debug("No valid GPS statistics calculated for trip with {} points", tripPath.size());
            }
        }

        return entity;
    }

    /**
     * Convert TimelineTripDTO to TimelineTripEntity.
     * This is the legacy method for DTO-based conversion without GPS statistics.
     *
     * @param trip    DTO to convert
     * @param userRef user entity reference for the trip
     * @return converted entity
     */
    public TimelineTripEntity convertTripToEntity(Trip trip, UserEntity userRef) {
        if (trip == null) return null;

        TimelineTripEntity entity = new TimelineTripEntity();
        entity.setUser(userRef);
        entity.setTimestamp(trip.getStartTime());
        entity.setStartPoint(GeoUtils.createPoint(trip.getStartLocation().getLongitude(), trip.getStartLocation().getLatitude()));
        entity.setEndPoint(GeoUtils.createPoint(trip.getEndLocation().getLongitude(), trip.getEndLocation().getLatitude()));

        entity.setDistanceMeters(Double.valueOf(trip.getDistanceMeters()).longValue());
        entity.setTripDuration(trip.getDuration().toSeconds()); // Already in seconds
        entity.setMovementType(trip.getTripType().name());

        // Convert path from List<GpsPoint> to LineString
        if (trip.getPath() != null && !trip.getPath().isEmpty()) {
            entity.setPath(convertPathToLineString(trip.getPath()));
        }

        return entity;
    }

    /**
     * Convert TimelineDataGapDTO to TimelineDataGapEntity.
     *
     * @param gap     DTO to convert
     * @param userRef user entity reference for the gap
     * @return converted entity
     */
    public TimelineDataGapEntity convertGapToEntity(DataGap gap, UserEntity userRef) {
        if (gap == null) return null;

        TimelineDataGapEntity entity = new TimelineDataGapEntity();
        entity.setUser(userRef);
        entity.setStartTime(gap.getStartTime());
        entity.setEndTime(gap.getEndTime());
        entity.setDurationSeconds(gap.getDuration().getSeconds());

        return entity;
    }

    // =================== Entity to DTO Conversions ===================

    /**
     * Convert TimelineStayEntity to TimelineStayLocationDTO.
     *
     * @param entity entity to convert
     * @return converted DTO
     */
    public TimelineStayLocationDTO convertStayEntityToDto(TimelineStayEntity entity) {
        if (entity == null) return null;

        return TimelineStayLocationDTO.builder()
                .timestamp(entity.getTimestamp())
                .longitude(entity.getLocation().getX())
                .latitude(entity.getLocation().getY())
                .stayDuration(entity.getStayDuration()) // Already in seconds
                .locationName(entity.getLocationName() != null ? entity.getLocationName() : "Unknown location")
                .favoriteId(entity.getFavoriteLocation() != null ? entity.getFavoriteLocation().getId() : null)
                .geocodingId(entity.getGeocodingLocation() != null ? entity.getGeocodingLocation().getId() : null)
                .build();
    }

    /**
     * Convert TimelineTripEntity to TimelineTripDTO.
     *
     * @param entity entity to convert
     * @return converted DTO
     */
    public TimelineTripDTO convertTripEntityToDto(TimelineTripEntity entity) {
        if (entity == null) return null;

        TimelineTripDTO.TimelineTripDTOBuilder builder = TimelineTripDTO.builder()
                .timestamp(entity.getTimestamp())
                .longitude(entity.getStartPoint().getX())
                .latitude(entity.getStartPoint().getY())
                .endLongitude(entity.getEndPoint().getX())
                .endLatitude(entity.getEndPoint().getY())
                .tripDuration(entity.getTripDuration()) // Already in seconds
                .distanceMeters(entity.getDistanceMeters())
                .movementType(entity.getMovementType());

        // Convert LineString path to GPS points if available
        if (entity.getPath() != null) {
            List<GpsPoint> pathPoints = convertLineStringToGpsPoints(entity.getPath());
            builder.path(pathPoints);
        }

        return builder.build();
    }

    // =================== Helper Methods ===================

    /**
     * Determine location source based on DTO fields.
     */
    private LocationSource getLocationSource(Stay stay) {
        if (stay.getFavoriteId() != null && stay.getFavoriteId() != 0) {
            return LocationSource.FAVORITE;
        }
        if (stay.getGeocodingId() != null && stay.getGeocodingId() != 0) {
            return LocationSource.GEOCODING;
        }
        return LocationSource.HISTORICAL;
    }

    /**
     * Convert GPS point path to JTS LineString geometry.
     */
    private LineString convertPathToLineString(List<? extends GpsPoint> path) {
        if (path == null || path.size() < 2) {
            return null;
        }

        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate[] coordinates = path.stream()
                .map(point -> new Coordinate(point.getLongitude(), point.getLatitude()))
                .toArray(Coordinate[]::new);

        return geometryFactory.createLineString(coordinates);
    }

    /**
     * Convert JTS LineString to GPS points.
     */
    private List<GpsPoint> convertLineStringToGpsPoints(LineString lineString) {
        return IntStream.range(0, lineString.getNumPoints())
                .mapToObj(i -> {
                    Coordinate coord = lineString.getCoordinateN(i);
                    return new GPSPoint(coord.y, coord.x, 0, 0); // Note: y=lat, x=lon
                })
                .collect(Collectors.toList());
    }

    /**
     * Create a minimal user reference for entity relationships.
     * This avoids loading the full user entity just for the foreign key reference.
     */
    public UserEntity createUserReference(UUID userId) {
        return entityManager.getReference(UserEntity.class, userId);
    }
}