package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripStaySplitOverrideEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineTripStaySplitOverrideRepository implements PanacheRepository<TimelineTripStaySplitOverrideEntity> {

    public Optional<TimelineTripStaySplitOverrideEntity> findByIdAndUserId(Long id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public Optional<TimelineTripStaySplitOverrideEntity> findByUserIdAndStayId(UUID userId, Long stayId) {
        return find("user.id = ?1 and stay.id = ?2", userId, stayId).firstResultOptional();
    }

    public Optional<TimelineTripStaySplitOverrideEntity> findByUserIdAndSourceTripAndStay(UUID userId,
                                                                                          Instant sourceTripTimestamp,
                                                                                          Instant stayStartTime,
                                                                                          Instant stayEndTime) {
        return find("user.id = ?1 and sourceTripTimestamp = ?2 and stayStartTime = ?3 and stayEndTime = ?4",
                userId, sourceTripTimestamp, stayStartTime, stayEndTime).firstResultOptional();
    }

    public List<TimelineTripStaySplitOverrideEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 order by stayStartTime", userId).list();
    }

    public List<TimelineTripStaySplitOverrideEntity> findByUserIdAndStayIds(UUID userId, List<Long> stayIds) {
        if (stayIds == null || stayIds.isEmpty()) {
            return List.of();
        }
        return find("user.id = ?1 and stay.id in ?2 order by stayStartTime", userId, stayIds).list();
    }
}
