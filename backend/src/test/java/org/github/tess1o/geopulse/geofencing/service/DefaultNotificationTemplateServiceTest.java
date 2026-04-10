package org.github.tess1o.geopulse.geofencing.service;

import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class DefaultNotificationTemplateServiceTest {

    @Mock
    NotificationTemplateRepository templateRepository;

    @Mock
    EntityManager entityManager;

    private DefaultNotificationTemplateService service;

    @BeforeEach
    void setUp() {
        service = new DefaultNotificationTemplateService(templateRepository, entityManager);
    }

    @Test
    void shouldCreateBothDefaultsWhenMissing() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);

        when(entityManager.getReference(eq(UserEntity.class), eq(userId))).thenReturn(user);
        when(templateRepository.existsDefaultEnterByUser(userId)).thenReturn(false);
        when(templateRepository.existsDefaultLeaveByUser(userId)).thenReturn(false);

        service.ensureDefaultsForUser(userId);

        ArgumentCaptor<NotificationTemplateEntity> captor = ArgumentCaptor.forClass(NotificationTemplateEntity.class);
        verify(templateRepository, times(2)).persist(captor.capture());
        List<NotificationTemplateEntity> created = captor.getAllValues();

        assertThat(created).hasSize(2);
        assertThat(created).anySatisfy(t -> {
            assertThat(t.getDefaultForEnter()).isTrue();
            assertThat(t.getDefaultForLeave()).isFalse();
            assertThat(t.getDestination()).isEmpty();
            assertThat(t.getSendInApp()).isTrue();
            assertThat(t.getBodyTemplate()).contains("{{eventVerb}}");
        });
        assertThat(created).anySatisfy(t -> {
            assertThat(t.getDefaultForEnter()).isFalse();
            assertThat(t.getDefaultForLeave()).isTrue();
            assertThat(t.getDestination()).isEmpty();
            assertThat(t.getSendInApp()).isTrue();
            assertThat(t.getBodyTemplate()).contains("{{eventVerb}}");
        });
    }

    @Test
    void shouldNotCreateDefaultsWhenAlreadyPresent() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);

        when(entityManager.getReference(eq(UserEntity.class), eq(userId))).thenReturn(user);
        when(templateRepository.existsDefaultEnterByUser(userId)).thenReturn(true);
        when(templateRepository.existsDefaultLeaveByUser(userId)).thenReturn(true);

        service.ensureDefaultsForUser(userId);

        verify(templateRepository, never()).persist(any(NotificationTemplateEntity.class));
    }
}
