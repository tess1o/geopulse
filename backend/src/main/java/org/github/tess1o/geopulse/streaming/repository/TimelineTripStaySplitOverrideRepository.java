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
        return find("id = ?1 and user.id = ?2 and migratedToTimelineEdits = false", id, userId)
                .firstResultOptional();
    }

    public Optional<TimelineTripStaySplitOverrideEntity> findByUserIdAndSourceTripAndStay(UUID userId,
                                                                                          Instant sourceTripTimestamp,
                                                                                          Instant stayStartTime,
                                                                                          Instant stayEndTime) {
        return find("user.id = ?1 and sourceTripTimestamp = ?2 and stayStartTime = ?3 and stayEndTime = ?4 "
                        + "and migratedToTimelineEdits = false",
                userId, sourceTripTimestamp, stayStartTime, stayEndTime).firstResultOptional();
    }

    /**
     * The user's splits parent first: a split made on a leg of an already split trip has a shorter source trip than
     * its parent, so ordering by source duration makes the leg exist before the child split is matched. This is
     * independent of ids, which import assigns in file order (old exports were ordered by stay start time).
     */
    public List<TimelineTripStaySplitOverrideEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 and migratedToTimelineEdits = false "
                + "order by sourceTripDurationSeconds desc, id", userId).list();
    }

    public List<TimelineTripStaySplitOverrideEntity> findByUserIdAndStayIds(UUID userId, List<Long> stayIds) {
        if (stayIds == null || stayIds.isEmpty()) {
            return List.of();
        }
        return find("user.id = ?1 and stay.id in ?2 and migratedToTimelineEdits = false order by stayStartTime",
                userId, stayIds).list();
    }
}
