package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.geofencing.model.entity.*;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.timelinelabels.model.entity.TimelineLabelEntity;
import org.github.tess1o.geopulse.trips.model.entity.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.IMPORT_JSON;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.requireImportingUser;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.defaultInstant;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.parseEnum;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.emptyIfNull;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.shouldSkipDueToDateFilter;

/**
 * GeoPulse import of trip workspaces (plans and collaborators), notification templates and geofence rules.
 */
@ApplicationScoped
@Slf4j
public class GeoPulsePlanningImporter {

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Transactional
    public void importNotificationTemplatesData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        NotificationTemplatesDataDto data = IMPORT_JSON.readValue(content, NotificationTemplatesDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);

        List<NotificationTemplatesDataDto.NotificationTemplateDto> templates = emptyIfNull(data.getTemplates());
        for (NotificationTemplatesDataDto.NotificationTemplateDto dto : templates) {
            if (dto.getName() == null) {
                continue;
            }
            NotificationTemplateEntity entity = entityManager.createQuery("""
                            SELECT template FROM NotificationTemplateEntity template
                            WHERE template.user.id = :userId AND template.name = :name
                            """, NotificationTemplateEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("name", dto.getName())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(NotificationTemplateEntity::new);

            entity.setUser(user);
            entity.setName(dto.getName());
            entity.setDestination(dto.getDestination());
            entity.setExternalRoutingMode(parseEnum(AppriseExternalRoutingMode.class, dto.getExternalRoutingMode(), AppriseExternalRoutingMode.URLS));
            entity.setAppriseConfigKey(dto.getAppriseConfigKey());
            entity.setAppriseTag(dto.getAppriseTag());
            entity.setTitleTemplate(dto.getTitleTemplate());
            entity.setBodyTemplate(dto.getBodyTemplate());
            entity.setDefaultForEnter(Boolean.TRUE.equals(dto.getDefaultForEnter()));
            entity.setDefaultForLeave(Boolean.TRUE.equals(dto.getDefaultForLeave()));
            entity.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            entity.setSendInApp(dto.getSendInApp() == null || dto.getSendInApp());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            entityManager.flush();

            if (dto.getId() != null) {
                referenceMaps.notificationTemplateIds.put(dto.getId(), entity.getId());
            }
        }
        log.info("Imported/updated {} notification templates for user {}", templates.size(), job.getUserId());
    }

    @Transactional
    public void importTripWorkspaceData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        TripWorkspaceDataDto data = IMPORT_JSON.readValue(content, TripWorkspaceDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);

