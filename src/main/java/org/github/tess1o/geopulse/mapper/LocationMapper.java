package org.github.tess1o.geopulse.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.model.dto.LocationMessage;
import org.github.tess1o.geopulse.model.dto.LocationPathPointDTO;
import org.github.tess1o.geopulse.model.entity.LocationEntity;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class LocationMapper {
    private final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326);

    public LocationEntity toEntity(LocationMessage message, String userId, String deviceId) {
        LocationEntity entity = new LocationEntity();
        entity.setDeviceId(deviceId);
        entity.setUserId(userId);

        // Create PostGIS Point
        Point point = geometryFactory.createPoint(
                new Coordinate(message.getLon(), message.getLat())
        );
        entity.setLocation(point);

        entity.setTimestamp(Instant.ofEpochSecond(message.getTst()));
        entity.setAccuracy(message.getAcc());
        entity.setBattery(message.getBatt());
        entity.setVelocity(message.getVel());
        entity.setAltitude(message.getAlt());
        entity.setCreatedAt(Instant.now());

        return entity;
    }

    /**
     * Convert a LocationEntity to a LocationPathPointDTO.
     *
     * @param entity The location entity
     * @return The location path point DTO
     */
    public LocationPathPointDTO toPathPoint(LocationEntity entity) {
        if (entity == null || entity.getLocation() == null) {
            return null;
        }

        return new LocationPathPointDTO(
            entity.getLocation().getY(), // Latitude
            entity.getLocation().getX(), // Longitude
            entity.getTimestamp(),
            entity.getAccuracy(),
            entity.getAltitude(),
            entity.getVelocity()
        );
    }

    /**
     * Convert a list of LocationEntity objects to a list of LocationPathPointDTO objects.
     *
     * @param entities The list of location entities
     * @return The list of location path point DTOs
     */
    public List<LocationPathPointDTO> toPathPoints(List<LocationEntity> entities) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
            .map(this::toPathPoint)
            .collect(Collectors.toList());
    }
}
