package org.github.tess1o.geopulse.favorites.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.favorites.model.*;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class FavoriteLocationMapper {

    @Inject
    EntityManager entityManager;

    public FavoritesEntity toEntity(AddPointToFavoritesDto favorite, UUID userId) {
        return FavoritesEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .geometry(GeoUtils.createPoint(favorite.getLon(), favorite.getLat()))
                .name(favorite.getName())
                .type(FavoriteLocationType.POINT)
                .build();
    }

    public FavoritesEntity toEntity(AddAreaToFavoritesDto favorite, UUID userId) {
        return FavoritesEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .geometry(GeoUtils.createRectangleFromLeafletBounds(favorite.getNorthEastLat(),
                        favorite.getNorthEastLon(),
                        favorite.getSouthWestLat(),
                        favorite.getSouthWestLon()))
                .name(favorite.getName())
                .type(FavoriteLocationType.AREA)
                .build();
    }

    public FavoriteLocationsDto toFavoriteLocationDto(List<FavoritesEntity> favorites) {
        List<FavoritePointDto> points = favorites.stream()
                .map(this::toFavoritePointDto)
                .filter(Objects::nonNull)
                .toList();
        List<FavoriteAreaDto> areas = favorites.stream()
                .map(this::toFavoriteAreaDto)
                .filter(Objects::nonNull)
                .toList();
        return new FavoriteLocationsDto(points, areas);
    }

    public FavoritePointDto toFavoritePointDto(FavoritesEntity favorite) {
        if (favorite.getType() != FavoriteLocationType.POINT ||
                !(favorite.getGeometry() instanceof Point point)) {
            return null;
        }
        return FavoritePointDto.builder()
                .id(favorite.getId())
                .userId(favorite.getUser().getId())
                .name(favorite.getName())
                .longitude(point.getX())
                .latitude(point.getY())
                .type(FavoriteLocationType.POINT.name())
                .build();
    }

    public FavoriteAreaDto toFavoriteAreaDto(FavoritesEntity favorite) {
        if (favorite.getType() != FavoriteLocationType.AREA) {
            return null;
        }
        Envelope envelope = favorite.getGeometry().getEnvelopeInternal(); // minX, maxX = lon; minY, maxY = lat

        FavoriteAreaDto dto = new FavoriteAreaDto();
        dto.setId(favorite.getId());
        dto.setNorthEastLat(envelope.getMaxY());
        dto.setNorthEastLon(envelope.getMaxX());
        dto.setSouthWestLat(envelope.getMinY());
        dto.setSouthWestLon(envelope.getMinX());
        dto.setName(favorite.getName());
        dto.setUserId(favorite.getUser().getId());
        dto.setType(FavoriteLocationType.AREA.name());

        return dto;
    }
}
