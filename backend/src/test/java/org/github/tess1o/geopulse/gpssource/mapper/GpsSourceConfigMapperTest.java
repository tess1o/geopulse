package org.github.tess1o.geopulse.gpssource.mapper;

import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.gpssource.model.CreateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class GpsSourceConfigMapperTest {

    private final SecurePasswordUtils passwordUtils = mock(SecurePasswordUtils.class);
    private final AIEncryptionService encryptionService = mock(AIEncryptionService.class);
    private final GpsSourceConfigMapper mapper = new GpsSourceConfigMapper(passwordUtils, encryptionService);

    @Test
    void mapsPayloadEncryptionSecretToWriteOnlyEncryptedStorage() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        when(passwordUtils.hashPassword("password")).thenReturn("password-hash");
        when(encryptionService.encrypt("payload-secret")).thenReturn("encrypted-payload-secret");
        when(encryptionService.getCurrentKeyId()).thenReturn("v1");

        CreateGpsSourceConfigDto dto = new CreateGpsSourceConfigDto(
                GpsSourceType.OWNTRACKS,
                "owntracks-user",
                "password",
                null,
                null,
                "payload-secret",
                user.getId(),
                GpsSourceConfigEntity.ConnectionType.HTTP,
                false,
                null,
                null,
                false,
                null
        );

        GpsSourceConfigEntity entity = mapper.toEntity(dto, user);
        GpsSourceConfigDTO response = mapper.toDTO(entity);

        assertEquals("encrypted-payload-secret", entity.getPayloadEncryptionSecretEncrypted());
        assertEquals("v1", entity.getPayloadEncryptionSecretKeyId());
        assertTrue(response.isHasPayloadEncryptionSecret());
    }

    @Test
    void omitsPayloadEncryptionStorageForNonOwnTracksSources() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());

        CreateGpsSourceConfigDto dto = new CreateGpsSourceConfigDto(
                GpsSourceType.OVERLAND,
                null,
                null,
                "token",
                null,
                "payload-secret",
                user.getId(),
                GpsSourceConfigEntity.ConnectionType.HTTP,
                false,
                null,
                null,
                false,
                null
        );

        GpsSourceConfigEntity entity = mapper.toEntity(dto, user);
        GpsSourceConfigDTO response = mapper.toDTO(entity);

        assertNull(entity.getPayloadEncryptionSecretEncrypted());
        assertNull(entity.getPayloadEncryptionSecretKeyId());
        assertFalse(response.isHasPayloadEncryptionSecret());
    }
}
