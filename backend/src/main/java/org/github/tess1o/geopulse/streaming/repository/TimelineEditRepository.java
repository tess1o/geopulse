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

    /** Active corrections in the range, with their place references loaded: a fold reads them for every correction. */
    public List<TimelineEditEntity> findActiveOverlapping(UUID userId, Instant from, Instant to) {
        return getEntityManager().createQuery("""
                        select e from TimelineEditEntity e
                        left join fetch e.placeFavorite
                        left join fetch e.placeGeocoding
                        where e.user.id = :userId and e.status = :status
                          and e.affectedFrom < :to and e.affectedTo > :from
                        order by e.seq
                        """, TimelineEditEntity.class)
                .setParameter("userId", userId).setParameter("status", TimelineEditStatus.ACTIVE)
                .setParameter("from", from).setParameter("to", to)
                .getResultList();
    }

    /** The latest affected end of the user's active corrections of these kinds, or null. */
    public Instant maxActiveAffectedTo(UUID userId, List<org.github.tess1o.geopulse.streaming.edits.TimelineEditKind> kinds) {
        return getEntityManager().createQuery("""
                        select max(e.affectedTo) from TimelineEditEntity e
                        where e.user.id = :userId and e.status = :status and e.kind in :kinds
                        """, Instant.class)
                .setParameter("userId", userId).setParameter("status", TimelineEditStatus.ACTIVE)
                .setParameter("kinds", kinds)
                .getSingleResult();
    }

    /** Of these corrections, the ids whose affected range starts at or after {@code from}. */
    public List<Long> findIdsAffectedFrom(java.util.Collection<Long> ids, Instant from) {
        if (ids.isEmpty()) return List.of();
        return getEntityManager().createQuery(
                        "select e.id from TimelineEditEntity e where e.id in :ids and e.affectedFrom >= :from", Long.class)
                .setParameter("ids", ids).setParameter("from", from)
                .getResultList();
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
