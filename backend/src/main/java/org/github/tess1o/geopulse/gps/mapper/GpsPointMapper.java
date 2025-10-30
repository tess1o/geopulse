package org.github.tess1o.geopulse.gps.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichLocation;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantGpsData;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantLocation;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointDTO;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class GpsPointMapper {

    public GpsPointEntity toEntity(OwnTracksLocationMessage message, UserEntity userId, String deviceId, GpsSourceType sourceType) {
        String device = deviceId != null ? deviceId : "Unknown Device";
        GpsPointEntity entity = new GpsPointEntity();
        entity.setDeviceId(device);
        entity.setUser(userId);
        entity.setCoordinates(GeoUtils.createPoint(message.getLon(), message.getLat()));
        entity.setTimestamp(Instant.ofEpochSecond(message.getTst()));
        entity.setAccuracy(message.getAcc());
        entity.setBattery(message.getBatt());
        entity.setVelocity(message.getVel());
        entity.setAltitude(message.getAlt());
        entity.setSourceType(sourceType);
        entity.setCreatedAt(Instant.now());

        return entity;
    }

    public GpsPointEntity toEntity(OverlandLocationMessage message, UserEntity userId, GpsSourceType sourceType) {
        GpsPointEntity entity = new GpsPointEntity();
        entity.setDeviceId(message.getProperties().getDeviceId());
        entity.setUser(userId);
        entity.setCoordinates(GeoUtils.createPoint(message.getGeometry().getCoordinates()[0], message.getGeometry().getCoordinates()[1]));
        entity.setTimestamp(message.getProperties().getTimestamp());
        entity.setAccuracy(message.getProperties().getVerticalAccuracy());
        entity.setBattery(message.getProperties().getBatteryLevel() * 100);
        // Convert speed from m/s to km/h for consistent storage (Overland sends m/s)
        Double speedMs = message.getProperties().getSpeed();
        entity.setVelocity(speedMs != null ? speedMs * 3.6 : null);
        entity.setAltitude(message.getProperties().getAltitude() * 1.0);
        entity.setSourceType(sourceType);
        entity.setCreatedAt(Instant.now());

        return entity;
    }

    public GpsPointEntity toEntity(DawarichLocation message, UserEntity userId, GpsSourceType sourceType) {
        GpsPointEntity entity = new GpsPointEntity();
        entity.setDeviceId(message.getProperties().getDeviceId());
        entity.setUser(userId);
        entity.setCoordinates(GeoUtils.createPoint(message.getGeometry().getLongitude(), message.getGeometry().getLatitude()));
        entity.setTimestamp(message.getProperties().getTimestamp());
        entity.setAccuracy(message.getProperties().getVerticalAccuracy());
        // Convert speed from m/s to km/h for consistent storage (Dawarich sends m/s)
        Double speedMs = message.getProperties().getSpeed();
        entity.setVelocity(speedMs != null ? speedMs * 3.6 : null);
        entity.setAltitude(Math.round(message.getProperties().getAltitude()) * 1.0);
        entity.setBattery(-1.0);
        entity.setSourceType(sourceType);
        entity.setCreatedAt(Instant.now());


        return entity;
    }

    public GpsPointEntity toEntity(HomeAssistantGpsData message, UserEntity userId, GpsSourceType sourceType) {
        GpsPointEntity entity = new GpsPointEntity();
        entity.setUser(userId);
        entity.setTimestamp(message.getTimestamp());
        entity.setDeviceId(message.getDeviceId());

        HomeAssistantLocation location = message.getLocation();

        entity.setCoordinates(GeoUtils.createPoint(location.getLongitude(), location.getLatitude()));
        entity.setAccuracy(location.getAccuracy());
        // Convert speed from m/s to km/h for consistent storage (HomeAssistant sends m/s)
        Double speedMs = location.getSpeed();
        entity.setVelocity(speedMs != null ? speedMs * 3.6 : null);
        entity.setAltitude(Math.round(location.getAltitude()) * 1.0);
        entity.setBattery(message.getBattery().getLevel() * 1.0);
        entity.setSourceType(sourceType);
        entity.setCreatedAt(Instant.now());


        return entity;
    }

    /**
     * Convert a GpsPointEntity to a GpsPointPathPointDTO.
     *
     * @param entity The GPS point entity
     * @return The GPS point path point DTO
     */
    public GpsPointPathPointDTO toPathPoint(GpsPointEntity entity) {
        if (entity == null || entity.getCoordinates() == null) {
            return null;
        }

        return new GpsPointPathPointDTO(
                entity.getId(),
                entity.getCoordinates().getX(), // Longitude
                entity.getCoordinates().getY(), // Latitude
                entity.getTimestamp(),
                entity.getAccuracy(),
                entity.getAltitude(),
                entity.getVelocity(),
                entity.getUser().getId(),
                entity.getSourceType().name()
        );
    }

    /**
     * Convert a list of GpsPointEntity objects to a list of GpsPointPathPointDTO objects.
     *
     * @param entities The list of GPS point entities
     * @return The list of GPS point path point DTOs
     */
    public List<GpsPointPathPointDTO> toPathPoints(List<GpsPointEntity> entities) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(this::toPathPoint)
                .collect(Collectors.toList());
    }

    /**
     * Convert a GpsPointEntity to an OwnTracksLocationMessage.
     *
     * @param entity The GPS point entity
     * @return The OwnTracks location message
     */
    public OwnTracksLocationMessage toOwnTracksLocationMessage(GpsPointEntity entity) {
        if (entity == null || entity.getCoordinates() == null) {
            return null;
        }

        return OwnTracksLocationMessage.builder()
                .lat(entity.getLatitude())
                .lon(entity.getLongitude())
                .tst(entity.getTimestamp().getEpochSecond())
                .acc(entity.getAccuracy())
                .batt(entity.getBattery())
                .vel(entity.getVelocity())
                .alt(entity.getAltitude())
                .type("location")
                .tid(entity.getDeviceId())
                .createdAt(entity.getCreatedAt().getEpochSecond())
                .build();
    }

    /**
     * Convert a list of GpsPointEntity objects to a list of OwnTracksLocationMessage objects.
     *
     * @param entities The list of GPS point entities
     * @return The list of OwnTracks location messages
     */
    public List<OwnTracksLocationMessage> toOwnTracksLocationMessages(List<GpsPointEntity> entities) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(this::toOwnTracksLocationMessage)
                .collect(Collectors.toList());
    }

    /**
     * Convert a GpsPointEntity to a GpsPointDTO.
     *
     * @param entity The GPS point entity
     * @return The GPS point DTO
     */
    public GpsPointDTO toGpsPointDTO(GpsPointEntity entity) {
        if (entity == null || entity.getCoordinates() == null) {
            return null;
        }

        GpsPointDTO.CoordinatesDTO coordinates = new GpsPointDTO.CoordinatesDTO(
                entity.getCoordinates().getY(), // Latitude
                entity.getCoordinates().getX()  // Longitude
        );

        return new GpsPointDTO(
                entity.getId(),
                entity.getTimestamp(),
                coordinates,
                entity.getAccuracy(),
                entity.getBattery(),
                entity.getVelocity(),
                entity.getAltitude(),
                entity.getSourceType().name()
        );
    }

    /**
     * Convert a list of GpsPointEntity objects to a list of GpsPointDTO objects.
     *
     * @param entities The list of GPS point entities
     * @return The list of GPS point DTOs
     */
    public List<GpsPointDTO> toGpsPointDTOs(List<GpsPointEntity> entities) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(this::toGpsPointDTO)
                .collect(Collectors.toList());
    }
}