        List<TripWorkspaceDataDto.TripDto> trips = emptyIfNull(data.getTrips());
        for (TripWorkspaceDataDto.TripDto dto : trips) {
            if (dto.getName() == null || (dto.getStartTime() != null && shouldSkipDueToDateFilter(dto.getStartTime(), job))) {
                continue;
            }
            TripEntity entity = findExistingTrip(job.getUserId(), dto)
                    .orElseGet(TripEntity::new);

            entity.setUser(user);
            Long mappedTimelineLabelId = dto.getTimelineLabelId() == null ? null : referenceMaps.timelineLabelIds.get(dto.getTimelineLabelId());
            entity.setTimelineLabel(mappedTimelineLabelId == null ? null : entityManager.getReference(TimelineLabelEntity.class, mappedTimelineLabelId));
            entity.setName(dto.getName());
            entity.setStartTime(dto.getStartTime());
            entity.setEndTime(dto.getEndTime());
            entity.setStatus(parseEnum(TripStatus.class, dto.getStatus(), TripStatus.UPCOMING));
            entity.setColor(dto.getColor());
            entity.setNotes(dto.getNotes());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            entityManager.flush();

            if (dto.getId() != null) {
                referenceMaps.tripIds.put(dto.getId(), entity.getId());
            }
            importTripPlanItems(entity, dto.getPlanItems());
            importTripCollaborators(entity, dto.getCollaborators());
        }
        log.info("Imported/updated {} trip workspace records for user {}", trips.size(), job.getUserId());
    }

    private void importTripPlanItems(TripEntity trip, List<TripWorkspaceDataDto.TripPlanItemDto> items) {
        if (items == null) {
            return;
        }
        // A plan can hold the same place more than once (back at the hotel each night), so an
        // existing item is matched at most once per import.
        Set<Long> matchedItemIds = new HashSet<>();
        for (TripWorkspaceDataDto.TripPlanItemDto dto : items) {
            // Matched on title and location rather than position: stops are reordered and moved
            // between days by drag-and-drop, so re-importing an older export must still find them.
            TripPlanItemEntity entity = entityManager.createQuery("""
                            SELECT item FROM TripPlanItemEntity item
                            WHERE item.trip.id = :tripId AND item.title = :title
                            ORDER BY item.id
                            """, TripPlanItemEntity.class)
                    .setParameter("tripId", trip.getId())
                    .setParameter("title", dto.getTitle())
                    .getResultStream()
                    .filter(existing -> !matchedItemIds.contains(existing.getId()))
                    .filter(existing -> sameCoordinate(existing.getLatitude(), dto.getLatitude())
                            && sameCoordinate(existing.getLongitude(), dto.getLongitude()))
                    .findFirst()
                    .orElseGet(TripPlanItemEntity::new);
            if (entity.getId() != null) {
                matchedItemIds.add(entity.getId());
            }
            entity.setTrip(trip);
            entity.setTitle(dto.getTitle());
            entity.setNotes(dto.getNotes());
            entity.setLatitude(dto.getLatitude());
            entity.setLongitude(dto.getLongitude());
            entity.setPlannedDay(dto.getPlannedDay());
            entity.setPriority(parseEnum(TripPlanItemPriority.class, dto.getPriority(), TripPlanItemPriority.OPTIONAL));
            entity.setTravelMode(parseEnum(TripPlanItemTravelMode.class, dto.getTravelMode(), null));
            entity.setOrderIndex(dto.getOrderIndex() == null ? 0 : dto.getOrderIndex());
            entity.setIsVisited(Boolean.TRUE.equals(dto.getVisited()));
            entity.setVisitConfidence(dto.getVisitConfidence());
            entity.setVisitSource(parseEnum(TripPlanItemVisitSource.class, dto.getVisitSource(), null));
            entity.setVisitedAt(dto.getVisitedAt());
            entity.setManualOverrideState(parseEnum(TripPlanItemOverrideState.class, dto.getManualOverrideState(), null));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
        }
    }

    private static boolean sameCoordinate(Double a, Double b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return Math.abs(a - b) < 1e-6;
    }

    private void importTripCollaborators(TripEntity trip, List<TripWorkspaceDataDto.TripCollaboratorDto> collaborators) {
        if (collaborators == null) {
            return;
        }
        for (TripWorkspaceDataDto.TripCollaboratorDto dto : collaborators) {
            if (dto.getEmail() == null) {
                continue;
            }
            Optional<UserEntity> collaboratorUser = userRepository.findByEmailIgnoreCase(dto.getEmail());
            if (collaboratorUser.isEmpty() || collaboratorUser.get().getId().equals(trip.getUser().getId())) {
                continue;
            }
            boolean exists = entityManager.createQuery("""
                            SELECT COUNT(collaborator) FROM TripCollaboratorEntity collaborator
                            WHERE collaborator.trip.id = :tripId AND collaborator.collaborator.id = :collaboratorId
                            """, Long.class)
                    .setParameter("tripId", trip.getId())
                    .setParameter("collaboratorId", collaboratorUser.get().getId())
                    .getSingleResult() > 0;
            if (!exists) {
                TripCollaboratorEntity entity = TripCollaboratorEntity.builder()
                        .trip(trip)
                        .collaborator(collaboratorUser.get())
                        .accessRole(parseEnum(TripCollaboratorAccessRole.class, dto.getAccessRole(), TripCollaboratorAccessRole.VIEW))
                        .createdAt(defaultInstant(dto.getCreatedAt()))
                        .updatedAt(Instant.now())
                        .build();
                entityManager.persist(entity);
            }
        }
    }

    private Optional<TripEntity> findExistingTrip(UUID userId, TripWorkspaceDataDto.TripDto dto) {
        if (dto.getStartTime() == null) {
            return entityManager.createQuery("""
                            SELECT trip FROM TripEntity trip
                            WHERE trip.user.id = :userId
                              AND trip.name = :name
                              AND trip.startTime IS NULL
                            """, TripEntity.class)
                    .setParameter("userId", userId)
                    .setParameter("name", dto.getName())
                    .getResultStream()
                    .findFirst();
        }
        return entityManager.createQuery("""
                        SELECT trip FROM TripEntity trip
                        WHERE trip.user.id = :userId
                          AND trip.name = :name
                          AND trip.startTime = :startTime
                        """, TripEntity.class)
                .setParameter("userId", userId)
                .setParameter("name", dto.getName())
                .setParameter("startTime", dto.getStartTime())
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public void importGeofencingData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        GeofencingDataDto data = IMPORT_JSON.readValue(content, GeofencingDataDto.class);
        UserEntity owner = requireImportingUser(userRepository, job);

        List<GeofencingDataDto.GeofenceRuleDto> rules = emptyIfNull(data.getRules());
        for (GeofencingDataDto.GeofenceRuleDto dto : rules) {
            if (dto.getName() == null) {
                continue;
            }
            GeofenceRuleEntity entity = entityManager.createQuery("""
                            SELECT rule FROM GeofenceRuleEntity rule
                            WHERE rule.ownerUser.id = :userId AND rule.name = :name
                            """, GeofenceRuleEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("name", dto.getName())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(GeofenceRuleEntity::new);
            entity.setOwnerUser(owner);
            entity.setName(dto.getName());
            entity.setNorthEastLat(dto.getNorthEastLat());
            entity.setNorthEastLon(dto.getNorthEastLon());
            entity.setSouthWestLat(dto.getSouthWestLat());
            entity.setSouthWestLon(dto.getSouthWestLon());
            entity.setMonitorEnter(dto.getMonitorEnter() == null || dto.getMonitorEnter());
            entity.setMonitorLeave(dto.getMonitorLeave() == null || dto.getMonitorLeave());
            entity.setCooldownSeconds(dto.getCooldownSeconds() == null ? 120 : dto.getCooldownSeconds());
            entity.setEnterTemplate(resolveTemplate(dto.getEnterTemplateId(), referenceMaps));
            entity.setLeaveTemplate(resolveTemplate(dto.getLeaveTemplateId(), referenceMaps));
            entity.setStatus(parseEnum(GeofenceRuleStatus.class, dto.getStatus(), GeofenceRuleStatus.ACTIVE));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            entityManager.flush();

            importGeofenceSubjects(entity, dto.getSubjects());
        }
        log.info("Imported/updated {} geofence rules for user {}", rules.size(), job.getUserId());
    }

    private NotificationTemplateEntity resolveTemplate(Long oldTemplateId, ImportReferenceMaps referenceMaps) {
        if (oldTemplateId == null) {
            return null;
        }
        Long mappedId = referenceMaps.notificationTemplateIds.get(oldTemplateId);
        return mappedId == null ? null : entityManager.getReference(NotificationTemplateEntity.class, mappedId);
    }

    private void importGeofenceSubjects(GeofenceRuleEntity rule, List<GeofencingDataDto.SubjectDto> subjects) {
        if (subjects == null) {
            return;
        }
        for (GeofencingDataDto.SubjectDto dto : subjects) {
            UserEntity subject = dto.getEmail() == null
                    ? null
                    : userRepository.findByEmailIgnoreCase(dto.getEmail()).orElse(null);
            if (subject == null) {
                continue;
            }
            boolean exists = entityManager.createQuery("""
                            SELECT COUNT(subject) FROM GeofenceRuleSubjectEntity subject
                            WHERE subject.rule.id = :ruleId AND subject.subjectUser.id = :subjectUserId
                            """, Long.class)
                    .setParameter("ruleId", rule.getId())
                    .setParameter("subjectUserId", subject.getId())
                    .getSingleResult() > 0;
            if (!exists) {
                GeofenceRuleSubjectEntity entity = GeofenceRuleSubjectEntity.builder()
                        .id(new GeofenceRuleSubjectId(rule.getId(), subject.getId()))
                        .rule(rule)
                        .subjectUser(subject)
                        .createdAt(defaultInstant(dto.getCreatedAt()))
                        .build();
                entityManager.persist(entity);
            }
        }
    }
}
