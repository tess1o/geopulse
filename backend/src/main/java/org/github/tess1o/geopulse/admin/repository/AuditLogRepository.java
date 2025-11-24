package org.github.tess1o.geopulse.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.AuditLogEntity;
import org.github.tess1o.geopulse.admin.model.TargetType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for audit log entries.
 */
@ApplicationScoped
public class AuditLogRepository implements PanacheRepositoryBase<AuditLogEntity, Long> {

    public List<AuditLogEntity> findWithFilters(
            ActionType actionType,
            TargetType targetType,
            UUID adminUserId,
            Instant from,
            Instant to,
            int page,
            int size) {

        StringBuilder query = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        int paramIndex = 1;

        if (actionType != null) {
            query.append(" and actionType = ?").append(paramIndex++);
            params.add(actionType);
        }
        if (targetType != null) {
            query.append(" and targetType = ?").append(paramIndex++);
            params.add(targetType);
        }
        if (adminUserId != null) {
            query.append(" and adminUserId = ?").append(paramIndex++);
            params.add(adminUserId);
        }
        if (from != null) {
            query.append(" and timestamp >= ?").append(paramIndex++);
            params.add(from);
        }
        if (to != null) {
            query.append(" and timestamp <= ?").append(paramIndex++);
            params.add(to);
        }

        return find(query.toString(), Sort.descending("timestamp"), params.toArray())
                .page(Page.of(page, size))
                .list();
    }

    public long countWithFilters(
            ActionType actionType,
            TargetType targetType,
            UUID adminUserId,
            Instant from,
            Instant to) {

        StringBuilder query = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        int paramIndex = 1;

        if (actionType != null) {
            query.append(" and actionType = ?").append(paramIndex++);
            params.add(actionType);
        }
        if (targetType != null) {
            query.append(" and targetType = ?").append(paramIndex++);
            params.add(targetType);
        }
        if (adminUserId != null) {
            query.append(" and adminUserId = ?").append(paramIndex++);
            params.add(adminUserId);
        }
        if (from != null) {
            query.append(" and timestamp >= ?").append(paramIndex++);
            params.add(from);
        }
        if (to != null) {
            query.append(" and timestamp <= ?").append(paramIndex++);
            params.add(to);
        }

        return count(query.toString(), params.toArray());
    }

    public List<AuditLogEntity> findByAdminUserId(UUID adminUserId, int page, int size) {
        return find("adminUserId", Sort.descending("timestamp"), adminUserId)
                .page(Page.of(page, size))
                .list();
    }

    public List<AuditLogEntity> findByTargetTypeAndId(TargetType targetType, String targetId) {
        return find("targetType = ?1 and targetId = ?2", Sort.descending("timestamp"), targetType, targetId)
                .list();
    }
}
