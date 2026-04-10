package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.geofencing.model.entity.*;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleStateRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.notifications.service.GeofenceNotificationProjectionService;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GeofenceEvaluationService {

    private static final DateTimeFormatter DATE_TIME_FORMAT_MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMAT_DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMAT_YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GeofenceRuleRepository ruleRepository;
    private final GeofenceRuleStateRepository stateRepository;
    private final GeofenceEventRepository eventRepository;
    private final NotificationTemplateRepository templateRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserFriendPermissionRepository permissionRepository;
    private final GeofenceTemplateRenderer templateRenderer;
    private final GeofenceNotificationProjectionService notificationProjectionService;

    @Inject
    public GeofenceEvaluationService(GeofenceRuleRepository ruleRepository,
                                     GeofenceRuleStateRepository stateRepository,
                                     GeofenceEventRepository eventRepository,
                                     NotificationTemplateRepository templateRepository,
                                     FriendshipRepository friendshipRepository,
                                     UserFriendPermissionRepository permissionRepository,
                                     GeofenceTemplateRenderer templateRenderer,
                                     GeofenceNotificationProjectionService notificationProjectionService) {
        this.ruleRepository = ruleRepository;
        this.stateRepository = stateRepository;
        this.eventRepository = eventRepository;
        this.templateRepository = templateRepository;
        this.friendshipRepository = friendshipRepository;
        this.permissionRepository = permissionRepository;
        this.templateRenderer = templateRenderer;
        this.notificationProjectionService = notificationProjectionService;
    }

    @Transactional
    public void handlePersistedPoint(GpsPointEntity point) {
        if (point == null || point.getUser() == null || point.getCoordinates() == null || point.getTimestamp() == null) {
            return;
        }

        UserEntity subject = point.getUser();
        UUID subjectUserId = subject.getId();
        List<GeofenceRuleEntity> rules = ruleRepository.findActiveBySubject(subjectUserId);
        if (rules.isEmpty()) {
            return;
        }

        double latitude = point.getCoordinates().getY();
        double longitude = point.getCoordinates().getX();

        for (GeofenceRuleEntity rule : rules) {
            try {
                if (!isSubjectTrackable(rule.getOwnerUser(), subject)) {
                    continue;
                }
                evaluateRulePoint(rule, subject, point, latitude, longitude);
            } catch (Exception e) {
                log.warn("Failed to evaluate geofence rule {} for point {}: {}", rule.getId(), point.getId(), e.getMessage());
            }
        }
    }

    private boolean isSubjectTrackable(UserEntity ownerUser, UserEntity subjectUser) {
        if (ownerUser == null || ownerUser.getId() == null || subjectUser == null || subjectUser.getId() == null) {
            return false;
        }

        UUID ownerId = ownerUser.getId();
        UUID subjectId = subjectUser.getId();

        if (ownerId.equals(subjectId)) {
            return true;
        }

        if (!friendshipRepository.existsFriendship(ownerId, subjectId)) {
            return false;
        }

        return permissionRepository.hasLiveLocationPermission(subjectId, ownerId);
    }

    private void evaluateRulePoint(GeofenceRuleEntity rule,
                                   UserEntity subject,
                                   GpsPointEntity point,
                                   double latitude,
                                   double longitude) {
        boolean inside = isInside(rule, latitude, longitude);

        GeofenceRuleStateId stateId = new GeofenceRuleStateId(rule.getId(), subject.getId());
        var existingStateOpt = stateRepository.findByIdOptional(stateId);
        if (existingStateOpt.isEmpty()) {
            GeofenceRuleStateEntity newState = GeofenceRuleStateEntity.builder()
                    .id(stateId)
                    .rule(rule)
                    .subjectUser(subject)
                    .currentInside(inside)
                    .lastPoint(point)
                    .lastTransitionAt(point.getTimestamp())
                    .lastNotifiedInside(inside)
                    .build();

            if (inside && Boolean.TRUE.equals(rule.getMonitorEnter())) {
                emitEvent(rule, subject, point, GeofenceEventType.ENTER);
                newState.setLastNotifiedAt(point.getTimestamp());
                newState.setLastNotifiedInside(true);
            }

            stateRepository.persist(newState);
            return;
        }

        GeofenceRuleStateEntity state = existingStateOpt.get();
        state.setLastPoint(point);

        boolean changed = !Objects.equals(state.getCurrentInside(), inside);
        if (changed) {
            state.setCurrentInside(inside);
            state.setLastTransitionAt(point.getTimestamp());
            GeofenceEventType eventType = inside ? GeofenceEventType.ENTER : GeofenceEventType.LEAVE;

            if (isMonitored(rule, eventType)) {
                maybeEmitWithCooldown(rule, subject, state, point, eventType, inside);
            } else {
                state.setLastNotifiedInside(inside);
            }
            return;
        }

        if (state.getLastNotifiedInside() == null || !state.getLastNotifiedInside().equals(inside)) {
            GeofenceEventType pendingEventType = inside ? GeofenceEventType.ENTER : GeofenceEventType.LEAVE;
            if (isMonitored(rule, pendingEventType)) {
                maybeEmitWithCooldown(rule, subject, state, point, pendingEventType, inside);
            } else {
                state.setLastNotifiedInside(inside);
            }
        }
    }

    private void maybeEmitWithCooldown(GeofenceRuleEntity rule,
                                       UserEntity subject,
                                       GeofenceRuleStateEntity state,
                                       GpsPointEntity point,
                                       GeofenceEventType eventType,
                                       boolean insideStateAfterEvent) {
        if (!isCooldownElapsed(state.getLastNotifiedAt(), point.getTimestamp(), rule.getCooldownSeconds())) {
            return;
        }

        emitEvent(rule, subject, point, eventType);
        state.setLastNotifiedAt(point.getTimestamp());
        state.setLastNotifiedInside(insideStateAfterEvent);
    }

    private boolean isCooldownElapsed(Instant lastNotifiedAt, Instant now, Integer cooldownSeconds) {
        if (lastNotifiedAt == null) {
            return true;
        }
        int cooldown = cooldownSeconds != null ? Math.max(cooldownSeconds, 0) : 0;
        if (cooldown == 0) {
            return true;
        }
        return Duration.between(lastNotifiedAt, now).getSeconds() >= cooldown;
    }

    private void emitEvent(GeofenceRuleEntity rule, UserEntity subject, GpsPointEntity point, GeofenceEventType eventType) {
        NotificationTemplateEntity template = resolveTemplate(rule, eventType);
        UserEntity owner = rule.getOwnerUser();

        String subjectName = subject.getFullName() != null && !subject.getFullName().isBlank()
                ? subject.getFullName()
                : subject.getEmail();
        String eventCode = eventType.name();
        String eventVerb = eventType == GeofenceEventType.ENTER ? "entered" : "left";
        String timestampLocal = formatTimestampForOwner(point.getTimestamp(), owner);

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("subjectName", subjectName);
        placeholders.put("eventCode", eventCode);
        placeholders.put("eventVerb", eventVerb);
        placeholders.put("geofenceName", rule.getName());
        placeholders.put("timestamp", timestampLocal);
        placeholders.put("timestampUtc", point.getTimestamp().toString());
        placeholders.put("lat", String.valueOf(point.getCoordinates().getY()));
        placeholders.put("lon", String.valueOf(point.getCoordinates().getX()));

        String defaultTitle = "Geofence Alert: " + rule.getName();
        String defaultMessage = subjectName + " " + eventVerb + " geofence '" + rule.getName() + "' at " + timestampLocal;

        String title = template != null && template.getTitleTemplate() != null && !template.getTitleTemplate().isBlank()
                ? templateRenderer.render(template.getTitleTemplate(), placeholders)
                : defaultTitle;

        String message = template != null && template.getBodyTemplate() != null && !template.getBodyTemplate().isBlank()
                ? templateRenderer.render(template.getBodyTemplate(), placeholders)
                : defaultMessage;

        GeofenceDeliveryStatus deliveryStatus = shouldQueueForApprise(template)
                ? GeofenceDeliveryStatus.PENDING
                : GeofenceDeliveryStatus.SKIPPED;

        GeofenceEventEntity event = GeofenceEventEntity.builder()
                .ownerUser(rule.getOwnerUser())
                .subjectUser(subject)
                .rule(rule)
                .template(template)
                .point(point)
                .eventType(eventType)
                .occurredAt(point.getTimestamp())
                .title(title)
                .message(message)
                .subjectDisplayName(subjectName)
                .deliveryStatus(deliveryStatus)
                .deliveryAttempts(0)
                .createdAt(Instant.now())
                .build();

        if (deliveryStatus == GeofenceDeliveryStatus.SKIPPED) {
            event.setDeliveredAt(Instant.now());
            event.setLastDeliveryError("No enabled template configured for external delivery");
        }

        eventRepository.persist(event);
        eventRepository.flush();

        Map<String, Object> metadataSnapshot = new LinkedHashMap<>();
        metadataSnapshot.put("ruleId", rule.getId());
        metadataSnapshot.put("ruleName", rule.getName());
        metadataSnapshot.put("subjectUserId", subject.getId());
        metadataSnapshot.put("subjectDisplayName", subjectName);
        metadataSnapshot.put("eventCode", eventCode);
        metadataSnapshot.put("eventVerb", eventVerb);
        metadataSnapshot.put("lat", point.getCoordinates().getY());
        metadataSnapshot.put("lon", point.getCoordinates().getX());

        if (shouldPublishInApp(template)) {
            notificationProjectionService.publishSnapshot(event, metadataSnapshot);
        }
    }

    private NotificationTemplateEntity resolveTemplate(GeofenceRuleEntity rule, GeofenceEventType eventType) {
        NotificationTemplateEntity selected = eventType == GeofenceEventType.ENTER
                ? rule.getEnterTemplate()
                : rule.getLeaveTemplate();

        UUID ownerId = rule.getOwnerUser().getId();
        if (selected != null && selected.getUser() != null && ownerId.equals(selected.getUser().getId())
                && Boolean.TRUE.equals(selected.getEnabled())) {
            return selected;
        }

        return eventType == GeofenceEventType.ENTER
                ? templateRepository.findDefaultEnterByUser(ownerId).orElse(null)
                : templateRepository.findDefaultLeaveByUser(ownerId).orElse(null);
    }

    private boolean shouldQueueForApprise(NotificationTemplateEntity template) {
        return template != null
                && Boolean.TRUE.equals(template.getEnabled())
                && template.getDestination() != null
                && !template.getDestination().isBlank();
    }

    private boolean shouldPublishInApp(NotificationTemplateEntity template) {
        // Bell/toast inbox projection should happen only when an in-app-enabled template is resolved.
        return template != null && Boolean.TRUE.equals(template.getSendInApp());
    }

    private boolean isMonitored(GeofenceRuleEntity rule, GeofenceEventType eventType) {
        return eventType == GeofenceEventType.ENTER
                ? Boolean.TRUE.equals(rule.getMonitorEnter())
                : Boolean.TRUE.equals(rule.getMonitorLeave());
    }

    private boolean isInside(GeofenceRuleEntity rule, double lat, double lon) {
        double northLat = Math.max(rule.getNorthEastLat(), rule.getSouthWestLat());
        double southLat = Math.min(rule.getNorthEastLat(), rule.getSouthWestLat());
        double eastLon = Math.max(rule.getNorthEastLon(), rule.getSouthWestLon());
        double westLon = Math.min(rule.getNorthEastLon(), rule.getSouthWestLon());

        return lat >= southLat && lat <= northLat && lon >= westLon && lon <= eastLon;
    }

    private String formatTimestampForOwner(Instant timestamp, UserEntity owner) {
        ZoneId zoneId = resolveZoneId(owner != null ? owner.getTimezone() : null);
        DateTimeFormatter formatter = resolveDateTimeFormatter(owner != null ? owner.getDateFormat() : null);
        return timestamp.atZone(zoneId).format(formatter);
    }

    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private DateTimeFormatter resolveDateTimeFormatter(String dateFormat) {
        if (dateFormat == null || dateFormat.isBlank()) {
            return DATE_TIME_FORMAT_MDY;
        }
        return switch (dateFormat.trim().toUpperCase(Locale.ROOT)) {
            case "MDY", "US", "MM/DD/YYYY", "MM-DD-YYYY" -> DATE_TIME_FORMAT_MDY;
            case "DMY", "EU", "DD/MM/YYYY", "DD-MM-YYYY" -> DATE_TIME_FORMAT_DMY;
            case "YMD", "ISO", "YYYY-MM-DD" -> DATE_TIME_FORMAT_YMD;
            default -> DATE_TIME_FORMAT_MDY;
        };
    }
}
