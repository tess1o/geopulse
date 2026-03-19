package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.NotificationTemplateDto;
import org.github.tess1o.geopulse.geofencing.model.dto.UpdateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.geofencing.util.NotificationDestinationParser;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class NotificationTemplateService {

    private static final Pattern TEMPLATE_MACRO_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9]*)\\s*}}");
    private static final Set<String> ALLOWED_TEMPLATE_MACROS = Set.of(
            "subjectName",
            "eventCode",
            "eventVerb",
            "geofenceName",
            "timestamp",
            "timestampUtc",
            "lat",
            "lon"
    );

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
        TemplateWriteInput writeInput = buildCreateWriteInput(request);
        validateTemplateRequest(writeInput.name(), writeInput.destination(), writeInput.titleTemplate(), writeInput.bodyTemplate());

        NotificationTemplateEntity entity = NotificationTemplateEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .build();

        return persistTemplate(userId, entity, writeInput, true);
    }

    @Transactional
    public NotificationTemplateDto updateTemplate(UUID userId, Long templateId, UpdateNotificationTemplateRequest request) {
        NotificationTemplateEntity entity = templateRepository.findByIdAndUser(templateId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification template not found"));

        TemplateWriteInput writeInput = buildUpdateWriteInput(entity, request);
        validateTemplateRequest(writeInput.name(), writeInput.destination(), writeInput.titleTemplate(), writeInput.bodyTemplate());
        return persistTemplate(userId, entity, writeInput, false);
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

    private void clearDefaultsAndFlush(UUID userId,
                                       Long excludedTemplateId,
                                       boolean clearEnterDefaults,
                                       boolean clearLeaveDefaults) {
        if (clearEnterDefaults) {
            clearOtherDefaultEnter(userId, excludedTemplateId);
        }
        if (clearLeaveDefaults) {
            clearOtherDefaultLeave(userId, excludedTemplateId);
        }
        entityManager.flush();
    }

    private void validateTemplateRequest(String name,
                                         String destination,
                                         String titleTemplate,
                                         String bodyTemplate) {
        validateName(name);
        validateDestination(destination);
        validateTemplateSyntax("Title template", titleTemplate);
        validateTemplateSyntax("Body template", bodyTemplate);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (name.length() > 120) {
            throw new IllegalArgumentException("Template name must be at most 120 characters");
        }
    }

    private void validateDestination(String destination) {
        NotificationDestinationParser.parseUrls(destination);
    }

    private void validateTemplateSyntax(String fieldName, String template) {
        if (template == null || template.isBlank()) {
            return;
        }

        Matcher matcher = TEMPLATE_MACRO_PATTERN.matcher(template);
        Set<String> unknownMacros = new LinkedHashSet<>();
        while (matcher.find()) {
            String macroName = matcher.group(1);
            if (!ALLOWED_TEMPLATE_MACROS.contains(macroName)) {
                unknownMacros.add(macroName);
            }
        }

        if (!unknownMacros.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported macros: "
                    + String.join(", ", unknownMacros));
        }

        String templateWithoutMacros = TEMPLATE_MACRO_PATTERN.matcher(template).replaceAll("");
        if (templateWithoutMacros.contains("{{") || templateWithoutMacros.contains("}}")) {
            throw new IllegalArgumentException(fieldName + " contains invalid macro syntax. Use {{macroName}}.");
        }
    }

    private IllegalArgumentException translatePersistenceException(PersistenceException exception) {
        IllegalArgumentException translated = tryTranslateConstraintException(exception);
        if (translated != null) {
            return translated;
        }
        return new IllegalArgumentException("Unable to save notification template due to a data constraint.");
    }

    private IllegalArgumentException tryTranslateConstraintException(Throwable exception) {
        String message = flattenExceptionMessage(exception).toLowerCase(Locale.ROOT);
        if (message.contains("uq_notification_templates_default_enter")) {
            return new IllegalArgumentException("Failed to set default ENTER template. Please try again.");
        }
        if (message.contains("uq_notification_templates_default_leave")) {
            return new IllegalArgumentException("Failed to set default LEAVE template. Please try again.");
        }
        return null;
    }

    private String flattenExceptionMessage(Throwable exception) {
        List<String> messages = new ArrayList<>();
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                messages.add(current.getMessage());
            }
            current = current.getCause();
        }
        return String.join(" ", messages);
    }

    private String normalizeDestination(String destination) {
        return NotificationDestinationParser.normalize(destination);
    }

    private String normalizeTemplate(String template) {
        if (template == null) {
            return null;
        }
        String normalized = template.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private TemplateWriteInput buildCreateWriteInput(CreateNotificationTemplateRequest request) {
        return new TemplateWriteInput(
                request.getName() == null ? null : request.getName().trim(),
                normalizeDestination(request.getDestination()),
                normalizeTemplate(request.getTitleTemplate()),
                normalizeTemplate(request.getBodyTemplate()),
                Boolean.TRUE.equals(request.getDefaultForEnter()),
                Boolean.TRUE.equals(request.getDefaultForLeave()),
                request.getEnabled() == null || request.getEnabled()
        );
    }

    private TemplateWriteInput buildUpdateWriteInput(NotificationTemplateEntity entity,
                                                     UpdateNotificationTemplateRequest request) {
        return new TemplateWriteInput(
                request.getName() != null ? request.getName().trim() : entity.getName(),
                request.getDestination() != null ? normalizeDestination(request.getDestination()) : entity.getDestination(),
                request.getTitleTemplate() != null ? normalizeTemplate(request.getTitleTemplate()) : entity.getTitleTemplate(),
                request.getBodyTemplate() != null ? normalizeTemplate(request.getBodyTemplate()) : entity.getBodyTemplate(),
                request.getDefaultForEnter() != null ? request.getDefaultForEnter() : Boolean.TRUE.equals(entity.getDefaultForEnter()),
                request.getDefaultForLeave() != null ? request.getDefaultForLeave() : Boolean.TRUE.equals(entity.getDefaultForLeave()),
                request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE.equals(entity.getEnabled())
        );
    }

    private NotificationTemplateDto persistTemplate(UUID userId,
                                                    NotificationTemplateEntity entity,
                                                    TemplateWriteInput writeInput,
                                                    boolean isCreate) {
        try {
            if (writeInput.defaultForEnter() || writeInput.defaultForLeave()) {
                clearDefaultsAndFlush(userId, isCreate ? null : entity.getId(), writeInput.defaultForEnter(), writeInput.defaultForLeave());
            }

            applyWriteInput(entity, writeInput);
            if (isCreate) {
                templateRepository.persist(entity);
            }
            entityManager.flush();
            return toDto(entity);
        } catch (PersistenceException e) {
            throw translatePersistenceException(e);
        } catch (RuntimeException e) {
            IllegalArgumentException translated = tryTranslateConstraintException(e);
            if (translated != null) {
                throw translated;
            }
            throw e;
        }
    }

    private void applyWriteInput(NotificationTemplateEntity entity, TemplateWriteInput writeInput) {
        entity.setName(writeInput.name());
        entity.setDestination(writeInput.destination());
        entity.setTitleTemplate(writeInput.titleTemplate());
        entity.setBodyTemplate(writeInput.bodyTemplate());
        entity.setDefaultForEnter(writeInput.defaultForEnter());
        entity.setDefaultForLeave(writeInput.defaultForLeave());
        entity.setEnabled(writeInput.enabled());
    }

    private record TemplateWriteInput(String name,
                                      String destination,
                                      String titleTemplate,
                                      String bodyTemplate,
                                      boolean defaultForEnter,
                                      boolean defaultForLeave,
                                      boolean enabled) {
    }
}
