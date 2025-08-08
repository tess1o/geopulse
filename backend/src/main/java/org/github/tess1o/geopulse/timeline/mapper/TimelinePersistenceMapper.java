package org.github.tess1o.geopulse.timeline.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper for converting between timeline DTOs and entities for persistence.
 */
@ApplicationScoped
public class TimelinePersistenceMapper {

    /**
     * Convert a TimelineStayLocationDTO to a TimelineStayEntity for persistence.
     * Note: You must set the user, favoriteLocation, and geocodingLocation references
     * after calling this method based on the IDs in the DTO.
     *
     * @param dto the DTO to convert
     * @return timeline stay entity (without user/location references set)
     */
    public TimelineStayEntity toEntity(TimelineStayLocationDTO dto) {
        return TimelineStayEntity.builder()
                .timestamp(dto.getTimestamp())
                .stayDuration(dto.getStayDuration())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .locationName(dto.getLocationName())
                .build();
    }

    /**
     * Convert a TimelineStayEntity to a TimelineStayLocationDTO.
     *
     * @param entity the entity to convert
     * @return timeline stay location DTO
     */
    public TimelineStayLocationDTO toDTO(TimelineStayEntity entity) {
        return TimelineStayLocationDTO.builder()
                .timestamp(entity.getTimestamp())
                .stayDuration(entity.getStayDuration())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .locationName(entity.getLocationName())
                .favoriteId(entity.getFavoriteLocation() != null ? entity.getFavoriteLocation().getId() : null)
                .geocodingId(entity.getGeocodingLocation() != null ? entity.getGeocodingLocation().getId() : null)
                .build();
    }

    /**
     * Set the location references on an entity based on the DTO IDs.
     * This helper method handles the foreign key relationships.
     *
     * @param entity            the entity to update
     * @param dto               the source DTO with the IDs
     * @param user              the user entity
     * @param favoriteLocation  the favorite location entity (null if not applicable)
     * @param geocodingLocation the geocoding location entity (null if not applicable)
     */
    public void setLocationReferences(TimelineStayEntity entity,
                                      TimelineStayLocationDTO dto,
                                      UserEntity user,
                                      FavoritesEntity favoriteLocation,
                                      ReverseGeocodingLocationEntity geocodingLocation) {
        entity.setUser(user);
        entity.setFavoriteLocation(favoriteLocation);
        entity.setGeocodingLocation(geocodingLocation);
    }

