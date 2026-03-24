package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventDto;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventPageDto;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventQueryDto;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.notifications.service.GeofenceNotificationProjectionService;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class GeofenceEventService {

    private static final int MAX_PAGE_SIZE = 200;

    private final GeofenceEventRepository eventRepository;
    private final GeofenceNotificationProjectionService notificationProjectionService;

    @Inject
    public GeofenceEventService(GeofenceEventRepository eventRepository,
                                GeofenceNotificationProjectionService notificationProjectionService) {
        this.eventRepository = eventRepository;
        this.notificationProjectionService = notificationProjectionService;
    }

    public GeofenceEventPageDto listEventsPage(UUID ownerUserId, GeofenceEventQueryDto queryDto) {
        GeofenceEventQueryDto query = normalizeQuery(queryDto);
        GeofenceEventRepository.GeofenceEventPageResult pageResult = eventRepository.findPageByOwner(ownerUserId, query);

        return GeofenceEventPageDto.builder()
                .items(pageResult.items().stream().map(this::toDto).toList())
                .totalCount(pageResult.totalCount())
                .page(query.getPage())
                .pageSize(query.getPageSize())
                .build();
    }

    public long countUnread(UUID ownerUserId) {
        return eventRepository.countUnreadByOwner(ownerUserId);
    }

    @Transactional
    public GeofenceEventDto markSeen(UUID ownerUserId, Long eventId) {
        GeofenceEventEntity event = eventRepository.findByIdAndOwner(eventId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence event not found"));

        Instant seenAt = event.getSeenAt() == null ? Instant.now() : event.getSeenAt();
        if (event.getSeenAt() == null) {
            event.setSeenAt(seenAt);
        }
        notificationProjectionService.syncSeen(ownerUserId, eventId, seenAt);
        return toDto(event);
    }

    @Transactional
    public long markAllSeen(UUID ownerUserId) {
        Instant seenAt = Instant.now();
        long updatedCount = eventRepository.markAllSeenByOwner(ownerUserId, seenAt);
        notificationProjectionService.syncAllSeen(ownerUserId, seenAt);
        return updatedCount;
    }

    public GeofenceEventDto toDto(GeofenceEventEntity entity) {
        UserEntity subject = entity.getSubjectUser();
        String subjectDisplayName = entity.getSubjectDisplayName();
        if (subjectDisplayName == null || subjectDisplayName.isBlank()) {
            subjectDisplayName = subject.getFullName() != null && !subject.getFullName().isBlank()
                    ? subject.getFullName()
                    : subject.getEmail();
        }

        Double pointLat = null;
        Double pointLon = null;
        Long pointId = null;
        if (entity.getPoint() != null) {
            pointId = entity.getPoint().getId();
            if (entity.getPoint().getCoordinates() != null) {
                pointLat = entity.getPoint().getCoordinates().getY();
                pointLon = entity.getPoint().getCoordinates().getX();
            }
        }

        return GeofenceEventDto.builder()
                .id(entity.getId())
                .ruleId(entity.getRule().getId())
                .ruleName(entity.getRule().getName())
                .subjectUserId(subject.getId())
                .subjectDisplayName(subjectDisplayName)
                .eventType(entity.getEventType())
                .occurredAt(entity.getOccurredAt())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .deliveryStatus(entity.getDeliveryStatus())
                .deliveryAttempts(entity.getDeliveryAttempts())
                .lastDeliveryError(entity.getLastDeliveryError())
                .deliveredAt(entity.getDeliveredAt())
                .pointId(pointId)
                .pointLat(pointLat)
                .pointLon(pointLon)
                .seenAt(entity.getSeenAt())
                .seen(entity.getSeenAt() != null)
                .build();
    }

    private GeofenceEventQueryDto normalizeQuery(GeofenceEventQueryDto queryDto) {
        GeofenceEventQueryDto source = queryDto == null ? GeofenceEventQueryDto.builder().build() : queryDto;

        int page = Math.max(source.getPage(), 0);
        int pageSize = Math.min(Math.max(source.getPageSize(), 1), MAX_PAGE_SIZE);

        if (source.getDateFrom() != null && source.getDateTo() != null && source.getDateFrom().isAfter(source.getDateTo())) {
            throw new IllegalArgumentException("dateFrom must be before or equal to dateTo");
        }

        List<UUID> subjectUserIds = source.getSubjectUserIds() == null
                ? List.of()
                : source.getSubjectUserIds().stream().filter(value -> value != null).distinct().toList();

        List<GeofenceEventType> eventTypes = source.getEventTypes() == null
                ? List.of()
                : source.getEventTypes().stream().filter(value -> value != null).distinct().toList();

        return GeofenceEventQueryDto.builder()
                .page(page)
                .pageSize(pageSize)
                .sortBy(normalizeSortBy(source.getSortBy()))
                .sortDir("asc".equalsIgnoreCase(source.getSortDir()) ? "asc" : "desc")
                .unreadOnly(source.isUnreadOnly())
                .dateFrom(source.getDateFrom())
                .dateTo(source.getDateTo())
                .subjectUserIds(subjectUserIds)
                .eventTypes(eventTypes)
                .build();
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "occurredAt";
        }

        return switch (sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "subject", "subjectdisplayname" -> "subjectDisplayName";
            case "event", "eventtype" -> "eventType";
            case "time", "occurredat" -> "occurredAt";
            default -> throw new IllegalArgumentException(
                    "Unsupported sortBy value. Supported values: occurredAt, subjectDisplayName, eventType."
            );
        };
    }
}
