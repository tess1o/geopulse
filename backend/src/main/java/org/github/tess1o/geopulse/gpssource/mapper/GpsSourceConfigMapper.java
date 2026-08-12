package org.github.tess1o.geopulse.gpssource.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.gpssource.model.CreateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigDTO;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

@ApplicationScoped
public class GpsSourceConfigMapper {

    private final SecurePasswordUtils securePasswordUtils;
    private final AIEncryptionService encryptionService;

    public GpsSourceConfigMapper(SecurePasswordUtils securePasswordUtils,
                                 AIEncryptionService encryptionService) {
        this.securePasswordUtils = securePasswordUtils;
        this.encryptionService = encryptionService;
    }

    public GpsSourceConfigDTO toDTO(GpsSourceConfigEntity config) {
        return GpsSourceConfigDTO.builder()
                .id(config.getId())
                .userId(config.getUser().getId())
                .username(config.getUsername())
                .token(config.getToken())
                .deviceId(config.getDeviceId())
                .hasPayloadEncryptionSecret(hasText(config.getPayloadEncryptionSecretEncrypted()))
                .type(config.getSourceType().name())
                .active(config.isActive())
                .connectionType(config.getConnectionType())
                .filterInaccurateData(config.isFilterInaccurateData())
                .maxAllowedAccuracy(config.getMaxAllowedAccuracy())
                .maxAllowedSpeed(config.getMaxAllowedSpeed())
                .enableDuplicateDetection(config.isEnableDuplicateDetection())
                .duplicateDetectionThresholdMinutes(config.getDuplicateDetectionThresholdMinutes())
                .build();
    }

    public GpsSourceConfigEntity toEntity(CreateGpsSourceConfigDto newConfig, UserEntity user) {
        String hashedPassword = newConfig.getPassword() == null || newConfig.getPassword().isEmpty()
                ? "" : securePasswordUtils.hashPassword(newConfig.getPassword());
        String encryptedPayloadSecret = null;
        String encryptedPayloadSecretKeyId = null;
        if (newConfig.getType() == GpsSourceType.OWNTRACKS && hasText(newConfig.getPayloadEncryptionSecret())) {
            encryptedPayloadSecret = encryptionService.encrypt(newConfig.getPayloadEncryptionSecret());
            encryptedPayloadSecretKeyId = encryptionService.getCurrentKeyId();
        }
        return GpsSourceConfigEntity.builder()
                .user(user)
                .active(true)
                .token(newConfig.getToken())
                .deviceId(newConfig.getDeviceId())
                .payloadEncryptionSecretEncrypted(encryptedPayloadSecret)
                .payloadEncryptionSecretKeyId(encryptedPayloadSecretKeyId)
                .username(newConfig.getUsername())
                .passwordHash(hashedPassword)
                .sourceType(newConfig.getType())
                .connectionType(newConfig.getConnectionType())
                // Handle null Boolean by defaulting to false
                .filterInaccurateData(newConfig.getFilterInaccurateData() != null ? newConfig.getFilterInaccurateData() : false)
                .maxAllowedAccuracy(newConfig.getMaxAllowedAccuracy())
                .maxAllowedSpeed(newConfig.getMaxAllowedSpeed())
                .enableDuplicateDetection(newConfig.getEnableDuplicateDetection() != null ? newConfig.getEnableDuplicateDetection() : false)
                .duplicateDetectionThresholdMinutes(newConfig.getDuplicateDetectionThresholdMinutes())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
