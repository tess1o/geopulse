package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineTripMovementOverrideRepository implements PanacheRepository<TimelineTripMovementOverrideEntity> {

    public Optional<TimelineTripMovementOverrideEntity> findByUserIdAndTripId(UUID userId, Long tripId) {
        return find("user.id = ?1 and trip.id = ?2", userId, tripId).firstResultOptional();
    }

    public List<TimelineTripMovementOverrideEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 order by sourceTripTimestamp", userId).list();
    }

    public List<TimelineTripMovementOverrideEntity> findUnmatchedByUserId(UUID userId) {
        return find("user.id = ?1 and trip is null order by sourceTripTimestamp", userId).list();
    }

    public long deleteByUserIdAndTripId(UUID userId, Long tripId) {
        return delete("user.id = ?1 and trip.id = ?2", userId, tripId);
    }
}
