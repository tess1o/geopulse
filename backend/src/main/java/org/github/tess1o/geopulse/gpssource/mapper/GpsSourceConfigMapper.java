package org.github.tess1o.geopulse.gpssource.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gpssource.model.CreateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigDTO;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.PasswordUtils;

@ApplicationScoped
public class GpsSourceConfigMapper {

    private final PasswordUtils passwordUtils;

    public GpsSourceConfigMapper(PasswordUtils passwordUtils) {
        this.passwordUtils = passwordUtils;
    }

    public GpsSourceConfigDTO toDTO(GpsSourceConfigEntity config) {
        return GpsSourceConfigDTO.builder()
                .id(config.getId())
                .userId(config.getUser().getId())
                .username(config.getUsername())
                .token(config.getToken())
                .type(config.getSourceType().name())
                .active(config.isActive())
                .build();
    }

    public GpsSourceConfigEntity toEntity(CreateGpsSourceConfigDto newConfig, UserEntity user) {
        String hashedPassword = newConfig.getPassword() == null || newConfig.getPassword().isEmpty()
                ? "" : passwordUtils.hashPassword(newConfig.getPassword());
        return GpsSourceConfigEntity.builder()
                .user(user)
                .active(true)
                .token(newConfig.getToken())
                .username(newConfig.getUsername())
                .passwordHash(hashedPassword)
                .sourceType(newConfig.getType())
                .build();
    }
}
