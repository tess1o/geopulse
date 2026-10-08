package org.github.tess1o.geopulse.user.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.service.DemoModeService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.model.UserResponse;
import org.github.tess1o.geopulse.user.service.UserService;

/**
 * Mapper for converting between User entities and DTOs.
 */
@ApplicationScoped
public class UserMapper {

    @Inject
    DemoModeService demoModeService;

    @Inject
    UserService userService;

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
                .createdAt(entity.getCreatedAt())
                .uiPreferences(entity.getUiPreferences().withDefaults())
                .timelineDisplay(userService.getTimelineDisplaySettings(entity))
                .build();
    }
}
