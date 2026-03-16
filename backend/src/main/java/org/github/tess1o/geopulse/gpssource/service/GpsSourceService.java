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
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GpsSourceService implements GpsSourceConfigProvider {

    private final GpsSourceRepository gpsSourceRepository;
    private final GpsSourceConfigMapper gpsSourceMapper;
    private final SecurePasswordUtils passwordUtils;
    private final EntityManager em;

    @Getter
    @ConfigProperty(name = "geopulse.gps.filter.inaccurate-data.enabled", defaultValue = "false")
    boolean defaultFilterInaccurateDataEnabled;

    @Getter
    @ConfigProperty(name = "geopulse.gps.max-allowed-accuracy", defaultValue = "100")
    int defaultMaxAllowedAccuracy;

    @Getter
    @ConfigProperty(name = "geopulse.gps.max-allowed-speed", defaultValue = "250")
    int defaultMaxAllowedSpeed;

    @Getter
    @ConfigProperty(name = "geopulse.gps.duplicate-detection.enabled", defaultValue = "false")
    boolean defaultDuplicateDetectionEnabled;

    @Getter
    @ConfigProperty(name = "geopulse.gps.duplicate-detection.threshold-minutes", defaultValue = "2")
    int defaultDuplicateDetectionThresholdMinutes;

    @Inject
    public GpsSourceService(GpsSourceRepository gpsSourceRepository,
                            GpsSourceConfigMapper gpsSourceMapper,
                            SecurePasswordUtils passwordUtils,
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
        if (newConfig.getType() == GpsSourceType.OWNTRACKS || newConfig.getType() == GpsSourceType.GPSLOGGER || newConfig.getType() == GpsSourceType.COLOTA) {
            boolean isUnique = isOwnTrackSourceUnique(newConfig);
            if (!isUnique) {
                String msg = switch (newConfig.getType()) {
                    case GPSLOGGER -> "GPSLogger username is already used";
                    case COLOTA -> "Colota username is already used";
                    default -> "Owntrack username is already used";
                };
                throw new IllegalArgumentException(msg);
            }
        } else if (newConfig.getType() == GpsSourceType.OVERLAND) {
            boolean isUnique = isTokenSourceUnique(newConfig, GpsSourceType.OVERLAND);
            if (!isUnique) {
                throw new IllegalArgumentException("Overland token is already used");
            }
        } else if (newConfig.getType() == GpsSourceType.TRACCAR) {
            boolean isUnique = isTokenSourceUnique(newConfig, GpsSourceType.TRACCAR);
            if (!isUnique) {
                throw new IllegalArgumentException("Traccar token is already used");
            }
        } else if (newConfig.getType() == GpsSourceType.HOME_ASSISTANT) {
            boolean isUnique = isTokenSourceUnique(newConfig, GpsSourceType.HOME_ASSISTANT);
            if (!isUnique) {
                throw new IllegalArgumentException("Home assistant username is already used");
            }
        }
    }

    private boolean isOwnTrackSourceUnique(CreateGpsSourceConfigDto newConfig) {
        List<GpsSourceConfigEntity> configs = gpsSourceRepository.findByUserIdAndSourceType(newConfig.getUserId(), newConfig.getType());
        if (configs == null || configs.isEmpty()) {
            return true;
        }
        return configs.stream().noneMatch(config -> config.getUsername().equals(newConfig.getUsername()));
    }

    private boolean isTokenSourceUnique(CreateGpsSourceConfigDto newConfig, GpsSourceType sourceType) {
        List<GpsSourceConfigEntity> configs = gpsSourceRepository.findByUserIdAndSourceType(newConfig.getUserId(), sourceType);
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
        if (dbConfig.getSourceType() == GpsSourceType.OWNTRACKS || dbConfig.getSourceType() == GpsSourceType.GPSLOGGER || dbConfig.getSourceType() == GpsSourceType.COLOTA) {
            dbConfig.setUsername(config.getUsername());
            // Only update password if a new one is provided
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                dbConfig.setPasswordHash(passwordUtils.hashPassword(config.getPassword()));
            }
        }
        if (dbConfig.getSourceType() == GpsSourceType.OVERLAND ||
                dbConfig.getSourceType() == GpsSourceType.TRACCAR ||
                dbConfig.getSourceType() == GpsSourceType.DAWARICH ||
                dbConfig.getSourceType() == GpsSourceType.HOME_ASSISTANT) {
            // Only update token if a new one is provided
            if (config.getToken() != null && !config.getToken().isEmpty()) {
                dbConfig.setToken(config.getToken());
            }
        }
        if (config.getConnectionType() != null) {
            dbConfig.setConnectionType(config.getConnectionType());
        }
        // Update filtering settings
        dbConfig.setFilterInaccurateData(config.isFilterInaccurateData());
        dbConfig.setMaxAllowedAccuracy(config.getMaxAllowedAccuracy());
        dbConfig.setMaxAllowedSpeed(config.getMaxAllowedSpeed());
        // Update duplicate detection settings
        dbConfig.setEnableDuplicateDetection(config.isEnableDuplicateDetection());
        dbConfig.setDuplicateDetectionThresholdMinutes(config.getDuplicateDetectionThresholdMinutes());
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
    public Optional<GpsSourceConfigEntity> findByUsernameAndSourceType(String username, GpsSourceType sourceType) {
        return gpsSourceRepository.findByUsernameAndSourceType(username, sourceType);
    }

    @Override
    public Optional<GpsSourceConfigEntity> findByToken(String token) {
        return gpsSourceRepository.findByToken(token);
    }

    @Override
    public Optional<GpsSourceConfigEntity> findByTokenAndSourceType(String token, GpsSourceType sourceType) {
        return gpsSourceRepository.findByTokenAndSourceType(token, sourceType);
    }

    @Override
    public Optional<GpsSourceConfigEntity> findByUsernameAndConnectionType(String username, GpsSourceConfigEntity.ConnectionType connectionType) {
        return gpsSourceRepository.findByUsernameAndConnectionType(username, connectionType);
    }

}
