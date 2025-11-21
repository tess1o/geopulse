package org.github.tess1o.geopulse.user.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.model.UserResponse;

/**
 * Mapper for converting between User entities and DTOs.
 */
@ApplicationScoped
public class UserMapper {

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
                .hasPassword(entity.getPasswordHash() != null)
                .timezone(entity.getTimezone())
                .customMapTileUrl(entity.getCustomMapTileUrl())
                .measureUnit(entity.getMeasureUnit())
                .defaultRedirectUrl(entity.getDefaultRedirectUrl())
                .shareLocationWithFriends(entity.isShareLocationWithFriends())
                .build();
    }
}