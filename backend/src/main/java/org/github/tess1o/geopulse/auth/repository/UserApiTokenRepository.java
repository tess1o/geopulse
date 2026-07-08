package org.github.tess1o.geopulse.auth.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.model.ApiTokenStatus;
import org.github.tess1o.geopulse.auth.model.UserApiTokenEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserApiTokenRepository implements PanacheRepositoryBase<UserApiTokenEntity, UUID> {

    public Optional<UserApiTokenEntity> findByHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    public List<UserApiTokenEntity> findByUserId(UUID userId) {
        return find("user.id = ?1", Sort.descending("createdAt"), userId).list();
    }

    public List<UserApiTokenEntity> findForAdmin(UUID userId, ApiTokenStatus status, int page, int size) {
        String query = buildAdminQuery(userId, status);
        Object[] params = buildAdminParams(userId, status);
        return find(query, Sort.descending("createdAt"), params).page(Page.of(page, size)).list();
    }

    public long countForAdmin(UUID userId, ApiTokenStatus status) {
        return count(buildAdminQuery(userId, status), buildAdminParams(userId, status));
    }

    private String buildAdminQuery(UUID userId, ApiTokenStatus status) {
        StringBuilder query = new StringBuilder("1 = 1");
        if (userId != null) {
            query.append(" AND user.id = ?1");
        }
        if (status != null) {
            int index = userId == null ? 1 : 2;
            switch (status) {
                case ACTIVE -> query.append(" AND revokedAt IS NULL AND (expiresAt IS NULL OR expiresAt > ?").append(index).append(")");
                case EXPIRED -> query.append(" AND revokedAt IS NULL AND expiresAt IS NOT NULL AND expiresAt <= ?").append(index);
                case REVOKED -> query.append(" AND revokedAt IS NOT NULL");
            }
        }
        return query.toString();
    }

    private Object[] buildAdminParams(UUID userId, ApiTokenStatus status) {
        if (userId == null && status == null) {
            return new Object[0];
        }
        if (status == null) {
            return new Object[]{userId};
        }
        if (status == ApiTokenStatus.REVOKED) {
            return userId == null ? new Object[0] : new Object[]{userId};
        }
        Instant now = Instant.now();
        return userId == null ? new Object[]{now} : new Object[]{userId, now};
    }
}
