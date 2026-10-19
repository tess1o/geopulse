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

        Filter filter = filter(actionType, targetType, adminUserId, from, to);
        return find(filter.query(), Sort.descending("timestamp"), filter.params())
                .page(Page.of(page, size))
                .list();
    }

    public long countWithFilters(
            ActionType actionType,
            TargetType targetType,
            UUID adminUserId,
            Instant from,
            Instant to) {

        Filter filter = filter(actionType, targetType, adminUserId, from, to);
        return count(filter.query(), filter.params());
    }

    /** A Panache query with positional parameters; null criteria are left out. */
    private record Filter(String query, Object[] params) {
    }

    private static Filter filter(ActionType actionType, TargetType targetType, UUID adminUserId,
                                 Instant from, Instant to) {
        StringBuilder query = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        addCondition(query, params, "actionType =", actionType);
        addCondition(query, params, "targetType =", targetType);
        addCondition(query, params, "adminUserId =", adminUserId);
        addCondition(query, params, "timestamp >=", from);
        addCondition(query, params, "timestamp <=", to);
        return new Filter(query.toString(), params.toArray());
    }

    private static void addCondition(StringBuilder query, List<Object> params, String condition, Object value) {
        if (value != null) {
            params.add(value);
            query.append(" and ").append(condition).append(" ?").append(params.size());
        }
    }
}
