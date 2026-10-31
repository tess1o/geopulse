package org.github.tess1o.geopulse.trips.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripRepository implements PanacheRepository<TripEntity> {

    public List<TripEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY startTime DESC", userId);
    }

    public Optional<TripEntity> findByIdAndUserId(Long id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public Optional<TripEntity> findByTimelineLabelIdAndUserId(Long timelineLabelId, UUID userId) {
        return find("timelineLabel.id = ?1 and user.id = ?2", timelineLabelId, userId).firstResultOptional();
    }

    /** The user's dated trips that are under way or done (not upcoming or cancelled), starting in {@code [from, toExclusive)}. */
    public List<TripEntity> findTakenStartingBetween(UUID userId, Instant from, Instant toExclusive) {
        return list("""
                        user.id = ?1 and startTime >= ?2 and startTime < ?3 and endTime > startTime
                        and status not in ?4 order by startTime, id""",
                userId, from, toExclusive, List.of(TripStatus.UPCOMING, TripStatus.CANCELLED));
    }
}
