package org.github.tess1o.geopulse.streaming.repository;

import org.github.tess1o.geopulse.streaming.model.dto.TimelineOverrideIds;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapStayOverrideEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineDataGapStayOverrideRepository implements PanacheRepository<TimelineDataGapStayOverrideEntity> {

    public Optional<TimelineDataGapStayOverrideEntity> findByIdAndUserId(Long overrideId, UUID userId) {
        return find("id = ?1 and user.id = ?2 and migratedToTimelineEdits = false", overrideId, userId)
                .firstResultOptional();
    }

    public Optional<TimelineDataGapStayOverrideEntity> findByUserIdAndSourceGap(UUID userId, Instant sourceStart, Instant sourceEnd) {
        return find("user.id = ?1 and sourceGapStartTime = ?2 and sourceGapEndTime = ?3 "
                        + "and migratedToTimelineEdits = false",
                userId, sourceStart, sourceEnd).firstResultOptional();
    }

    public List<TimelineDataGapStayOverrideEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 and migratedToTimelineEdits = false order by sourceGapStartTime", userId).list();
    }

    public List<TimelineDataGapStayOverrideEntity> findByUserIdAndStayIds(UUID userId, List<Long> stayIds) {
        if (stayIds == null || stayIds.isEmpty()) {
            return List.of();
        }
        return find("user.id = ?1 and stay.id in ?2 and migratedToTimelineEdits = false order by sourceGapStartTime",
                userId, stayIds).list();
    }
    public List<TimelineOverrideIds> findIdsByUserIdAndStayIds(
            UUID userId, List<Long> stayIds) {
        if (stayIds == null || stayIds.isEmpty()) return List.of();
        return getEntityManager().createQuery("""
                select new org.github.tess1o.geopulse.streaming.model.dto.TimelineOverrideIds(id, stay.id)
                from TimelineDataGapStayOverrideEntity
                where user.id = :userId and stay.id in :stayIds and migratedToTimelineEdits = false
                order by sourceGapStartTime
                """, TimelineOverrideIds.class)
                .setParameter("userId", userId).setParameter("stayIds", stayIds).getResultList();
    }

}
