package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayNameOverrideEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayNameOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Names the user gave single stays, given to them again after every regeneration. */
@ApplicationScoped
@Slf4j
public class StayNameOverrideService {

    @Inject
    TimelineStayNameOverrideRepository overrideRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    EntityManager entityManager;

    /** Names the stay the user spent from {@code from} to {@code to}, now and after every regeneration. */
    @Transactional
    public void nameStay(UUID userId, Instant from, Instant to, String locationName) {
        if (locationName == null || locationName.isBlank() || locationName.length() > 500) {
            throw new IllegalArgumentException("Stay name must be between 1 and 500 characters");
        }
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Stay name end must be after its start");
        }
        TimelineStayNameOverrideEntity override = overrideRepository
                .findByUserIdAndInterval(userId, from, to)
                .orElseGet(() -> TimelineStayNameOverrideEntity.builder()
                        .user(entityManager.getReference(UserEntity.class, userId))
                        .stayStartTime(from)
                        .stayEndTime(to)
                        .build());
        override.setLocationName(locationName);
        if (override.getId() == null) {
            overrideRepository.persist(override);
        }
    }

    /**
     * Gives each name to the stay sharing most of its time, provided that is more than half of the named time and
     * more than half of the stay: regeneration moves a stay's boundaries a little, but a different stay never shares
     * most of the time with it.
     *
     * @return number of stays named
     */
    @Transactional
    public int reapplyManualOverrides(UUID userId) {
        int applied = 0;
        for (TimelineStayNameOverrideEntity override : overrideRepository.findByUserId(userId)) {
            Optional<TimelineStayEntity> stay = findStay(userId, override);
            if (stay.isPresent()) {
                stay.get().setLocationName(override.getLocationName());
                applied++;
            }
        }
        if (applied > 0) {
            log.info("Re-applied {} manual stay names for user {}", applied, userId);
        }
        return applied;
    }

    /** The stay the name applies to now, if any. */
    public Optional<TimelineStayEntity> findStay(UUID userId, TimelineStayNameOverrideEntity override) {
        long namedSeconds = Duration.between(override.getStayStartTime(), override.getStayEndTime()).getSeconds();
        TimelineStayEntity best = null;
        long bestShared = 0;
        for (TimelineStayEntity stay : stayRepository.findOverlapping(userId, override.getStayStartTime(),
                override.getStayEndTime())) {
            Instant stayEnd = stay.getTimestamp().plusSeconds(stay.getStayDuration());
            Instant sharedStart = stay.getTimestamp().isAfter(override.getStayStartTime())
                    ? stay.getTimestamp() : override.getStayStartTime();
            Instant sharedEnd = stayEnd.isBefore(override.getStayEndTime()) ? stayEnd : override.getStayEndTime();
            long shared = Duration.between(sharedStart, sharedEnd).getSeconds();
            if (shared * 2 > namedSeconds && shared * 2 > stay.getStayDuration() && shared > bestShared) {
                best = stay;
                bestShared = shared;
            }
        }
        return Optional.ofNullable(best);
    }
}
