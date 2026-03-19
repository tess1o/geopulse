package org.github.tess1o.geopulse.geofencing.service;

import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventDto;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GeofenceEventServiceTest {

    @Mock
    GeofenceEventRepository eventRepository;

    private GeofenceEventService service;

    @BeforeEach
    void setUp() {
        service = new GeofenceEventService(eventRepository);
    }

    @Test
    void shouldMarkEventSeen() {
        UUID ownerId = UUID.randomUUID();
        GeofenceEventEntity entity = createEventEntity(ownerId, null);
        when(eventRepository.findByIdAndOwner(10L, ownerId)).thenReturn(Optional.of(entity));

        GeofenceEventDto result = service.markSeen(ownerId, 10L);

        assertThat(result.getSeen()).isTrue();
        assertThat(result.getSeenAt()).isNotNull();
        assertThat(entity.getSeenAt()).isNotNull();
    }

    @Test
    void shouldThrowWhenMarkSeenForMissingEvent() {
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndOwner(10L, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markSeen(ownerId, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void shouldReturnUnreadCount() {
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.countUnreadByOwner(ownerId)).thenReturn(7L);

        long count = service.countUnread(ownerId);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void shouldMarkAllSeen() {
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.markAllSeenByOwner(eq(ownerId), any(Instant.class))).thenReturn(3L);

        long updated = service.markAllSeen(ownerId);

        assertThat(updated).isEqualTo(3L);
        verify(eventRepository).markAllSeenByOwner(eq(ownerId), any(Instant.class));
    }

    @Test
    void shouldMapSeenFlagInDto() {
        UUID ownerId = UUID.randomUUID();
        GeofenceEventEntity unseen = createEventEntity(ownerId, null);
        GeofenceEventEntity seen = createEventEntity(ownerId, Instant.now());

        GeofenceEventDto unseenDto = service.toDto(unseen);
        GeofenceEventDto seenDto = service.toDto(seen);

        assertThat(unseenDto.getSeen()).isFalse();
        assertThat(unseenDto.getSeenAt()).isNull();
        assertThat(seenDto.getSeen()).isTrue();
        assertThat(seenDto.getSeenAt()).isNotNull();
    }

    private GeofenceEventEntity createEventEntity(UUID subjectId, Instant seenAt) {
        UserEntity subject = new UserEntity();
        subject.setId(subjectId);
        subject.setEmail("friend@example.com");
        subject.setFullName("Friend");

        GeofenceRuleEntity rule = new GeofenceRuleEntity();
        rule.setId(5L);
        rule.setName("Home");

        return GeofenceEventEntity.builder()
                .id(10L)
                .subjectUser(subject)
                .rule(rule)
                .eventType(GeofenceEventType.ENTER)
                .occurredAt(Instant.now())
                .message("Hello")
                .seenAt(seenAt)
                .build();
    }
}
