package org.github.tess1o.geopulse.geofencing.service;

import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.NotificationTemplateDto;
import org.github.tess1o.geopulse.geofencing.model.dto.UpdateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class NotificationTemplateServiceTest {

    @Mock
    NotificationTemplateRepository templateRepository;

    @Mock
    GeofenceRuleRepository ruleRepository;

    @Mock
    GeofenceEventRepository eventRepository;

    @Mock
    EntityManager entityManager;

    private NotificationTemplateService service;

    @BeforeEach
    void setUp() {
        service = new NotificationTemplateService(templateRepository, ruleRepository, eventRepository, entityManager);
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
        verify(entityManager, times(1)).flush();
    }

    @Test
    void shouldNormalizeMultilineDestination() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(entityManager.getReference(eq(UserEntity.class), eq(userId))).thenReturn(user);

        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setName("Apprise");
        request.setDestination(" tgram://token1 \n\n discord://token2 ");
        request.setDefaultForEnter(false);
        request.setDefaultForLeave(false);
        request.setEnabled(true);

        service.createTemplate(userId, request);

        ArgumentCaptor<NotificationTemplateEntity> captor = ArgumentCaptor.forClass(NotificationTemplateEntity.class);
        verify(templateRepository).persist(captor.capture());
        assertThat(captor.getValue().getDestination()).isEqualTo("tgram://token1\ndiscord://token2");
    }

    @Test
    void shouldDefaultTemplateEnabledToTrueWhenOmitted() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(entityManager.getReference(eq(UserEntity.class), eq(userId))).thenReturn(user);

        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setName("Default Enabled");
        request.setDestination("");
        request.setTitleTemplate("Title");
        request.setBodyTemplate("Body");
        request.setDefaultForEnter(false);
        request.setDefaultForLeave(false);

        service.createTemplate(userId, request);

        ArgumentCaptor<NotificationTemplateEntity> captor = ArgumentCaptor.forClass(NotificationTemplateEntity.class);
        verify(templateRepository).persist(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    void shouldRejectUnsupportedTemplateMacro() {
        UUID userId = UUID.randomUUID();

        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setName("Invalid Macro");
        request.setDestination("");
        request.setTitleTemplate("Alert {{unknownMacro}}");
        request.setBodyTemplate("Body");
        request.setDefaultForEnter(false);
        request.setDefaultForLeave(false);
        request.setEnabled(true);

        assertThatThrownBy(() -> service.createTemplate(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported macros");
    }

    @Test
    void shouldRejectInvalidDestinationSeparator() {
        UUID userId = UUID.randomUUID();

        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setName("Invalid Destination");
        request.setDestination("tgram://token,discord://token");
        request.setTitleTemplate("Title");
        request.setBodyTemplate("Body");
        request.setDefaultForEnter(false);
        request.setDefaultForLeave(false);
        request.setEnabled(true);

        assertThatThrownBy(() -> service.createTemplate(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one destination per line");
    }

    @Test
    void shouldClearExistingDefaultsBeforeSavingNewDefault() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(entityManager.getReference(eq(UserEntity.class), eq(userId))).thenReturn(user);
        when(templateRepository.findDefaultEnterOthers(eq(userId), isNull())).thenReturn(List.of(new NotificationTemplateEntity()));

        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setName("New default");
        request.setDestination("");
        request.setTitleTemplate("{{subjectName}}");
        request.setBodyTemplate("{{subjectName}} {{eventVerb}}");
        request.setDefaultForEnter(true);
        request.setDefaultForLeave(false);
        request.setEnabled(true);

        service.createTemplate(userId, request);

        InOrder inOrder = inOrder(templateRepository, entityManager);
        inOrder.verify(templateRepository).findDefaultEnterOthers(eq(userId), isNull());
        inOrder.verify(entityManager).flush();
        inOrder.verify(templateRepository).persist(any(NotificationTemplateEntity.class));
        verify(entityManager, times(2)).flush();
    }

    @Test
    void shouldPreserveEnabledOnUpdateWhenOmitted() {
        UUID userId = UUID.randomUUID();
        long templateId = 42L;

        NotificationTemplateEntity entity = NotificationTemplateEntity.builder()
                .id(templateId)
                .name("Existing")
                .destination("")
                .titleTemplate("Title")
                .bodyTemplate("Body")
                .defaultForEnter(false)
                .defaultForLeave(false)
                .enabled(false)
                .build();

        when(templateRepository.findByIdAndUser(templateId, userId)).thenReturn(Optional.of(entity));

        UpdateNotificationTemplateRequest request = new UpdateNotificationTemplateRequest();
        request.setName("Updated Name");

        service.updateTemplate(userId, templateId, request);

        assertThat(entity.getEnabled()).isFalse();
        verify(entityManager).flush();
    }

    @Test
    void shouldDetachRuleAndEventReferencesBeforeDelete() {
        UUID userId = UUID.randomUUID();
        long templateId = 42L;
        NotificationTemplateEntity entity = NotificationTemplateEntity.builder()
                .id(templateId)
                .build();

        when(templateRepository.findByIdAndUser(templateId, userId)).thenReturn(Optional.of(entity));

        service.deleteTemplate(userId, templateId);

        InOrder inOrder = inOrder(ruleRepository, eventRepository, entityManager, templateRepository);
        inOrder.verify(ruleRepository).clearEnterTemplate(userId, templateId);
        inOrder.verify(ruleRepository).clearLeaveTemplate(userId, templateId);
        inOrder.verify(eventRepository).clearTemplateReference(userId, templateId);
        inOrder.verify(entityManager).flush();
        inOrder.verify(templateRepository).delete(entity);
    }
}
