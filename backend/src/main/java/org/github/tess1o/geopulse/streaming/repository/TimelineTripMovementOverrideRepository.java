package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineTripMovementOverrideRepository implements PanacheRepository<TimelineTripMovementOverrideEntity> {

    public Optional<TimelineTripMovementOverrideEntity> findByUserIdAndTripId(UUID userId, Long tripId) {
        return find("user.id = ?1 and trip.id = ?2 and migratedToTimelineEdits = false", userId, tripId)
                .firstResultOptional();
    }

    public List<TimelineTripMovementOverrideEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 and migratedToTimelineEdits = false order by sourceTripTimestamp", userId).list();
    }

    /** Corrections recorded for the same travelled interval, whether linked to a trip yet or not. */
    public List<TimelineTripMovementOverrideEntity> findByUserIdAndSource(UUID userId, Instant sourceStart,
                                                                          long sourceDurationSeconds) {
        return find("user.id = ?1 and sourceTripTimestamp = ?2 and sourceTripDurationSeconds = ?3 "
                        + "and migratedToTimelineEdits = false",
                userId, sourceStart, sourceDurationSeconds).list();
    }

    public List<TimelineTripMovementOverrideEntity> findUnmatchedByUserId(UUID userId) {
        return find("user.id = ?1 and trip is null and migratedToTimelineEdits = false order by sourceTripTimestamp",
                userId).list();
    }

    public long deleteByUserIdAndTripId(UUID userId, Long tripId) {
        return delete("user.id = ?1 and trip.id = ?2 and migratedToTimelineEdits = false", userId, tripId);
    }
}
