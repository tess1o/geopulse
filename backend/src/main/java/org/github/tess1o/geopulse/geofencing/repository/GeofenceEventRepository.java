package org.github.tess1o.geopulse.geofencing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventQueryDto;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GeofenceEventRepository implements PanacheRepository<GeofenceEventEntity> {

    public record GeofenceEventPageResult(List<GeofenceEventEntity> items, long totalCount) {
    }

    public GeofenceEventPageResult findPageByOwner(UUID ownerUserId, GeofenceEventQueryDto queryDto) {
        GeofenceEventQueryDto query = queryDto == null ? GeofenceEventQueryDto.builder().build() : queryDto;

        StringBuilder where = new StringBuilder("e.ownerUser.id = :ownerUserId");
        Map<String, Object> params = new HashMap<>();
        params.put("ownerUserId", ownerUserId);

        if (query.isUnreadOnly()) {
            where.append(" AND e.seenAt IS NULL");
        }

        if (query.getDateFrom() != null) {
            where.append(" AND e.occurredAt >= :dateFrom");
            params.put("dateFrom", query.getDateFrom());
        }

        if (query.getDateTo() != null) {
            where.append(" AND e.occurredAt <= :dateTo");
            params.put("dateTo", query.getDateTo());
        }

        if (query.getSubjectUserIds() != null && !query.getSubjectUserIds().isEmpty()) {
            where.append(" AND e.subjectUser.id IN :subjectUserIds");
            params.put("subjectUserIds", query.getSubjectUserIds());
        }

        if (query.getEventTypes() != null && !query.getEventTypes().isEmpty()) {
            where.append(" AND e.eventType IN :eventTypes");
            params.put("eventTypes", query.getEventTypes());
        }

        String orderBy = resolveSortField(query.getSortBy()) + " " + resolveSortDirection(query.getSortDir()) + ", e.id DESC";

        String select = "SELECT e FROM GeofenceEventEntity e " +
                "JOIN FETCH e.rule r " +
                "JOIN FETCH e.subjectUser s " +
                "LEFT JOIN FETCH e.point p " +
                "WHERE " + where +
                " ORDER BY " + orderBy;

        TypedQuery<GeofenceEventEntity> dataQuery = getEntityManager().createQuery(select, GeofenceEventEntity.class);
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult(Math.max(0, query.getPage()) * Math.max(1, query.getPageSize()));
        dataQuery.setMaxResults(Math.max(1, query.getPageSize()));

        String countSql = "SELECT COUNT(e.id) FROM GeofenceEventEntity e WHERE " + where;
        TypedQuery<Long> countQuery = getEntityManager().createQuery(countSql, Long.class);
        params.forEach(countQuery::setParameter);

        return new GeofenceEventPageResult(dataQuery.getResultList(), countQuery.getSingleResult());
    }

    public long countUnreadByOwner(UUID ownerUserId) {
        return count("ownerUser.id = ?1 AND seenAt IS NULL", ownerUserId);
    }

    public Optional<GeofenceEventEntity> findByIdAndOwner(Long eventId, UUID ownerUserId) {
        return find("id = ?1 AND ownerUser.id = ?2", eventId, ownerUserId).firstResultOptional();
    }

    public long markAllSeenByOwner(UUID ownerUserId, Instant seenAt) {
        return update("seenAt = ?1 WHERE ownerUser.id = ?2 AND seenAt IS NULL", seenAt, ownerUserId);
    }

    public long clearTemplateReference(UUID ownerUserId, Long templateId) {
        return update("template = null WHERE ownerUser.id = ?1 AND template.id = ?2", ownerUserId, templateId);
    }

    public List<GeofenceEventEntity> findPendingForDelivery(int limit, int maxAttempts) {
        return find("deliveryStatus = ?1 AND deliveryAttempts < ?2 ORDER BY createdAt ASC",
                GeofenceDeliveryStatus.PENDING,
                maxAttempts)
                .page(0, Math.max(1, limit))
                .list();
    }

    public long deleteOlderThan(Instant cutoff) {
        return delete("occurredAt < ?1", cutoff);
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "e.occurredAt";
        }

        return switch (sortBy.trim().toLowerCase()) {
            case "subject", "subjectdisplayname" -> "e.subjectDisplayName";
            case "event", "eventtype" -> "e.eventType";
            case "time", "occurredat" -> "e.occurredAt";
            default -> "e.occurredAt";
        };
    }

    private String resolveSortDirection(String sortDir) {
        return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
    }
}
