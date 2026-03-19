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
    private static final Pattern DESTINATION_URL_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.+$");
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
        String name = request.getName() == null ? null : request.getName().trim();
        String destination = normalizeDestination(request.getDestination());
        String titleTemplate = normalizeTemplate(request.getTitleTemplate());
        String bodyTemplate = normalizeTemplate(request.getBodyTemplate());
        boolean defaultForEnter = Boolean.TRUE.equals(request.getDefaultForEnter());
        boolean defaultForLeave = Boolean.TRUE.equals(request.getDefaultForLeave());

        validateTemplateRequest(name, destination, titleTemplate, bodyTemplate);

        try {
            if (defaultForEnter || defaultForLeave) {
                clearDefaultsAndFlush(userId, null, defaultForEnter, defaultForLeave);
            }

            NotificationTemplateEntity entity = NotificationTemplateEntity.builder()
                    .user(entityManager.getReference(UserEntity.class, userId))
                    .name(name)
                    .destination(destination)
                    .titleTemplate(titleTemplate)
                    .bodyTemplate(bodyTemplate)
                    .defaultForEnter(defaultForEnter)
                    .defaultForLeave(defaultForLeave)
                    .enabled(Boolean.TRUE.equals(request.getEnabled()))
                    .build();

            templateRepository.persist(entity);
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

    @Transactional
    public NotificationTemplateDto updateTemplate(UUID userId, Long templateId, UpdateNotificationTemplateRequest request) {
        NotificationTemplateEntity entity = templateRepository.findByIdAndUser(templateId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification template not found"));

        String name = request.getName() != null ? request.getName().trim() : entity.getName();
        String destination = request.getDestination() != null
                ? normalizeDestination(request.getDestination())
                : entity.getDestination();
        String titleTemplate = request.getTitleTemplate() != null
                ? normalizeTemplate(request.getTitleTemplate())
                : entity.getTitleTemplate();
        String bodyTemplate = request.getBodyTemplate() != null
                ? normalizeTemplate(request.getBodyTemplate())
                : entity.getBodyTemplate();
        boolean defaultForEnter = request.getDefaultForEnter() != null
                ? request.getDefaultForEnter()
                : Boolean.TRUE.equals(entity.getDefaultForEnter());
        boolean defaultForLeave = request.getDefaultForLeave() != null
                ? request.getDefaultForLeave()
                : Boolean.TRUE.equals(entity.getDefaultForLeave());
        boolean enabled = request.getEnabled() != null
                ? request.getEnabled()
                : Boolean.TRUE.equals(entity.getEnabled());

        validateTemplateRequest(name, destination, titleTemplate, bodyTemplate);

        try {
            if (defaultForEnter || defaultForLeave) {
                clearDefaultsAndFlush(userId, entity.getId(), defaultForEnter, defaultForLeave);
            }

            entity.setName(name);
            entity.setDestination(destination);
            entity.setTitleTemplate(titleTemplate);
            entity.setBodyTemplate(bodyTemplate);
            entity.setEnabled(enabled);
            entity.setDefaultForEnter(defaultForEnter);
            entity.setDefaultForLeave(defaultForLeave);

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
        if (destination == null || destination.isBlank()) {
            return;
        }

        String[] lines = destination.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.contains(",") || line.contains(";")) {
                throw new IllegalArgumentException("Destination line " + (i + 1)
                        + " must contain exactly one URL. Use one destination per line.");
            }
            if (!DESTINATION_URL_PATTERN.matcher(line).matches()) {
                throw new IllegalArgumentException("Destination line " + (i + 1)
                        + " must be a valid URL in the format scheme://...");
            }
        }
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
        if (destination == null || destination.isBlank()) {
            return "";
        }

        String[] lines = destination.split("\\R");
        List<String> normalized = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return String.join("\n", normalized);
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
}
