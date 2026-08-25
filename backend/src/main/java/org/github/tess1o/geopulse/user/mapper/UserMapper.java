package org.github.tess1o.geopulse.user.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.service.DemoModeService;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.model.UserResponse;

/**
 * Mapper for converting between User entities and DTOs.
 */
@ApplicationScoped
public class UserMapper {

    @Inject
    DemoModeService demoModeService;

    @Inject
    MapMatchingConfiguration mapMatchingConfiguration;

    /**
     * Convert a UserEntity to a UserResponse DTO.
     *
     * @param entity The user entity
     * @return The user response DTO
     */
    public UserResponse toResponse(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        boolean mapMatchingAvailable = mapMatchingConfiguration != null && mapMatchingConfiguration.isAvailable();

        return UserResponse.builder()
                .userId(entity.getId())
                .email(entity.getEmail())
                .avatar(entity.getAvatar())
                .fullName(entity.getFullName())
                .role(entity.getRole() != null ? entity.getRole().name() : "USER")
                .demoMode(demoModeService.isEnabled())
                .canViewAdmin(demoModeService.canViewAdmin(entity))
                .adminReadOnly(demoModeService.isAdminReadOnly(entity))
                .hasPassword(entity.getPasswordHash() != null)
                .timezone(entity.getTimezone())
                .customMapTileUrl(entity.getCustomMapTileUrl())
                .customMapStyleUrl(entity.getCustomMapStyleUrl())
                .mapRenderMode(entity.getMapRenderMode() != null ? entity.getMapRenderMode() : MapRenderMode.VECTOR)
                .distanceUnit(entity.getDistanceUnit())
                .temperatureUnit(entity.getTemperatureUnit())
                .defaultRedirectUrl(entity.getDefaultRedirectUrl())
                .dateFormat(entity.getDateFormat())
                .timeFormat(entity.getTimeFormat())
                .defaultDateRangePreset(entity.getDefaultDateRangePreset())
                .autoShowTripReplayControls(entity.getTimelineDisplayAutoShowTripReplayControls() != null
                        ? entity.getTimelineDisplayAutoShowTripReplayControls() : true)
                .mapMatchingEnabled(mapMatchingAvailable
                        && Boolean.TRUE.equals(entity.getTimelineDisplayMapMatchingEnabled()))
                .mapMatchingAvailable(mapMatchingAvailable)
                .build();
    }
}
