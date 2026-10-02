package org.github.tess1o.geopulse.gps.integrations.owntracks.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.timelinelabels.model.entity.TimelineLabelEntity;
import org.github.tess1o.geopulse.timelinelabels.repository.TimelineLabelRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling OwnTracks tag messages.
 * Manages automatic timeline label lifecycle based on OwnTracks tag changes.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksTagService {

    private final TimelineLabelRepository timelineLabelRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public OwnTracksTagService(TimelineLabelRepository timelineLabelRepository,
                               UserRepository userRepository,
                               EntityManager entityManager) {
        this.timelineLabelRepository = timelineLabelRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    /**
     * Handle OwnTracks tag by managing timeline label lifecycle.
     * This method can be called from MQTT callback threads (non-CDI managed), so it needs
     * both transaction and request context activation.
     *
     * @param message OwnTracks location message with tag information
     * @param userId User ID
     */
    @Transactional
    @ActivateRequestContext
    public void handleTag(OwnTracksLocationMessage message, UUID userId) {
        String tagValue = message.getTag();
        Instant timestamp = Instant.ofEpochSecond(message.getTst());

        log.debug("Processing OwnTracks tag for user {}", userId);

        // Get current active tag for user
        Optional<TimelineLabelEntity> activeTagOpt = timelineLabelRepository.findActiveByUserId(userId);

        // Case 1: New tag value arrives (non-empty)
        if (tagValue != null && !tagValue.trim().isEmpty()) {
            handleNewTag(userId, tagValue.trim(), timestamp, activeTagOpt);
        }
        // Case 2: Empty/null tag arrives (end current tag)
        else if (activeTagOpt.isPresent()) {
            endActiveTag(activeTagOpt.get(), timestamp);
            log.info("Ended active tag for user {} (empty tag received)", userId);
        }
    }

    private void handleNewTag(UUID userId, String tagName, Instant timestamp,
                              Optional<TimelineLabelEntity> activeTagOpt) {
        // If there's an active tag with the same name, do nothing
        if (activeTagOpt.isPresent() && tagName.equals(activeTagOpt.get().getName())) {
            log.debug("OwnTracks tag is already active for user {}", userId);
            return;
        }

        // End the current active tag if exists
        if (activeTagOpt.isPresent()) {
            TimelineLabelEntity currentTag = activeTagOpt.get();
            log.info("Ending active OwnTracks tag for user {}", userId);
            endActiveTag(currentTag, timestamp);

            // Flush to ensure the old tag's is_active=false is committed before creating new active tag
            // This prevents unique constraint violation on idx_timeline_labels_user_active
            entityManager.flush();
            log.debug("Flushed entity manager after ending active tag");
        }

        // Create new active tag
        createActiveTag(userId, tagName, timestamp);
        log.info("Created new active OwnTracks tag for user {}", userId);
    }

    private void endActiveTag(TimelineLabelEntity tag, Instant endTime) {
        tag.setEndTime(endTime);
        tag.setIsActive(false);
        timelineLabelRepository.persist(tag);
    }

    private void createActiveTag(UUID userId, String tagName, Instant startTime) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        TimelineLabelEntity newTag = TimelineLabelEntity.builder()
                .user(user)
                .name(tagName)
                .startTime(startTime)
                .endTime(null)  // Active tag has no end time
                .isActive(true)
                .source("owntracks")
                .color(null)     // OwnTracks tags don't use colors
                .showAsPreset(true)
                .build();

        timelineLabelRepository.persist(newTag);
    }
}
