package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.NotificationTemplateDto;
import org.github.tess1o.geopulse.geofencing.model.dto.UpdateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final EntityManager entityManager;

    @Inject
    public NotificationTemplateService(NotificationTemplateRepository templateRepository,
                                       EntityManager entityManager) {
        this.templateRepository = templateRepository;
        this.entityManager = entityManager;
    }

    public List<NotificationTemplateDto> listTemplates(UUID userId) {
        return templateRepository.findByUser(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public NotificationTemplateDto createTemplate(UUID userId, CreateNotificationTemplateRequest request) {
        NotificationTemplateEntity entity = NotificationTemplateEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .name(request.getName().trim())
                .destination(normalizeDestination(request.getDestination()))
                .titleTemplate(request.getTitleTemplate())
                .bodyTemplate(request.getBodyTemplate())
                .defaultForEnter(Boolean.TRUE.equals(request.getDefaultForEnter()))
                .defaultForLeave(Boolean.TRUE.equals(request.getDefaultForLeave()))
                .enabled(Boolean.TRUE.equals(request.getEnabled()))
                .build();

        if (entity.getDefaultForEnter()) {
            clearOtherDefaultEnter(userId, null);
        }
        if (entity.getDefaultForLeave()) {
            clearOtherDefaultLeave(userId, null);
        }

        templateRepository.persist(entity);
        return toDto(entity);
    }

    @Transactional
    public NotificationTemplateDto updateTemplate(UUID userId, Long templateId, UpdateNotificationTemplateRequest request) {
        NotificationTemplateEntity entity = templateRepository.findByIdAndUser(templateId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification template not found"));

        if (request.getName() != null) {
            entity.setName(request.getName().trim());
        }
        if (request.getDestination() != null) {
            entity.setDestination(normalizeDestination(request.getDestination()));
        }
        if (request.getTitleTemplate() != null) {
            entity.setTitleTemplate(request.getTitleTemplate());
        }
        if (request.getBodyTemplate() != null) {
            entity.setBodyTemplate(request.getBodyTemplate());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        if (request.getDefaultForEnter() != null) {
            entity.setDefaultForEnter(request.getDefaultForEnter());
        }
        if (request.getDefaultForLeave() != null) {
            entity.setDefaultForLeave(request.getDefaultForLeave());
        }

        if (Boolean.TRUE.equals(entity.getDefaultForEnter())) {
            clearOtherDefaultEnter(userId, entity.getId());
        }
        if (Boolean.TRUE.equals(entity.getDefaultForLeave())) {
            clearOtherDefaultLeave(userId, entity.getId());
        }

        return toDto(entity);
    }

    @Transactional
    public void deleteTemplate(UUID userId, Long templateId) {
        NotificationTemplateEntity entity = templateRepository.findByIdAndUser(templateId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification template not found"));
        templateRepository.delete(entity);
    }

    private void clearOtherDefaultEnter(UUID userId, Long excludedTemplateId) {
        List<NotificationTemplateEntity> others = templateRepository.findDefaultEnterOthers(userId, excludedTemplateId);
        others.forEach(template -> template.setDefaultForEnter(false));
    }

    private void clearOtherDefaultLeave(UUID userId, Long excludedTemplateId) {
        List<NotificationTemplateEntity> others = templateRepository.findDefaultLeaveOthers(userId, excludedTemplateId);
        others.forEach(template -> template.setDefaultForLeave(false));
    }

    private String normalizeDestination(String destination) {
        return destination == null ? "" : destination.trim();
    }

    public NotificationTemplateDto toDto(NotificationTemplateEntity entity) {
        return NotificationTemplateDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .destination(entity.getDestination())
                .titleTemplate(entity.getTitleTemplate())
                .bodyTemplate(entity.getBodyTemplate())
                .defaultForEnter(entity.getDefaultForEnter())
                .defaultForLeave(entity.getDefaultForLeave())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
