package org.github.tess1o.geopulse.timelinelabels.service;

import org.github.tess1o.geopulse.timelinelabels.model.dto.CreateTimelineLabelDto;
import org.github.tess1o.geopulse.timelinelabels.model.dto.TimelineLabelDto;
import org.github.tess1o.geopulse.timelinelabels.model.dto.UpdateTimelineLabelDto;
import org.github.tess1o.geopulse.timelinelabels.model.entity.TimelineLabelEntity;
import org.github.tess1o.geopulse.timelinelabels.repository.TimelineLabelRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TimelineLabelServiceTest {

    @Mock
    TimelineLabelRepository repository;

    @Mock
    UserRepository userRepository;

    @Mock
    TripRepository tripRepository;

    TimelineLabelService service;

    UUID userId;
    UserEntity user;

    @BeforeEach
    void setUp() {
        service = new TimelineLabelService(repository, userRepository, tripRepository);
        userId = UUID.randomUUID();
        user = UserEntity.builder()
                .id(userId)
                .email("test@example.com")
                .build();
    }

    @Test
    void createTimelineLabelDefaultsShowAsPresetToTrueWhenOmitted() {
        when(userRepository.findById(userId)).thenReturn(user);

        CreateTimelineLabelDto dto = createDto(null);

        TimelineLabelDto created = service.createTimelineLabel(userId, dto);

        assertThat(created.getShowAsPreset()).isTrue();
    }

    @Test
    void createTimelineLabelAllowsHidingFromPresets() {
        when(userRepository.findById(userId)).thenReturn(user);

        CreateTimelineLabelDto dto = createDto(false);

        TimelineLabelDto created = service.createTimelineLabel(userId, dto);

        assertThat(created.getShowAsPreset()).isFalse();
    }

    @Test
    void updateTimelineLabelPreservesShowAsPresetWhenOmitted() {
        TimelineLabelEntity existing = existingEntity(false);
        when(repository.findByIdAndUserId(42L, userId)).thenReturn(Optional.of(existing));
        when(tripRepository.findByTimelineLabelIdAndUserId(42L, userId)).thenReturn(Optional.empty());

        UpdateTimelineLabelDto dto = updateDto(null);

        TimelineLabelDto updated = service.updateTimelineLabel(userId, 42L, dto);

        assertThat(updated.getShowAsPreset()).isFalse();
        assertThat(existing.getShowAsPreset()).isFalse();
    }

    @Test
    void updateTimelineLabelAppliesShowAsPresetWhenProvided() {
        TimelineLabelEntity existing = existingEntity(true);
        when(repository.findByIdAndUserId(42L, userId)).thenReturn(Optional.of(existing));
        when(tripRepository.findByTimelineLabelIdAndUserId(42L, userId)).thenReturn(Optional.empty());

        UpdateTimelineLabelDto dto = updateDto(false);

        TimelineLabelDto updated = service.updateTimelineLabel(userId, 42L, dto);

        assertThat(updated.getShowAsPreset()).isFalse();
        assertThat(existing.getShowAsPreset()).isFalse();
    }

    @Test
    void createTimelineLabelAllowsARangeEntirelyInTheFuture() {
        when(userRepository.findById(userId)).thenReturn(user);

        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant end = start.plus(14, ChronoUnit.DAYS);
        CreateTimelineLabelDto dto = createDto(null);
        dto.setStartTime(start);
        dto.setEndTime(end);

        TimelineLabelDto created = service.createTimelineLabel(userId, dto);

        assertThat(created.getStartTime()).isEqualTo(start);
        assertThat(created.getEndTime()).isEqualTo(end);
    }

    @Test
    void updateTimelineLabelAllowsARangeEntirelyInTheFuture() {
        TimelineLabelEntity existing = existingEntity(true);
        when(repository.findByIdAndUserId(42L, userId)).thenReturn(Optional.of(existing));
        when(tripRepository.findByTimelineLabelIdAndUserId(42L, userId)).thenReturn(Optional.empty());

        Instant start = Instant.now().plus(60, ChronoUnit.DAYS);
        Instant end = start.plus(7, ChronoUnit.DAYS);
        UpdateTimelineLabelDto dto = updateDto(null);
        dto.setStartTime(start);
        dto.setEndTime(end);

        TimelineLabelDto updated = service.updateTimelineLabel(userId, 42L, dto);

        assertThat(updated.getStartTime()).isEqualTo(start);
        assertThat(updated.getEndTime()).isEqualTo(end);
    }

    @Test
    void createTimelineLabelRejectsAnEndBeforeTheStart() {
        CreateTimelineLabelDto dto = createDto(null);
        dto.setStartTime(Instant.now());
        dto.setEndTime(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> service.createTimelineLabel(userId, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void createTimelineLabelRequiresAnEndTime() {
        CreateTimelineLabelDto dto = createDto(null);
        dto.setEndTime(null);

        assertThatThrownBy(() -> service.createTimelineLabel(userId, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End time is required");
    }

    private CreateTimelineLabelDto createDto(Boolean showAsPreset) {
        Instant start = Instant.now().minusSeconds(86_400);
        Instant end = Instant.now().minusSeconds(3_600);

        CreateTimelineLabelDto dto = new CreateTimelineLabelDto();
        dto.setName("Vacation");
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setColor("#FF6B6B");
        dto.setShowAsPreset(showAsPreset);
        return dto;
    }

    private UpdateTimelineLabelDto updateDto(Boolean showAsPreset) {
        Instant start = Instant.now().minusSeconds(172_800);
        Instant end = Instant.now().minusSeconds(86_400);

        UpdateTimelineLabelDto dto = new UpdateTimelineLabelDto();
        dto.setName("Updated vacation");
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setColor("#4ECDC4");
        dto.setShowAsPreset(showAsPreset);
        return dto;
    }

    private TimelineLabelEntity existingEntity(Boolean showAsPreset) {
        Instant start = Instant.now().minusSeconds(86_400);
        Instant end = Instant.now().minusSeconds(3_600);

        return TimelineLabelEntity.builder()
                .id(42L)
                .user(user)
                .name("Vacation")
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
