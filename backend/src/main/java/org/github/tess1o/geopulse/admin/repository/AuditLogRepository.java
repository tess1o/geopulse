package org.github.tess1o.geopulse.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.AuditLogEntity;
import org.github.tess1o.geopulse.admin.model.TargetType;

import java.time.Instant;
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

        if (actionType != null) {
            query.append(" and actionType = ?1");
        }
        if (targetType != null) {
            query.append(" and targetType = ?2");
        }
        if (adminUserId != null) {
            query.append(" and adminUserId = ?3");
        }
        if (from != null) {
            query.append(" and timestamp >= ?4");
        }
        if (to != null) {
            query.append(" and timestamp <= ?5");
        }

        return find(query.toString(), Sort.descending("timestamp"),
                actionType, targetType, adminUserId, from, to)
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

        if (actionType != null) {
            query.append(" and actionType = ?1");
        }
        if (targetType != null) {
            query.append(" and targetType = ?2");
        }
        if (adminUserId != null) {
            query.append(" and adminUserId = ?3");
        }
        if (from != null) {
            query.append(" and timestamp >= ?4");
        }
        if (to != null) {
            query.append(" and timestamp <= ?5");
        }

        return count(query.toString(), actionType, targetType, adminUserId, from, to);
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
