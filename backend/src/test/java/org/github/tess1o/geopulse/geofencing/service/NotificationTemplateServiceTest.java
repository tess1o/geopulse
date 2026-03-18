package org.github.tess1o.geopulse.geofencing.service;

import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.NotificationTemplateDto;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class NotificationTemplateServiceTest {

    @Mock
    NotificationTemplateRepository templateRepository;

    @Mock
    EntityManager entityManager;

    private NotificationTemplateService service;

    @BeforeEach
    void setUp() {
        service = new NotificationTemplateService(templateRepository, entityManager);
    }

    @Test
    void shouldAllowBlankDestinationForInAppTemplates() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(entityManager.getReference(eq(UserEntity.class), eq(userId))).thenReturn(user);

        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setName("In-App");
        request.setDestination("   ");
        request.setTitleTemplate("Hello");
        request.setBodyTemplate("World");
        request.setDefaultForEnter(false);
        request.setDefaultForLeave(false);
        request.setEnabled(true);

        NotificationTemplateDto result = service.createTemplate(userId, request);

        ArgumentCaptor<NotificationTemplateEntity> captor = ArgumentCaptor.forClass(NotificationTemplateEntity.class);
        verify(templateRepository).persist(captor.capture());
        assertThat(captor.getValue().getDestination()).isEmpty();
        assertThat(result.getDestination()).isEmpty();
    }
}
