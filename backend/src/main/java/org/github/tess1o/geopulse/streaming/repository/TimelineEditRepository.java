package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineEditRepository implements PanacheRepository<TimelineEditEntity> {
    public List<TimelineEditEntity> findByUser(UUID userId) {
        return find("user.id = ?1 order by seq", userId).list();
    }

    /** Every correction of the user with its effects and replacement loaded, for the history list. */
    public List<TimelineEditEntity> findByUserForHistory(UUID userId) {
        return getEntityManager().createQuery("""
                        select distinct e from TimelineEditEntity e
                        left join fetch e.effects
                        left join fetch e.supersededBy
                        where e.user.id = :userId
                        order by e.seq
                        """, TimelineEditEntity.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public Optional<TimelineEditEntity> findByIdAndUser(Long id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public List<TimelineEditEntity> findActiveOverlapping(UUID userId, Instant from, Instant to) {
        return find("user.id = ?1 and status = ?2 and affectedFrom < ?4 and affectedTo > ?3 order by seq",
                userId, TimelineEditStatus.ACTIVE, from, to).list();
    }

    /** Active corrections whose own anchor (not their wider affected range) overlaps the interval. */
    public List<TimelineEditEntity> findActiveAnchorOverlapping(UUID userId, Instant from, Instant to) {
        return find("user.id = ?1 and status = ?2 and anchorStart < ?4 and anchorEnd > ?3 order by seq",
                userId, TimelineEditStatus.ACTIVE, from, to).list();
    }

    public List<TimelineEditEntity> findGroup(UUID userId, UUID groupId) {
        return find("user.id = ?1 and groupId = ?2 order by seq", userId, groupId).list();
    }

    public long nextSeq(UUID userId) {
        Long maximum = getEntityManager().createQuery(
                        "select max(e.seq) from TimelineEditEntity e where e.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
        return maximum == null ? 1 : maximum + 1;
    }
}
