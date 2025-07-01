package org.github.tess1o.geopulse.gpssource.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gpssource.mapper.GpsSourceConfigMapper;
import org.github.tess1o.geopulse.gpssource.model.CreateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.model.UpdateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.PasswordUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GpsSourceService implements GpsSourceConfigProvider {

    private final GpsSourceRepository gpsSourceRepository;
    private final GpsSourceConfigMapper gpsSourceMapper;
    private final PasswordUtils passwordUtils;
    private final EntityManager em;

    @Getter
    @ConfigProperty(name = "geopulse.gps.source.owntrack.url")
    String owntrackUrl;

    @Getter
    @ConfigProperty(name = "geopulse.gps.source.overland.url")
    String overlandUrl;

    @Inject
    public GpsSourceService(GpsSourceRepository gpsSourceRepository,
                            GpsSourceConfigMapper gpsSourceMapper, PasswordUtils passwordUtils,
                            EntityManager em) {
        this.gpsSourceRepository = gpsSourceRepository;
        this.gpsSourceMapper = gpsSourceMapper;
        this.passwordUtils = passwordUtils;
        this.em = em;
    }


    @Transactional
    public List<GpsSourceConfigDTO> findGpsSourceConfigs(UUID userId) {
        List<GpsSourceConfigEntity> configs = gpsSourceRepository.findByUserId(userId);
        return configs.stream().map(gpsSourceMapper::toDTO).toList();
    }

    @Transactional
    public GpsSourceConfigDTO addGpsSourceConfig(CreateGpsSourceConfigDto newConfig) {
        validateUniqueness(newConfig);
        UserEntity user = em.getReference(UserEntity.class, newConfig.getUserId());
        GpsSourceConfigEntity gpsSourceConfigEntity = gpsSourceMapper.toEntity(newConfig, user);
        gpsSourceRepository.persist(gpsSourceConfigEntity);
        return gpsSourceMapper.toDTO(gpsSourceConfigEntity);
    }

    private void validateUniqueness(CreateGpsSourceConfigDto newConfig) {
        if (newConfig.getType() == GpsSourceType.OWNTRACKS) {
            boolean isUnique = isOwnTrackSourceUnique(newConfig);
            if (!isUnique) {
                throw new IllegalArgumentException("Owntrack username is already used");
            }
        } else if (newConfig.getType() == GpsSourceType.OVERLAND) {
            boolean isUnique = isOverlandSourceUnique(newConfig);
            if (!isUnique) {
                throw new IllegalArgumentException("Overland token is already used");
            }
        }
    }

    private boolean isOwnTrackSourceUnique(CreateGpsSourceConfigDto newConfig) {
        List<GpsSourceConfigEntity> configs = gpsSourceRepository.findByUserIdAndSourceType(newConfig.getUserId(), GpsSourceType.OWNTRACKS);
        if (configs == null || configs.isEmpty()) {
            return true;
        }
        return configs.stream().noneMatch(config -> config.getUsername().equals(newConfig.getUsername()));
    }

    private boolean isOverlandSourceUnique(CreateGpsSourceConfigDto newConfig) {
        List<GpsSourceConfigEntity> configs = gpsSourceRepository.findByUserIdAndSourceType(newConfig.getUserId(), GpsSourceType.OVERLAND);
        if (configs == null || configs.isEmpty()) {
            return true;
        }
        return configs.stream().noneMatch(config -> config.getToken().equals(newConfig.getToken()));
    }

    @Transactional
    public boolean deleteGpsSourceConfig(UUID configId, UUID userId) {
        return gpsSourceRepository.deleteByUserIdAndConfigId(configId, userId) > 0;
    }

    @Transactional
    public boolean updateGpsConfigSource(UpdateGpsSourceConfigDto config, UUID userId) {
        Optional<GpsSourceConfigEntity> configOpt = gpsSourceRepository.findByConfigIdAndUserId(UUID.fromString(config.getId()), userId);
        if (configOpt.isEmpty()) {
            log.error("No config found for user {} and id {}", userId, config.getId());
            return false;
        }
        GpsSourceConfigEntity dbConfig = configOpt.get();
        if (dbConfig.getSourceType() == GpsSourceType.OWNTRACKS) {
            dbConfig.setUsername(config.getUsername());
            dbConfig.setPasswordHash(passwordUtils.hashPassword(config.getPassword()));
        }
        if (dbConfig.getSourceType() == GpsSourceType.OVERLAND) {
            dbConfig.setToken(config.getToken());
        }
        return true;
    }

    @Transactional
    public boolean updateGpsConfigSourceStatus(UUID configId, UUID userId, boolean newStatus) {
        Optional<GpsSourceConfigEntity> configOpt = gpsSourceRepository.findByConfigIdAndUserId(configId, userId);
        if (configOpt.isEmpty()) {
            log.error("No config found for user {} and id {}", userId, configId);
            return false;
        }
        GpsSourceConfigEntity dbConfig = configOpt.get();
        dbConfig.setActive(newStatus);
        return true;
    }

    @Override
    public Optional<GpsSourceConfigEntity> findByUsername(String username) {
        return gpsSourceRepository.findByUsername(username);
    }

    @Override
    public Optional<GpsSourceConfigEntity> findByToken(String token) {
        return gpsSourceRepository.findByToken(token);
    }

}
