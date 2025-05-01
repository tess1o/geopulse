package org.github.tess1o.geopulse.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.model.dto.UserResponse;
import org.github.tess1o.geopulse.model.entity.UserEntity;

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
        
        return new UserResponse(
            entity.getUserId(),
            entity.getDeviceId()
        );
    }
}