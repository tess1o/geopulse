package org.github.tess1o.geopulse.sharing.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class SharedLinkMapper {

    public SharedLinkEntity toEntity(CreateShareLinkRequest createShareLinkRequest, UserEntity user) {
        SharedLinkEntity sharedLinkEntity = new SharedLinkEntity();
        sharedLinkEntity.setName(createShareLinkRequest.getName());
        sharedLinkEntity.setExpiresAt(createShareLinkRequest.getExpiresAt());
        sharedLinkEntity.setPassword(createShareLinkRequest.getPassword());
        sharedLinkEntity.setShowHistory(createShareLinkRequest.isShowHistory());
        sharedLinkEntity.setHistoryHours(createShareLinkRequest.getHistoryHours());
        sharedLinkEntity.setUser(user);
        return sharedLinkEntity;
    }

    public CreateShareLinkResponse toResponse(SharedLinkEntity sharedLinkEntity) {
        CreateShareLinkResponse response = new CreateShareLinkResponse();
        response.setId(sharedLinkEntity.getId());
        response.setName(sharedLinkEntity.getName());
        response.setExpiresAt(sharedLinkEntity.getExpiresAt());
        response.setHasPassword(sharedLinkEntity.getPassword() != null);
        response.setShowHistory(sharedLinkEntity.isShowHistory());
        response.setHistoryHours(sharedLinkEntity.getHistoryHours());
        response.setCreatedAt(sharedLinkEntity.getCreatedAt());
        return response;
    }

    public SharedLinkDto toDto(SharedLinkEntity entity) {
        return SharedLinkDto.builder()
                .id(entity.getId().toString())
                .name(entity.getName())
                .expiresAt(entity.getExpiresAt())
                .hasPassword(entity.getPassword() != null)
                .showHistory(entity.isShowHistory())
                .historyHours(entity.getHistoryHours())
                .isActive(entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(Instant.now()))
                .createdAt(entity.getCreatedAt())
                .viewCount(entity.getViewCount())
                .build();
    }

    public SharedLocationInfo toLocationInfo(SharedLinkEntity entity) {
        return SharedLocationInfo.builder()
                .id(entity.getId())
                .name(entity.getName())
                .expiresAt(entity.getExpiresAt())
                .hasPassword(entity.getPassword() != null)
                .showHistory(entity.isShowHistory())
                .historyHours(entity.getHistoryHours())
                .isActive(entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(Instant.now()))
                .createdAt(entity.getCreatedAt())
                .viewCount(entity.getViewCount())
                .sharedBy(entity.getUser().getFullName() != null ?
                        entity.getUser().getFullName() : entity.getUser().getEmail())
                .build();
    }

    public LocationHistoryResponse.CurrentLocationData toCurrentLocationData(GpsPointEntity location) {
        return new LocationHistoryResponse.CurrentLocationData(
                location.getCoordinates().getY(), // latitude
                location.getCoordinates().getX(), // longitude
                location.getTimestamp(),
                location.getAccuracy()
        );
    }

    public LocationHistoryResponse.HistoricalLocationData toHistoricalLocationData(GpsPointEntity location) {
        return new LocationHistoryResponse.HistoricalLocationData(
                location.getCoordinates().getY(), // latitude
                location.getCoordinates().getX(), // longitude
                location.getTimestamp()
        );
    }

    public LocationHistoryResponse toLocationHistoryResponse(GpsPointEntity currentLocation, List<GpsPointEntity> history) {
        LocationHistoryResponse.CurrentLocationData current = currentLocation != null ?
                toCurrentLocationData(currentLocation) : null;

        List<LocationHistoryResponse.HistoricalLocationData> historicalData =
                history.stream()
                        .map(this::toHistoricalLocationData)
                        .collect(Collectors.toList());

        return new LocationHistoryResponse(current, historicalData);
    }

    public ShareLinkResponse toShareLinkResponse(GpsPointEntity location) {
        return new ShareLinkResponse(
                location.getCoordinates().getY(), // latitude
                location.getCoordinates().getX(), // longitude
                location.getTimestamp(),
                location.getAccuracy() != null ? location.getAccuracy() : 0.0
        );
    }

    public void updateEntityFromDto(SharedLinkEntity entity, UpdateShareLinkDto dto) {
        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }
        if (dto.getExpiresAt() != null) {
            entity.setExpiresAt(dto.getExpiresAt());
        }
        if (dto.getPassword() != null) {
            entity.setPassword(dto.getPassword());
        } else if (dto.isPasswordRemoval()) {
            entity.setPassword(null);
        }
        entity.setShowHistory(dto.isShowHistory());
        entity.setHistoryHours(dto.getHistoryHours());
    }
}
