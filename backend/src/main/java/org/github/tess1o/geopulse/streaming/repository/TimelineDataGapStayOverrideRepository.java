package org.github.tess1o.geopulse.streaming.repository;

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
        return find("id = ?1 and user.id = ?2", overrideId, userId).firstResultOptional();
    }

    public Optional<TimelineDataGapStayOverrideEntity> findByUserIdAndSourceGap(UUID userId, Instant sourceStart, Instant sourceEnd) {
        return find("user.id = ?1 and sourceGapStartTime = ?2 and sourceGapEndTime = ?3",
                userId, sourceStart, sourceEnd).firstResultOptional();
    }

    public List<TimelineDataGapStayOverrideEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 order by sourceGapStartTime", userId).list();
    }

    public List<TimelineDataGapStayOverrideEntity> findByUserIdAndStayIds(UUID userId, List<Long> stayIds) {
        if (stayIds == null || stayIds.isEmpty()) {
            return List.of();
        }
        return find("user.id = ?1 and stay.id in ?2 order by sourceGapStartTime", userId, stayIds).list();
    }
}