    /**
     * Convert a list of TimelineStayEntity to TimelineStayLocationDTO list.
     */
    public List<TimelineStayLocationDTO> toStayDTOs(List<TimelineStayEntity> entities) {
        return entities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert a TimelineTripEntity to a TimelineTripDTO.
     */
    public TimelineTripDTO toTripDTO(TimelineTripEntity entity) {
        TimelineTripDTO.TimelineTripDTOBuilder builder = TimelineTripDTO.builder()
                .timestamp(entity.getTimestamp())
                .latitude(entity.getStartLatitude())
                .longitude(entity.getStartLongitude())
                .tripDuration(entity.getTripDuration())
                .distanceKm(entity.getDistanceKm())
                .movementType(entity.getMovementType());

        // Convert LineString path to GPS points if available
        if (entity.getPath() != null) {
            List<GpsPoint> pathPoints = convertLineStringToGpsPoints(entity.getPath());
            builder.path(pathPoints);
        }

        return builder.build();
    }

    /**
     * Convert a list of TimelineTripEntity to TimelineTripDTO list.
     */
    public List<TimelineTripDTO> toTripDTOs(List<TimelineTripEntity> entities) {
        return entities.stream()
                .map(this::toTripDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert TimelineTripDTO to TimelineTripEntity for persistence.
     */
    public TimelineTripEntity toTripEntity(TimelineTripDTO dto) {
        LineString pathGeometry = null;
        if (dto.getPath() != null && !dto.getPath().isEmpty()) {
            pathGeometry = GeoUtils.convertGpsPointsToLineString(dto.getPath());
        }

        // Calculate end coordinates from path or use start coordinates as fallback
        double endLat = dto.getLatitude();
        double endLon = dto.getLongitude();

        if (dto.getPath() != null && !dto.getPath().isEmpty()) {
            GpsPoint lastPoint = dto.getPath().get(dto.getPath().size() - 1);
            endLat = lastPoint.getLatitude();
            endLon = lastPoint.getLongitude();
        }

        return TimelineTripEntity.builder()
                .timestamp(dto.getTimestamp())
                .tripDuration(dto.getTripDuration())
                .startLatitude(dto.getLatitude())
                .startLongitude(dto.getLongitude())
                .endLatitude(endLat)
                .endLongitude(endLon)
                .distanceKm(dto.getDistanceKm())
                .movementType(dto.getMovementType())
                .path(pathGeometry)
                .build();
    }

    /**
     * Convert LineString geometry to list of GPS points.
     */
    private List<GpsPoint> convertLineStringToGpsPoints(LineString lineString) {
        return java.util.stream.IntStream.range(0, lineString.getNumPoints())
                .mapToObj(i -> {
                    Coordinate coord = lineString.getCoordinateN(i);
                    return new SimpleGpsPoint(coord.y, coord.x); // Note: y=lat, x=lon
                })
                .collect(Collectors.toList());
    }


    /**
     * Convert lists of entities to a MovementTimelineDTO.
     *
     * @param userId       user ID
     * @param stayEntities list of stay entities
     * @param tripEntities list of trip entities
     * @return complete movement timeline DTO
     */
    public MovementTimelineDTO toMovementTimelineDTO(UUID userId, List<TimelineStayEntity> stayEntities, List<TimelineTripEntity> tripEntities) {
        List<TimelineStayLocationDTO> stays = toStayDTOs(stayEntities);
        List<TimelineTripDTO> trips = toTripDTOs(tripEntities);

        return new MovementTimelineDTO(userId, stays, trips);
    }

    /**
     * Convert lists of entities to a MovementTimelineDTO including data gaps.
     *
     * @param userId       user ID
     * @param stayEntities list of stay entities
     * @param tripEntities list of trip entities
     * @param dataGapEntities list of data gap entities
     * @return complete movement timeline DTO with data gaps
     */
    public MovementTimelineDTO toMovementTimelineDTO(UUID userId, 
                                                   List<TimelineStayEntity> stayEntities, 
                                                   List<TimelineTripEntity> tripEntities,
                                                   List<TimelineDataGapEntity> dataGapEntities) {
        List<TimelineStayLocationDTO> stays = toStayDTOs(stayEntities);
        List<TimelineTripDTO> trips = toTripDTOs(tripEntities);
        List<TimelineDataGapDTO> dataGaps = toDataGapDTOs(dataGapEntities);

        return new MovementTimelineDTO(userId, stays, trips, dataGaps);
    }

    /**
     * Convert MovementTimelineDTO stays to entities list.
     */
     public List<TimelineStayEntity> toStayEntities(MovementTimelineDTO timeline) {
         return timeline.getStays().stream()
                 .map(this::toEntity)
                 .collect(Collectors.toList());
     }

    /**
     * Convert MovementTimelineDTO trips to entities list.
     */
    public List<TimelineTripEntity> toTripEntities(MovementTimelineDTO timeline) {
        return timeline.getTrips().stream()
                .map(this::toTripEntity)
                .collect(Collectors.toList());
    }

    /**
     * Convert a TimelineDataGapEntity to a TimelineDataGapDTO.
     */
    public TimelineDataGapDTO toDataGapDTO(TimelineDataGapEntity entity) {
        return new TimelineDataGapDTO(entity.getStartTime(), entity.getEndTime(), entity.getDurationSeconds());
    }

    /**
     * Convert a TimelineDataGapDTO to a TimelineDataGapEntity for persistence.
     */
    public TimelineDataGapEntity toDataGapEntity(TimelineDataGapDTO dto) {
        return TimelineDataGapEntity.builder()
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .durationSeconds(dto.getDurationSeconds())
                .build();
    }

    /**
     * Convert a list of TimelineDataGapEntity to TimelineDataGapDTO list.
     */
    public List<TimelineDataGapDTO> toDataGapDTOs(List<TimelineDataGapEntity> entities) {
        return entities.stream()
                .map(this::toDataGapDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert MovementTimelineDTO data gaps to entities list.
     */
    public List<TimelineDataGapEntity> toDataGapEntities(MovementTimelineDTO timeline) {
        if (timeline.getDataGaps() == null) {
            return List.of();
        }
        return timeline.getDataGaps().stream()
                .map(this::toDataGapEntity)
                .collect(Collectors.toList());
    }

    /**
     * Simple GPS point implementation for conversion.
     */
    private static class SimpleGpsPoint implements GpsPoint {
        private final double latitude;
        private final double longitude;

        public SimpleGpsPoint(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        public double getLatitude() {
            return latitude;
        }

        @Override
        public double getLongitude() {
            return longitude;
        }

        @Override
        public java.time.Instant getTimestamp() {
            // For converted geometry points, timestamp is not meaningful
            return null;
        }
    }
}