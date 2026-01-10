package org.github.tess1o.geopulse.periods.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PeriodTagRepository implements PanacheRepository<PeriodTagEntity> {

    private final EntityManager em;

    @Inject
    public PeriodTagRepository(EntityManager em) {
        this.em = em;
    }

    public List<PeriodTagEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY startTime DESC", userId);
    }

    public Optional<PeriodTagEntity> findByIdAndUserId(Long id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public Optional<PeriodTagEntity> findActiveByUserId(UUID userId) {
        return find("user.id = ?1 AND isActive = true", userId).firstResultOptional();
    }

    public List<PeriodTagEntity> findByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return find("user.id = ?1 AND startTime <= ?2 AND endTime >= ?3 ORDER BY startTime",
                userId, endTime, startTime).list();
    }

    public List<PeriodTagEntity> findOverlapping(UUID userId, Instant startTime, Instant endTime, Long excludeId) {
        if (excludeId != null) {
            return find(
                    "user.id = ?1 AND id != ?2 AND ((startTime <= ?3 AND endTime >= ?3) OR (startTime <= ?4 AND endTime >= ?4) OR (startTime >= ?3 AND endTime <= ?4))",
                    userId, excludeId, startTime, endTime
            ).list();
        } else {
            return find(
                    "user.id = ?1 AND ((startTime <= ?2 AND endTime >= ?2) OR (startTime <= ?3 AND endTime >= ?3) OR (startTime >= ?2 AND endTime <= ?3))",
                    userId, startTime, endTime
            ).list();
        }
    }
}
