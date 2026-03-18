package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventDto;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GeofenceEventService {

    private final GeofenceEventRepository eventRepository;

    @Inject
    public GeofenceEventService(GeofenceEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<GeofenceEventDto> listEvents(UUID ownerUserId, int limit, boolean unreadOnly) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 200);
        return eventRepository.findByOwner(ownerUserId, normalizedLimit, unreadOnly)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public long countUnread(UUID ownerUserId) {
        return eventRepository.countUnreadByOwner(ownerUserId);
    }

    @Transactional
    public GeofenceEventDto markSeen(UUID ownerUserId, Long eventId) {
        GeofenceEventEntity event = eventRepository.findByIdAndOwner(eventId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence event not found"));

        if (event.getSeenAt() == null) {
            event.setSeenAt(Instant.now());
        }
        return toDto(event);
    }

    @Transactional
    public long markAllSeen(UUID ownerUserId) {
        return eventRepository.markAllSeenByOwner(ownerUserId, Instant.now());
    }

    public GeofenceEventDto toDto(GeofenceEventEntity entity) {
        UserEntity subject = entity.getSubjectUser();
        String subjectDisplayName = subject.getFullName() != null && !subject.getFullName().isBlank()
                ? subject.getFullName()
                : subject.getEmail();

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
                .seenAt(entity.getSeenAt())
                .seen(entity.getSeenAt() != null)
                .build();
    }
}
