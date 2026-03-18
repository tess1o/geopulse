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
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GeofenceEvaluationService {

    private final GeofenceRuleRepository ruleRepository;
    private final GeofenceRuleStateRepository stateRepository;
    private final GeofenceEventRepository eventRepository;
    private final NotificationTemplateRepository templateRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserFriendPermissionRepository permissionRepository;
    private final GeofenceTemplateRenderer templateRenderer;

    @Inject
    public GeofenceEvaluationService(GeofenceRuleRepository ruleRepository,
                                     GeofenceRuleStateRepository stateRepository,
                                     GeofenceEventRepository eventRepository,
                                     NotificationTemplateRepository templateRepository,
                                     FriendshipRepository friendshipRepository,
                                     UserFriendPermissionRepository permissionRepository,
                                     GeofenceTemplateRenderer templateRenderer) {
        this.ruleRepository = ruleRepository;
        this.stateRepository = stateRepository;
        this.eventRepository = eventRepository;
        this.templateRepository = templateRepository;
        this.friendshipRepository = friendshipRepository;
        this.permissionRepository = permissionRepository;
        this.templateRenderer = templateRenderer;
    }

    @Transactional
    public void handlePersistedPoint(GpsPointEntity point) {
        if (point == null || point.getUser() == null || point.getCoordinates() == null || point.getTimestamp() == null) {
            return;
        }

        UUID subjectUserId = point.getUser().getId();
        List<GeofenceRuleEntity> rules = ruleRepository.findActiveBySubject(subjectUserId);
        if (rules.isEmpty()) {
            return;
        }

        double latitude = point.getCoordinates().getY();
        double longitude = point.getCoordinates().getX();

        for (GeofenceRuleEntity rule : rules) {
            try {
                if (!isSubjectTrackable(rule)) {
                    continue;
                }
                evaluateRulePoint(rule, point, latitude, longitude);
            } catch (Exception e) {
                log.warn("Failed to evaluate geofence rule {} for point {}: {}", rule.getId(), point.getId(), e.getMessage());
            }
        }
    }

    private boolean isSubjectTrackable(GeofenceRuleEntity rule) {
        UUID ownerId = rule.getOwnerUser().getId();
        UUID subjectId = rule.getSubjectUser().getId();

        if (ownerId.equals(subjectId)) {
            return true;
        }

        if (!friendshipRepository.existsFriendship(ownerId, subjectId)) {
            return false;
        }

        return permissionRepository.hasLiveLocationPermission(subjectId, ownerId);
    }

    private void evaluateRulePoint(GeofenceRuleEntity rule, GpsPointEntity point, double latitude, double longitude) {
        boolean inside = isInside(rule, latitude, longitude);

        Optional<GeofenceRuleStateEntity> existingStateOpt = stateRepository.findByIdOptional(rule.getId());
        if (existingStateOpt.isEmpty()) {
            GeofenceRuleStateEntity newState = GeofenceRuleStateEntity.builder()
                    .rule(rule)
                    .currentInside(inside)
                    .lastPoint(point)
                    .lastTransitionAt(point.getTimestamp())
                    .lastNotifiedInside(inside)
                    .build();

            if (inside && Boolean.TRUE.equals(rule.getMonitorEnter())) {
                emitEvent(rule, point, GeofenceEventType.ENTER);
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
                maybeEmitWithCooldown(rule, state, point, eventType, inside);
            } else {
                state.setLastNotifiedInside(inside);
            }
            return;
        }

        if (state.getLastNotifiedInside() == null || !state.getLastNotifiedInside().equals(inside)) {
            GeofenceEventType pendingEventType = inside ? GeofenceEventType.ENTER : GeofenceEventType.LEAVE;
            if (isMonitored(rule, pendingEventType)) {
                maybeEmitWithCooldown(rule, state, point, pendingEventType, inside);
            } else {
                state.setLastNotifiedInside(inside);
            }
        }
    }

    private void maybeEmitWithCooldown(GeofenceRuleEntity rule,
                                       GeofenceRuleStateEntity state,
                                       GpsPointEntity point,
                                       GeofenceEventType eventType,
                                       boolean insideStateAfterEvent) {
        if (!isCooldownElapsed(state.getLastNotifiedAt(), point.getTimestamp(), rule.getCooldownSeconds())) {
            return;
        }

        emitEvent(rule, point, eventType);
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

    private void emitEvent(GeofenceRuleEntity rule, GpsPointEntity point, GeofenceEventType eventType) {
        NotificationTemplateEntity template = resolveTemplate(rule, eventType);

        UserEntity subject = rule.getSubjectUser();
        String subjectName = subject.getFullName() != null && !subject.getFullName().isBlank()
                ? subject.getFullName()
                : subject.getEmail();

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("subjectName", subjectName);
        placeholders.put("eventType", eventType.name());
        placeholders.put("geofenceName", rule.getName());
        placeholders.put("timestamp", point.getTimestamp().toString());
        placeholders.put("lat", String.valueOf(point.getCoordinates().getY()));
        placeholders.put("lon", String.valueOf(point.getCoordinates().getX()));

        String defaultTitle = "Geofence " + eventType.name() + ": " + rule.getName();
        String eventVerb = eventType == GeofenceEventType.ENTER ? "entered" : "left";
        String defaultMessage = subjectName + " " + eventVerb + " geofence '" + rule.getName() + "' at " + point.getTimestamp();

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
                .subjectUser(rule.getSubjectUser())
                .rule(rule)
                .template(template)
                .point(point)
                .eventType(eventType)
                .occurredAt(point.getTimestamp())
                .title(title)
                .message(message)
                .deliveryStatus(deliveryStatus)
                .deliveryAttempts(0)
                .createdAt(Instant.now())
                .build();

        if (deliveryStatus == GeofenceDeliveryStatus.SKIPPED) {
            event.setDeliveredAt(Instant.now());
            event.setLastDeliveryError("No enabled template configured for external delivery");
        }

        eventRepository.persist(event);
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
}
