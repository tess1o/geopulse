package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.NotificationTemplateDto;
import org.github.tess1o.geopulse.geofencing.model.dto.UpdateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
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
    private final GeofenceRuleRepository ruleRepository;
    private final GeofenceEventRepository eventRepository;
    private final EntityManager entityManager;

    @Inject
    public NotificationTemplateService(NotificationTemplateRepository templateRepository,
                                       GeofenceRuleRepository ruleRepository,
                                       GeofenceEventRepository eventRepository,
                                       EntityManager entityManager) {
        this.templateRepository = templateRepository;
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.entityManager = entityManager;
    }

    public List<NotificationTemplateDto> listTemplates(UUID userId) {
        return templateRepository.findByUser(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public NotificationTemplateDto createTemplate(UUID userId, CreateNotificationTemplateRequest request) {
        TemplateWriteInput writeInput = buildCreateWriteInput(request);
        validateTemplateRequest(
                writeInput.name(),
                writeInput.destination(),
                writeInput.externalRoutingMode(),
                writeInput.appriseConfigKey(),
                writeInput.appriseTag(),
                writeInput.titleTemplate(),
                writeInput.bodyTemplate(),
                writeInput.enabled(),
                writeInput.sendInApp()
        );

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
        validateTemplateRequest(
                writeInput.name(),
                writeInput.destination(),
                writeInput.externalRoutingMode(),
                writeInput.appriseConfigKey(),
                writeInput.appriseTag(),
                writeInput.titleTemplate(),
                writeInput.bodyTemplate(),
                writeInput.enabled(),
                writeInput.sendInApp()
        );
        return persistTemplate(userId, entity, writeInput, false);
    }

    @Transactional
    public void deleteTemplate(UUID userId, Long templateId) {
        NotificationTemplateEntity entity = templateRepository.findByIdAndUser(templateId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification template not found"));

        // Defensive detach before delete: prevents accidental cascades if DB constraints differ across environments.
        ruleRepository.clearEnterTemplate(userId, templateId);
        ruleRepository.clearLeaveTemplate(userId, templateId);
        eventRepository.clearTemplateReference(userId, templateId);
        entityManager.flush();

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
                                         AppriseExternalRoutingMode externalRoutingMode,
                                         String appriseConfigKey,
                                         String appriseTag,
                                         String titleTemplate,
                                         String bodyTemplate,
                                         boolean enabled,
                                         boolean sendInApp) {
        validateName(name);
        validateRoutingSettings(externalRoutingMode, destination, appriseConfigKey, appriseTag);
        validateTemplateSyntax("Title template", titleTemplate);
        validateTemplateSyntax("Body template", bodyTemplate);
        validateEnabledChannels(enabled, sendInApp, externalRoutingMode, destination, appriseConfigKey);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (name.length() > 120) {
            throw new IllegalArgumentException("Template name must be at most 120 characters");
        }
    }

    private void validateRoutingSettings(AppriseExternalRoutingMode mode,
                                         String destination,
                                         String appriseConfigKey,
                                         String appriseTag) {
        AppriseExternalRoutingMode resolvedMode = resolveRoutingMode(mode);
        if (resolvedMode == AppriseExternalRoutingMode.URLS) {
            NotificationDestinationParser.parseUrls(destination);
            return;
        }

        if (appriseConfigKey != null && appriseConfigKey.length() > 255) {
            throw new IllegalArgumentException("Apprise config key must be at most 255 characters.");
        }
        if (appriseTag != null && appriseTag.length() > 255) {
            throw new IllegalArgumentException("Apprise tag must be at most 255 characters.");
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

    private void validateEnabledChannels(boolean enabled,
                                         boolean sendInApp,
                                         AppriseExternalRoutingMode mode,
                                         String destination,
                                         String appriseConfigKey) {
        if (!enabled) {
            return;
        }

        boolean hasExternalRoute = hasExternalRouting(mode, destination, appriseConfigKey);
        if (!sendInApp && !hasExternalRoute) {
            if (resolveRoutingMode(mode) == AppriseExternalRoutingMode.KEY_TAG) {
                throw new IllegalArgumentException(
                        "Apprise config key is required when KEY_TAG routing mode is selected."
                );
            }
            throw new IllegalArgumentException(
                    "Enabled template must have at least one active channel: in-app or external routing."
            );
        }
    }

    private boolean hasExternalRouting(AppriseExternalRoutingMode mode, String destination, String appriseConfigKey) {
        AppriseExternalRoutingMode resolvedMode = resolveRoutingMode(mode);
        if (resolvedMode == AppriseExternalRoutingMode.KEY_TAG) {
            return appriseConfigKey != null && !appriseConfigKey.isBlank();
        }
        return destination != null && !destination.isBlank();
    }

    private AppriseExternalRoutingMode resolveRoutingMode(AppriseExternalRoutingMode mode) {
        return mode == null ? AppriseExternalRoutingMode.URLS : mode;
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

    private String normalizeConfigKey(String configKey) {
        if (configKey == null) {
            return null;
        }
        String normalized = configKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        String normalized = tag.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public NotificationTemplateDto toDto(NotificationTemplateEntity entity) {
        return NotificationTemplateDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .destination(entity.getDestination())
                .externalRoutingMode(resolveRoutingMode(entity.getExternalRoutingMode()))
                .appriseConfigKey(entity.getAppriseConfigKey())
                .appriseTag(entity.getAppriseTag())
                .titleTemplate(entity.getTitleTemplate())
                .bodyTemplate(entity.getBodyTemplate())
                .defaultForEnter(entity.getDefaultForEnter())
                .defaultForLeave(entity.getDefaultForLeave())
                .enabled(entity.getEnabled())
                .sendInApp(entity.getSendInApp())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TemplateWriteInput buildCreateWriteInput(CreateNotificationTemplateRequest request) {
        AppriseExternalRoutingMode routingMode = resolveRoutingMode(request.getExternalRoutingMode());
        String destination = routingMode == AppriseExternalRoutingMode.URLS
                ? normalizeDestination(request.getDestination())
                : "";
        return new TemplateWriteInput(
                request.getName() == null ? null : request.getName().trim(),
                destination,
                routingMode,
                normalizeConfigKey(request.getAppriseConfigKey()),
                normalizeTag(request.getAppriseTag()),
                normalizeTemplate(request.getTitleTemplate()),
                normalizeTemplate(request.getBodyTemplate()),
                Boolean.TRUE.equals(request.getDefaultForEnter()),
                Boolean.TRUE.equals(request.getDefaultForLeave()),
                request.getEnabled() == null || request.getEnabled(),
                request.getSendInApp() == null || request.getSendInApp()
        );
    }

    private TemplateWriteInput buildUpdateWriteInput(NotificationTemplateEntity entity,
                                                     UpdateNotificationTemplateRequest request) {
        AppriseExternalRoutingMode routingMode = request.getExternalRoutingMode() != null
                ? resolveRoutingMode(request.getExternalRoutingMode())
                : resolveRoutingMode(entity.getExternalRoutingMode());

        String destination = request.getDestination() != null
                ? (routingMode == AppriseExternalRoutingMode.URLS ? normalizeDestination(request.getDestination()) : "")
                : entity.getDestination();

        return new TemplateWriteInput(
                request.getName() != null ? request.getName().trim() : entity.getName(),
                destination,
                routingMode,
                request.getAppriseConfigKey() != null ? normalizeConfigKey(request.getAppriseConfigKey()) : entity.getAppriseConfigKey(),
                request.getAppriseTag() != null ? normalizeTag(request.getAppriseTag()) : entity.getAppriseTag(),
                request.getTitleTemplate() != null ? normalizeTemplate(request.getTitleTemplate()) : entity.getTitleTemplate(),
                request.getBodyTemplate() != null ? normalizeTemplate(request.getBodyTemplate()) : entity.getBodyTemplate(),
                request.getDefaultForEnter() != null ? request.getDefaultForEnter() : Boolean.TRUE.equals(entity.getDefaultForEnter()),
                request.getDefaultForLeave() != null ? request.getDefaultForLeave() : Boolean.TRUE.equals(entity.getDefaultForLeave()),
                request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE.equals(entity.getEnabled()),
                request.getSendInApp() != null ? request.getSendInApp() : Boolean.TRUE.equals(entity.getSendInApp())
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
        AppriseExternalRoutingMode routingMode = resolveRoutingMode(writeInput.externalRoutingMode());
        entity.setName(writeInput.name());
        entity.setExternalRoutingMode(routingMode);
        if (routingMode == AppriseExternalRoutingMode.KEY_TAG) {
            entity.setDestination(writeInput.destination() == null ? "" : writeInput.destination());
            entity.setAppriseConfigKey(writeInput.appriseConfigKey());
            entity.setAppriseTag(writeInput.appriseTag());
        } else {
            entity.setDestination(writeInput.destination());
            entity.setAppriseConfigKey(null);
            entity.setAppriseTag(null);
        }
        entity.setTitleTemplate(writeInput.titleTemplate());
        entity.setBodyTemplate(writeInput.bodyTemplate());
        entity.setDefaultForEnter(writeInput.defaultForEnter());
        entity.setDefaultForLeave(writeInput.defaultForLeave());
        entity.setEnabled(writeInput.enabled());
        entity.setSendInApp(writeInput.sendInApp());
    }

    private record TemplateWriteInput(String name,
                                      String destination,
                                      AppriseExternalRoutingMode externalRoutingMode,
                                      String appriseConfigKey,
                                      String appriseTag,
                                      String titleTemplate,
                                      String bodyTemplate,
                                      boolean defaultForEnter,
                                      boolean defaultForLeave,
                                      boolean enabled,
                                      boolean sendInApp) {
    }
}
