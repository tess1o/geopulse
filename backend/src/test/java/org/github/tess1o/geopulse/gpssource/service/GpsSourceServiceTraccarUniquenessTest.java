package org.github.tess1o.geopulse.gpssource.service;

import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.gpssource.mapper.GpsSourceConfigMapper;
import org.github.tess1o.geopulse.gpssource.model.CreateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.model.UpdateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GpsSourceServiceTraccarUniquenessTest {

    @Mock
    private GpsSourceRepository gpsSourceRepository;

    @Mock
    private GpsSourceConfigMapper gpsSourceMapper;

    @Mock
    private SecurePasswordUtils passwordUtils;

    @Mock
    private EntityManager em;

    private GpsSourceService service;

    @BeforeEach
    void setUp() {
        service = new GpsSourceService(gpsSourceRepository, gpsSourceMapper, passwordUtils, em);
    }

    @Test
    void addTraccar_allowsSameTokenWhenDeviceIdDiffers() {
        UUID userId = UUID.randomUUID();
        CreateGpsSourceConfigDto dto = new CreateGpsSourceConfigDto(
                GpsSourceType.TRACCAR,
                null,
                null,
                "shared-token",
                "  Phone-A  ",
                userId,
                GpsSourceConfigEntity.ConnectionType.HTTP,
                false,
                100,
                250,
                false,
                null
        );

        GpsSourceConfigEntity existing = traccarConfig(UUID.randomUUID(), userId, "shared-token", "phone-b");
        when(gpsSourceRepository.findByUserIdSourceTypeAndToken(userId, GpsSourceType.TRACCAR, "shared-token"))
                .thenReturn(List.of(existing));

        UserEntity user = new UserEntity();
        user.setId(userId);
        when(em.getReference(UserEntity.class, userId)).thenReturn(user);

        GpsSourceConfigEntity created = traccarConfig(UUID.randomUUID(), userId, "shared-token", "phone-a");
        when(gpsSourceMapper.toEntity(any(CreateGpsSourceConfigDto.class), eq(user))).thenReturn(created);
        when(gpsSourceMapper.toDTO(created)).thenReturn(new GpsSourceConfigDTO());

        service.addGpsSourceConfig(dto);

        ArgumentCaptor<CreateGpsSourceConfigDto> dtoCaptor = ArgumentCaptor.forClass(CreateGpsSourceConfigDto.class);
        verify(gpsSourceMapper).toEntity(dtoCaptor.capture(), eq(user));
        assertEquals("phone-a", dtoCaptor.getValue().getDeviceId());
        verify(gpsSourceRepository).persist(created);
    }

    @Test
    void addTraccar_rejectsSameTokenAndSameDeviceId() {
        UUID userId = UUID.randomUUID();
        CreateGpsSourceConfigDto dto = new CreateGpsSourceConfigDto(
                GpsSourceType.TRACCAR,
                null,
                null,
                "shared-token",
                "Phone-A",
                userId,
                GpsSourceConfigEntity.ConnectionType.HTTP,
                false,
                100,
                250,
                false,
                null
        );

        GpsSourceConfigEntity existing = traccarConfig(UUID.randomUUID(), userId, "shared-token", "phone-a");
        when(gpsSourceRepository.findByUserIdSourceTypeAndToken(userId, GpsSourceType.TRACCAR, "shared-token"))
                .thenReturn(List.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.addGpsSourceConfig(dto));
        assertTrue(ex.getMessage().contains("device ID"));
        verify(gpsSourceMapper, never()).toEntity(any(CreateGpsSourceConfigDto.class), any(UserEntity.class));
    }

    @Test
    void addTraccar_rejectsDuplicateWildcardForSameToken() {
        UUID userId = UUID.randomUUID();
        CreateGpsSourceConfigDto dto = new CreateGpsSourceConfigDto(
                GpsSourceType.TRACCAR,
                null,
                null,
                "shared-token",
                "   ",
                userId,
                GpsSourceConfigEntity.ConnectionType.HTTP,
                false,
                100,
                250,
                false,
                null
        );

        GpsSourceConfigEntity existing = traccarConfig(UUID.randomUUID(), userId, "shared-token", null);
        when(gpsSourceRepository.findByUserIdSourceTypeAndToken(userId, GpsSourceType.TRACCAR, "shared-token"))
                .thenReturn(List.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.addGpsSourceConfig(dto));
        assertTrue(ex.getMessage().contains("wildcard"));
    }

    @Test
    void updateTraccar_rejectsSameTokenAndConflictingDeviceId() {
        UUID userId = UUID.randomUUID();
        UUID currentConfigId = UUID.randomUUID();
        UUID conflictingConfigId = UUID.randomUUID();

        GpsSourceConfigEntity current = traccarConfig(currentConfigId, userId, "shared-token", "phone-a");
        GpsSourceConfigEntity conflicting = traccarConfig(conflictingConfigId, userId, "shared-token", "phone-b");

        when(gpsSourceRepository.findByConfigIdAndUserId(currentConfigId, userId)).thenReturn(Optional.of(current));
        when(gpsSourceRepository.findByUserIdSourceTypeAndToken(userId, GpsSourceType.TRACCAR, "shared-token"))
                .thenReturn(List.of(current, conflicting));

        UpdateGpsSourceConfigDto dto = new UpdateGpsSourceConfigDto(
                currentConfigId.toString(),
                "TRACCAR",
                null,
                null,
                null,
                "Phone-B",
                userId.toString(),
                null,
                false,
                null,
                null,
                false,
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.updateGpsConfigSource(dto, userId));
        assertTrue(ex.getMessage().contains("device ID"));
    }

    @Test
    void updateTraccar_allowsCurrentConfigAndNormalizesDeviceId() {
        UUID userId = UUID.randomUUID();
        UUID currentConfigId = UUID.randomUUID();

        GpsSourceConfigEntity current = traccarConfig(currentConfigId, userId, "shared-token", "phone-a");
        when(gpsSourceRepository.findByConfigIdAndUserId(currentConfigId, userId)).thenReturn(Optional.of(current));
        when(gpsSourceRepository.findByUserIdSourceTypeAndToken(userId, GpsSourceType.TRACCAR, "shared-token"))
                .thenReturn(List.of(current));

        UpdateGpsSourceConfigDto dto = new UpdateGpsSourceConfigDto(
                currentConfigId.toString(),
                "TRACCAR",
                null,
                null,
                null,
                "  PHONE-A ",
                userId.toString(),
                null,
                false,
                null,
                null,
                false,
                null
        );

        boolean updated = service.updateGpsConfigSource(dto, userId);

        assertTrue(updated);
        assertEquals("phone-a", current.getDeviceId());
    }

    private GpsSourceConfigEntity traccarConfig(UUID configId, UUID userId, String token, String deviceId) {
        UserEntity user = new UserEntity();
        user.setId(userId);

        GpsSourceConfigEntity config = new GpsSourceConfigEntity();
        config.setId(configId);
        config.setUser(user);
        config.setSourceType(GpsSourceType.TRACCAR);
        config.setToken(token);
        config.setDeviceId(deviceId);
        config.setActive(true);
        return config;
    }
}
