package org.github.tess1o.geopulse.gpssource.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gpssource.model.ConnectedGpsSourceDTO;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.auth.dto.CreateApiTokenResponse;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.model.UserApiTokenEntity;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksEncryptionKeyUtil;
import org.github.tess1o.geopulse.gpssource.mapper.GpsSourceConfigMapper;
import org.github.tess1o.geopulse.gpssource.model.CreateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceIngestStatusDTO;
import org.github.tess1o.geopulse.gpssource.model.UpdateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GpsSourceService implements GpsSourceConfigProvider {

    private final GpsSourceRepository gpsSourceRepository;
    private final GpsSourceConfigMapper gpsSourceMapper;
    private final SecurePasswordUtils passwordUtils;
    private final EntityManager em;
    private final AIEncryptionService encryptionService;
    private final GpsSourceIngestStatusService ingestStatusService;
    private final ApiTokenService apiTokenService;

    /** Scopes of tokens created together with a Home Assistant source: send locations and read data. */
    static final List<String> HOME_ASSISTANT_TOKEN_SCOPES =
            List.of(ApiTokenScope.READ.value(), ApiTokenScope.GPS_INGEST.value());

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
                            EntityManager em,
                            AIEncryptionService encryptionService,
                            GpsSourceIngestStatusService ingestStatusService,
                            ApiTokenService apiTokenService) {
        this.gpsSourceRepository = gpsSourceRepository;
        this.gpsSourceMapper = gpsSourceMapper;
        this.passwordUtils = passwordUtils;
        this.em = em;
        this.encryptionService = encryptionService;
        this.ingestStatusService = ingestStatusService;
        this.apiTokenService = apiTokenService;
    }


    @Transactional
    public List<GpsSourceConfigDTO> findGpsSourceConfigs(UUID userId) {
        List<GpsSourceConfigEntity> configs = gpsSourceRepository.findByUserId(userId);
        Map<UUID, GpsSourceIngestStatusDTO> statuses = ingestStatusService.findBySourceConfigIds(
                configs.stream().map(GpsSourceConfigEntity::getId).toList());
        return configs.stream().map(config -> {
            GpsSourceConfigDTO dto = gpsSourceMapper.toDTO(config);
            dto.setIngestStatus(statuses.get(config.getId()));
            return dto;
        }).toList();
    }

    @Transactional
    public GpsSourceConfigDTO addGpsSourceConfig(CreateGpsSourceConfigDto newConfig) {
        return addGpsSourceConfig(newConfig, null);
    }

    /**
     * Creates a source. A Home Assistant source with {@code apiTokenId} or {@code newApiToken} is connected to a
     * personal API token instead of having a source token; a new token is
     * created in the same transaction and its secret returned once.
     */
    @Transactional
    public GpsSourceConfigDTO addGpsSourceConfig(CreateGpsSourceConfigDto newConfig, String ipAddress) {
        if (isApiTokenSource(newConfig)) {
            return addApiTokenSource(newConfig, ipAddress);
        }
        normalizeTraccarConfig(newConfig);
        validatePayloadEncryptionSecret(newConfig.getType(), newConfig.getPayloadEncryptionSecret());
        validateUniqueness(newConfig);
        UserEntity user = em.getReference(UserEntity.class, newConfig.getUserId());
        GpsSourceConfigEntity gpsSourceConfigEntity = gpsSourceMapper.toEntity(newConfig, user);
        gpsSourceRepository.persist(gpsSourceConfigEntity);
        return gpsSourceMapper.toDTO(gpsSourceConfigEntity);
    }

    private static boolean isApiTokenSource(CreateGpsSourceConfigDto config) {
        return config.getApiTokenId() != null || config.getNewApiToken() != null;
    }

    private GpsSourceConfigDTO addApiTokenSource(CreateGpsSourceConfigDto newConfig, String ipAddress) {
        if (newConfig.getType() != GpsSourceType.HOME_ASSISTANT) {
            throw new IllegalArgumentException("Only Home Assistant sources can use an API token");
        }
        if (newConfig.getApiTokenId() != null && newConfig.getNewApiToken() != null) {
            throw new IllegalArgumentException("Choose either an existing API token or a new one");
        }

        UUID userId = newConfig.getUserId();
        String secret = null;
        UserApiTokenEntity apiToken;
        if (newConfig.getNewApiToken() != null) {
            String name = hasText(newConfig.getNewApiToken().getName())
                    ? newConfig.getNewApiToken().getName() : "Home Assistant";
            CreateApiTokenResponse created = apiTokenService.createToken(userId, name,
                    newConfig.getNewApiToken().getExpiresAt(), HOME_ASSISTANT_TOKEN_SCOPES, ipAddress);
            secret = created.getToken();
            apiToken = apiTokenService.requireOwnedActiveToken(userId, created.getApiToken().getId());
        } else {
            apiToken = apiTokenService.requireOwnedActiveToken(userId, newConfig.getApiTokenId());
            Set<ApiTokenScope> scopes = apiToken.getScopeSet();
            if (!scopes.contains(ApiTokenScope.GPS_INGEST) && !scopes.contains(ApiTokenScope.ALL)) {
                throw new IllegalArgumentException("This API token cannot send locations; it needs the gps:ingest scope");
            }
            if (gpsSourceRepository.findByApiTokenIdAndSourceType(apiToken.getId(), GpsSourceType.HOME_ASSISTANT).isPresent()) {
                throw new IllegalArgumentException("This API token is already connected to a Home Assistant source");
            }
        }

        newConfig.setToken(null);
        newConfig.setConnectionType(GpsSourceConfigEntity.ConnectionType.HTTP);
        GpsSourceConfigEntity entity = gpsSourceMapper.toEntity(newConfig, em.getReference(UserEntity.class, userId));
        entity.setApiToken(apiToken);
        gpsSourceRepository.persist(entity);

        GpsSourceConfigDTO dto = gpsSourceMapper.toDTO(entity);
        dto.setApiTokenSecret(secret);
        return dto;
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
            validateTraccarTokenDeviceUniqueness(newConfig.getUserId(), newConfig.getToken(), newConfig.getDeviceId(), null);
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
        return configs.stream().noneMatch(config -> Objects.equals(config.getToken(), newConfig.getToken()));
    }

    @Transactional
    public boolean deleteGpsSourceConfig(UUID configId, UUID userId) {
        return gpsSourceRepository.deleteByUserIdAndConfigId(configId, userId) > 0;
    }

    /**
     * Deletes a source and, when asked, revokes the API token it is connected to; otherwise points sent with that
     * token would create the source again.
     */
    @Transactional
    public boolean deleteGpsSourceConfig(UUID configId, UUID userId, boolean revokeApiToken, String ipAddress) {
        Optional<GpsSourceConfigEntity> source = gpsSourceRepository.findByConfigIdAndUserId(configId, userId);
        if (source.isEmpty()) {
            return false;
        }
        UserApiTokenEntity apiToken = source.get().getApiToken();
        if (revokeApiToken && apiToken != null && !apiToken.isRevoked()) {
            apiTokenService.revokeOwnedToken(userId, apiToken.getId(), ipAddress);
        }
        return deleteGpsSourceConfig(configId, userId);
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
        if (dbConfig.getSourceType() == GpsSourceType.OWNTRACKS) {
            if (config.isClearPayloadEncryptionSecret()) {
                dbConfig.setPayloadEncryptionSecretEncrypted(null);
                dbConfig.setPayloadEncryptionSecretKeyId(null);
            }
            if (hasText(config.getPayloadEncryptionSecret())) {
                validatePayloadEncryptionSecret(GpsSourceType.OWNTRACKS, config.getPayloadEncryptionSecret());
                dbConfig.setPayloadEncryptionSecretEncrypted(encryptionService.encrypt(config.getPayloadEncryptionSecret()));
                dbConfig.setPayloadEncryptionSecretKeyId(encryptionService.getCurrentKeyId());
            }
        }
        if (dbConfig.getSourceType() == GpsSourceType.OVERLAND ||
                dbConfig.getSourceType() == GpsSourceType.DAWARICH ||
                dbConfig.getSourceType() == GpsSourceType.HOME_ASSISTANT) {
            // Only update token if a new one is provided; API-token sources never get their own token
            if (hasText(config.getToken()) && dbConfig.getApiToken() == null) {
                dbConfig.setToken(config.getToken());
            }
        }

        if (dbConfig.getSourceType() == GpsSourceType.TRACCAR) {
            String nextToken = hasText(config.getToken()) ? config.getToken() : dbConfig.getToken();
            String nextDeviceId = config.getDeviceId() == null
                    ? dbConfig.getDeviceId()
                    : normalizeTraccarDeviceId(config.getDeviceId());

            validateTraccarTokenDeviceUniqueness(userId, nextToken, nextDeviceId, dbConfig.getId());

            if (hasText(config.getToken())) {
                dbConfig.setToken(config.getToken());
            }
            if (config.getDeviceId() != null) {
                dbConfig.setDeviceId(nextDeviceId);
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

    /** The source connected to the user's API token, with its ingest status but no credentials. */
    @Transactional
    public Optional<ConnectedGpsSourceDTO> findConnectedSource(UUID userId, UUID apiTokenId) {
        return gpsSourceRepository.findByApiTokenIdAndSourceType(apiTokenId, GpsSourceType.HOME_ASSISTANT)
                .filter(source -> source.getUser().getId().equals(userId))
                .map(source -> new ConnectedGpsSourceDTO(source.getId(), source.getSourceType().name(), source.isActive(),
                        ingestStatusService.findBySourceConfigIds(List.of(source.getId())).get(source.getId())));
    }

    /**
     * The source connected to this personal API token: points sent with the token are stored under it. Sources are only
     * connected in Location Sources (or through {@code POST /gps/sources} with an API token), never implicitly.
     */
    @Transactional
    public Optional<GpsSourceConfigEntity> findApiTokenSource(UserApiTokenEntity apiToken, GpsSourceType sourceType) {
        return gpsSourceRepository.findByApiTokenIdAndSourceType(apiToken.getId(), sourceType);
    }

    public List<GpsSourceConfigEntity> findAllActiveByTokenAndSourceType(String token, GpsSourceType sourceType) {
        return gpsSourceRepository.findAllActiveByTokenAndSourceType(token, sourceType);
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

    private void normalizeTraccarConfig(CreateGpsSourceConfigDto newConfig) {
        if (newConfig.getType() == GpsSourceType.TRACCAR) {
            newConfig.setDeviceId(normalizeTraccarDeviceId(newConfig.getDeviceId()));
        }
    }

    private void validateTraccarTokenDeviceUniqueness(UUID userId, String token, String deviceId, UUID excludedConfigId) {
        List<GpsSourceConfigEntity> existing = gpsSourceRepository.findByUserIdSourceTypeAndToken(
                userId,
                GpsSourceType.TRACCAR,
                token
        );

        String normalizedDeviceId = normalizeTraccarDeviceId(deviceId);
        boolean exists = existing.stream()
                .filter(config -> excludedConfigId == null || !config.getId().equals(excludedConfigId))
                .map(config -> normalizeTraccarDeviceId(config.getDeviceId()))
                .anyMatch(existingDeviceId -> {
                    if (normalizedDeviceId == null) {
                        return existingDeviceId == null;
                    }
                    return normalizedDeviceId.equals(existingDeviceId);
                });

        if (exists) {
            if (normalizedDeviceId == null) {
                throw new IllegalArgumentException("Traccar wildcard source is already configured for this token");
            }
            throw new IllegalArgumentException("Traccar device ID is already configured for this token");
        }
    }

    private String normalizeTraccarDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        String trimmed = deviceId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void validatePayloadEncryptionSecret(GpsSourceType sourceType, String secret) {
        if (sourceType == GpsSourceType.OWNTRACKS && hasText(secret)) {
            OwnTracksEncryptionKeyUtil.toSecretBoxKey(secret);
        }
    }

}
