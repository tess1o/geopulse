package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayNameOverrideEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineStayNameOverrideRepository implements PanacheRepository<TimelineStayNameOverrideEntity> {

    /** The user's stay names in the order they were given, so a later one wins where two cover the same stay. */
    public List<TimelineStayNameOverrideEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 order by id", userId).list();
    }

    /** The correction for one named interval, if the user already named it. */
    public Optional<TimelineStayNameOverrideEntity> findByUserIdAndInterval(UUID userId, Instant from, Instant to) {
        return find("user.id = ?1 and stayStartTime = ?2 and stayEndTime = ?3", userId, from, to)
                .firstResultOptional();
    }
}
