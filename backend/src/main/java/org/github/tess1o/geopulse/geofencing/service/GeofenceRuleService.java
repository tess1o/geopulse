package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateGeofenceRuleRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceRuleDto;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceRuleSubjectDto;
import org.github.tess1o.geopulse.geofencing.model.dto.UpdateGeofenceRuleRequest;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectId;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class GeofenceRuleService {

    private final GeofenceRuleRepository ruleRepository;
    private final NotificationTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserFriendPermissionRepository permissionRepository;
    private final EntityManager entityManager;

    @Inject
    public GeofenceRuleService(GeofenceRuleRepository ruleRepository,
                               NotificationTemplateRepository templateRepository,
                               UserRepository userRepository,
                               FriendshipRepository friendshipRepository,
                               UserFriendPermissionRepository permissionRepository,
                               EntityManager entityManager) {
        this.ruleRepository = ruleRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.permissionRepository = permissionRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public List<GeofenceRuleDto> listRules(UUID ownerUserId) {
        return ruleRepository.findByOwner(ownerUserId).stream().map(this::toDto).toList();
    }

    @Transactional
    public GeofenceRuleDto createRule(UUID ownerUserId, CreateGeofenceRuleRequest request) {
        validateCoordinates(request.getNorthEastLat(), request.getNorthEastLon(), request.getSouthWestLat(), request.getSouthWestLon());
        validateEventSelection(request.getMonitorEnter(), request.getMonitorLeave());
        Set<UUID> subjectUserIds = normalizeSubjectUserIds(request.getSubjectUserIds(), true);
        subjectUserIds.forEach(subjectUserId -> validateSubjectAccess(ownerUserId, subjectUserId));

        NotificationTemplateEntity enterTemplate = resolveTemplate(ownerUserId, request.getEnterTemplateId());
        NotificationTemplateEntity leaveTemplate = resolveTemplate(ownerUserId, request.getLeaveTemplateId());

        GeofenceRuleEntity entity = GeofenceRuleEntity.builder()
                .ownerUser(entityManager.getReference(UserEntity.class, ownerUserId))
                .name(request.getName().trim())
                .northEastLat(request.getNorthEastLat())
                .northEastLon(request.getNorthEastLon())
                .southWestLat(request.getSouthWestLat())
                .southWestLon(request.getSouthWestLon())
                .monitorEnter(request.getMonitorEnter())
                .monitorLeave(request.getMonitorLeave())
                .cooldownSeconds(request.getCooldownSeconds())
                .enterTemplate(enterTemplate)
                .leaveTemplate(leaveTemplate)
                .status(GeofenceRuleStatus.ACTIVE)
                .build();

        applySubjectAssignments(entity, subjectUserIds);
        ruleRepository.persist(entity);
        return toDto(entity);
    }

    @Transactional
    public GeofenceRuleDto updateRule(UUID ownerUserId, Long ruleId, UpdateGeofenceRuleRequest request) {
        GeofenceRuleEntity entity = ruleRepository.findByIdAndOwner(ruleId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence rule not found"));

        double northEastLat = request.getNorthEastLat() != null ? request.getNorthEastLat() : entity.getNorthEastLat();
        double northEastLon = request.getNorthEastLon() != null ? request.getNorthEastLon() : entity.getNorthEastLon();
        double southWestLat = request.getSouthWestLat() != null ? request.getSouthWestLat() : entity.getSouthWestLat();
        double southWestLon = request.getSouthWestLon() != null ? request.getSouthWestLon() : entity.getSouthWestLon();

        validateCoordinates(northEastLat, northEastLon, southWestLat, southWestLon);

        boolean monitorEnter = request.getMonitorEnter() != null ? request.getMonitorEnter() : Boolean.TRUE.equals(entity.getMonitorEnter());
        boolean monitorLeave = request.getMonitorLeave() != null ? request.getMonitorLeave() : Boolean.TRUE.equals(entity.getMonitorLeave());
        validateEventSelection(monitorEnter, monitorLeave);

        if (request.getName() != null) {
            entity.setName(request.getName().trim());
        }

        if (request.getSubjectUserIds() != null) {
            Set<UUID> nextSubjectIds = normalizeSubjectUserIds(request.getSubjectUserIds(), true);
            Set<UUID> currentSubjectIds = entity.getSubjectAssignments().stream()
                    .map(assignment -> assignment.getSubjectUser().getId())
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);

            Set<UUID> addedSubjectIds = new LinkedHashSet<>(nextSubjectIds);
            addedSubjectIds.removeAll(currentSubjectIds);
            addedSubjectIds.forEach(subjectUserId -> validateSubjectAccess(ownerUserId, subjectUserId));

            applySubjectAssignments(entity, nextSubjectIds);
        }
        if (request.getNorthEastLat() != null) {
            entity.setNorthEastLat(request.getNorthEastLat());
        }
        if (request.getNorthEastLon() != null) {
            entity.setNorthEastLon(request.getNorthEastLon());
        }
        if (request.getSouthWestLat() != null) {
            entity.setSouthWestLat(request.getSouthWestLat());
        }
        if (request.getSouthWestLon() != null) {
            entity.setSouthWestLon(request.getSouthWestLon());
        }
        if (request.getMonitorEnter() != null) {
            entity.setMonitorEnter(request.getMonitorEnter());
        }
        if (request.getMonitorLeave() != null) {
            entity.setMonitorLeave(request.getMonitorLeave());
        }
        if (request.getCooldownSeconds() != null) {
            entity.setCooldownSeconds(request.getCooldownSeconds());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setEnterTemplate(resolveTemplate(ownerUserId, request.getEnterTemplateId()));
        entity.setLeaveTemplate(resolveTemplate(ownerUserId, request.getLeaveTemplateId()));

        return toDto(entity);
    }

    @Transactional
    public void deleteRule(UUID ownerUserId, Long ruleId) {
        GeofenceRuleEntity entity = ruleRepository.findByIdAndOwner(ruleId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence rule not found"));
        ruleRepository.delete(entity);
    }

    public GeofenceRuleDto toDto(GeofenceRuleEntity entity) {
        List<GeofenceRuleSubjectDto> subjects = entity.getSubjectAssignments().stream()
                .map(assignment -> {
                    UserEntity subject = assignment.getSubjectUser();
                    String subjectDisplayName = subject.getFullName() != null && !subject.getFullName().isBlank()
                            ? subject.getFullName()
                            : (subject.getEmail() != null ? subject.getEmail() : "Unknown subject");
                    return GeofenceRuleSubjectDto.builder()
                            .userId(subject.getId())
                            .displayName(subjectDisplayName)
                            .build();
                })
                .sorted((left, right) -> {
                    int byDisplayName = left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
                    if (byDisplayName != 0) {
                        return byDisplayName;
                    }
                    return left.getUserId().toString().compareTo(right.getUserId().toString());
                })
                .toList();

        return GeofenceRuleDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .ownerUserId(entity.getOwnerUser().getId())
                .subjects(subjects)
                .northEastLat(entity.getNorthEastLat())
                .northEastLon(entity.getNorthEastLon())
                .southWestLat(entity.getSouthWestLat())
                .southWestLon(entity.getSouthWestLon())
                .monitorEnter(entity.getMonitorEnter())
                .monitorLeave(entity.getMonitorLeave())
                .cooldownSeconds(entity.getCooldownSeconds())
                .enterTemplateId(entity.getEnterTemplate() != null ? entity.getEnterTemplate().getId() : null)
                .leaveTemplateId(entity.getLeaveTemplate() != null ? entity.getLeaveTemplate().getId() : null)
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void applySubjectAssignments(GeofenceRuleEntity entity, Set<UUID> subjectUserIds) {
        entity.getSubjectAssignments().removeIf(assignment -> !subjectUserIds.contains(assignment.getSubjectUser().getId()));

        Set<UUID> existingSubjectIds = entity.getSubjectAssignments().stream()
                .map(assignment -> assignment.getSubjectUser().getId())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        for (UUID subjectUserId : subjectUserIds) {
            if (existingSubjectIds.contains(subjectUserId)) {
                continue;
            }
            GeofenceRuleSubjectEntity assignment = GeofenceRuleSubjectEntity.builder()
                    .id(new GeofenceRuleSubjectId(entity.getId(), subjectUserId))
                    .rule(entity)
                    .subjectUser(entityManager.getReference(UserEntity.class, subjectUserId))
                    .build();
            entity.getSubjectAssignments().add(assignment);
        }
    }

    private Set<UUID> normalizeSubjectUserIds(List<UUID> subjectUserIds, boolean required) {
        if (subjectUserIds == null) {
            if (required) {
                throw new IllegalArgumentException("subjectUserIds is required");
            }
            return Set.of();
        }

        Set<UUID> normalized = subjectUserIds.stream()
                .filter(id -> id != null)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        if (required && normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one subject must be selected");
        }

        return normalized;
    }

    private NotificationTemplateEntity resolveTemplate(UUID ownerUserId, Long templateId) {
        if (templateId == null) {
            return null;
        }
        return templateRepository.findByIdAndUser(templateId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Notification template not found: " + templateId));
    }

    private void validateSubjectAccess(UUID ownerUserId, UUID subjectUserId) {
        if (subjectUserId == null) {
            throw new IllegalArgumentException("subjectUserId is required");
        }
        if (!userRepository.existsById(subjectUserId)) {
            throw new IllegalArgumentException("Subject user does not exist");
        }

        if (ownerUserId.equals(subjectUserId)) {
            return;
        }

        boolean areFriends = friendshipRepository.existsFriendship(ownerUserId, subjectUserId);
        if (!areFriends) {
            throw new IllegalArgumentException("Geofence subject must be yourself or an active friend");
        }

        boolean hasLivePermission = permissionRepository.hasLiveLocationPermission(subjectUserId, ownerUserId);
        if (!hasLivePermission) {
            throw new IllegalArgumentException("Friend has not granted live location sharing permission");
        }
    }

    private void validateCoordinates(double northEastLat, double northEastLon, double southWestLat, double southWestLon) {
        if (northEastLat < southWestLat) {
            throw new IllegalArgumentException("northEastLat must be greater than or equal to southWestLat");
        }
        if (northEastLon < southWestLon) {
            throw new IllegalArgumentException("northEastLon must be greater than or equal to southWestLon");
        }
    }

    private void validateEventSelection(Boolean monitorEnter, Boolean monitorLeave) {
        if (!Boolean.TRUE.equals(monitorEnter) && !Boolean.TRUE.equals(monitorLeave)) {
            throw new IllegalArgumentException("At least one event type must be enabled (enter or leave)");
        }
    }
}
