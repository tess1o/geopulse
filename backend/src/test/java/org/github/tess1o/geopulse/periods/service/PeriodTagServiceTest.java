package org.github.tess1o.geopulse.periods.service;

import org.github.tess1o.geopulse.periods.model.dto.CreatePeriodTagDto;
import org.github.tess1o.geopulse.periods.model.dto.PeriodTagDto;
import org.github.tess1o.geopulse.periods.model.dto.UpdatePeriodTagDto;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;
import org.github.tess1o.geopulse.periods.repository.PeriodTagRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class PeriodTagServiceTest {

    @Mock
    PeriodTagRepository repository;

    @Mock
    UserRepository userRepository;

    @Mock
    TripRepository tripRepository;

    PeriodTagService service;

    UUID userId;
    UserEntity user;

    @BeforeEach
    void setUp() {
        service = new PeriodTagService(repository, userRepository, tripRepository);
        userId = UUID.randomUUID();
        user = UserEntity.builder()
                .id(userId)
                .email("test@example.com")
                .build();
    }

    @Test
    void createPeriodTagDefaultsShowAsPresetToTrueWhenOmitted() {
        when(userRepository.findById(userId)).thenReturn(user);

        CreatePeriodTagDto dto = createDto(null);

        PeriodTagDto created = service.createPeriodTag(userId, dto);

        assertThat(created.getShowAsPreset()).isTrue();
    }

    @Test
    void createPeriodTagAllowsHidingFromPresets() {
        when(userRepository.findById(userId)).thenReturn(user);

        CreatePeriodTagDto dto = createDto(false);

        PeriodTagDto created = service.createPeriodTag(userId, dto);

        assertThat(created.getShowAsPreset()).isFalse();
    }

    @Test
    void updatePeriodTagPreservesShowAsPresetWhenOmitted() {
        PeriodTagEntity existing = existingEntity(false);
        when(repository.findByIdAndUserId(42L, userId)).thenReturn(Optional.of(existing));
        when(tripRepository.findByPeriodTagIdAndUserId(42L, userId)).thenReturn(Optional.empty());

        UpdatePeriodTagDto dto = updateDto(null);

        PeriodTagDto updated = service.updatePeriodTag(userId, 42L, dto);

        assertThat(updated.getShowAsPreset()).isFalse();
        assertThat(existing.getShowAsPreset()).isFalse();
    }

    @Test
    void updatePeriodTagAppliesShowAsPresetWhenProvided() {
        PeriodTagEntity existing = existingEntity(true);
        when(repository.findByIdAndUserId(42L, userId)).thenReturn(Optional.of(existing));
        when(tripRepository.findByPeriodTagIdAndUserId(42L, userId)).thenReturn(Optional.empty());

        UpdatePeriodTagDto dto = updateDto(false);

        PeriodTagDto updated = service.updatePeriodTag(userId, 42L, dto);

        assertThat(updated.getShowAsPreset()).isFalse();
        assertThat(existing.getShowAsPreset()).isFalse();
    }

    private CreatePeriodTagDto createDto(Boolean showAsPreset) {
        Instant start = Instant.now().minusSeconds(86_400);
        Instant end = Instant.now().minusSeconds(3_600);

        CreatePeriodTagDto dto = new CreatePeriodTagDto();
        dto.setTagName("Vacation");
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setColor("#FF6B6B");
        dto.setShowAsPreset(showAsPreset);
        return dto;
    }

    private UpdatePeriodTagDto updateDto(Boolean showAsPreset) {
        Instant start = Instant.now().minusSeconds(172_800);
        Instant end = Instant.now().minusSeconds(86_400);

        UpdatePeriodTagDto dto = new UpdatePeriodTagDto();
        dto.setTagName("Updated vacation");
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setColor("#4ECDC4");
        dto.setShowAsPreset(showAsPreset);
        return dto;
    }

    private PeriodTagEntity existingEntity(Boolean showAsPreset) {
        Instant start = Instant.now().minusSeconds(86_400);
        Instant end = Instant.now().minusSeconds(3_600);

        return PeriodTagEntity.builder()
                .id(42L)
                .user(user)
                .tagName("Vacation")
                .startTime(start)
                .endTime(end)
                .source("manual")
                .isActive(false)
                .color("#FF6B6B")
                .showAsPreset(showAsPreset)
                .createdAt(start)
                .updatedAt(start)
                .build();
    }
}
