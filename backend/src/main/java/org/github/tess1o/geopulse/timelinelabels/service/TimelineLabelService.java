package org.github.tess1o.geopulse.timelinelabels.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timelinelabels.model.dto.*;
import org.github.tess1o.geopulse.timelinelabels.model.entity.TimelineLabelEntity;
import org.github.tess1o.geopulse.timelinelabels.repository.TimelineLabelRepository;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class TimelineLabelService {

    private static final String MANUAL_SOURCE = "manual";
    private static final String OWNTRACKS_SOURCE = "owntracks";
    private static final Instant EPOCH = Instant.ofEpochMilli(0);

    private final TimelineLabelRepository repository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;

    public TimelineLabelService(TimelineLabelRepository repository,
                                UserRepository userRepository,
                                TripRepository tripRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
    }

    public List<TimelineLabelDto> getTimelineLabels(UUID userId) {
        log.debug("Fetching timeline labels for user {}", userId);
        List<TimelineLabelEntity> entities = repository.findByUserId(userId);
        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<TimelineLabelDto> getActiveLabel(UUID userId) {
        log.debug("Fetching active timeline label for user {}", userId);
        return repository.findActiveByUserId(userId)
                .map(this::toDto);
    }

    public List<TimelineLabelDto> getTimelineLabelsForTimeRange(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Fetching timeline labels for user {} in range {} to {}", userId, startTime, endTime);
        List<TimelineLabelEntity> entities = repository.findByUserIdAndTimeRange(userId, startTime, endTime);
        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<TimelineLabelDto> checkOverlaps(UUID userId, Instant startTime, Instant endTime, Long excludeId) {
        log.debug("Checking for overlapping timeline labels for user {}", userId);
        List<TimelineLabelEntity> overlapping = repository.findOverlapping(userId, startTime, endTime, excludeId);
        return overlapping.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TimelineLabelDto createTimelineLabel(UUID userId, CreateTimelineLabelDto dto) {
        log.info("Creating timeline label '{}' for user {}", dto.getName(), userId);

        validateNameAndRange(dto.getName(), dto.getStartTime(), dto.getEndTime());

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        TimelineLabelEntity entity = TimelineLabelEntity.builder()
                .user(user)
                .name(dto.getName())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .source(MANUAL_SOURCE)  // Labels created through the API are always manual
                .isActive(false)        // Only the OwnTracks integration creates active labels
                .color(dto.getColor())
                .showAsPreset(defaultShowAsPreset(dto.getShowAsPreset()))
                .build();

        repository.persist(entity);

        log.info("Created timeline label {}", entity.getId());
        return toDto(entity);
    }

    @Transactional
    public TimelineLabelDto updateTimelineLabel(UUID userId, Long id, UpdateTimelineLabelDto dto) {
        log.info("Updating timeline label {} for user {}", id, userId);

        TimelineLabelEntity entity = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Timeline label not found"));

        // Prevent updating ACTIVE OwnTracks labels (completed ones can be edited)
        if (OWNTRACKS_SOURCE.equals(entity.getSource()) && Boolean.TRUE.equals(entity.getIsActive())) {
            throw new IllegalArgumentException("Cannot update active OwnTracks label. It is currently being managed by the OwnTracks app. You can edit it after it's completed.");
        }

        validateNameAndRange(dto.getName(), dto.getStartTime(), dto.getEndTime());

        entity.setName(dto.getName());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setColor(dto.getColor());
        if (dto.getShowAsPreset() != null) {
            entity.setShowAsPreset(dto.getShowAsPreset());
        }

        repository.persist(entity);
        syncLinkedTrip(userId, entity);

        log.info("Updated timeline label {}", id);
        return toDto(entity);
    }

    @Transactional
    public void deleteTimelineLabel(UUID userId, Long id, boolean deleteBoth) {
        log.info("Deleting timeline label {} for user {}", id, userId);

        TimelineLabelEntity entity = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Timeline label not found"));

        // Prevent deleting ACTIVE OwnTracks labels (completed ones can be deleted)
        if (OWNTRACKS_SOURCE.equals(entity.getSource()) && Boolean.TRUE.equals(entity.getIsActive())) {
            throw new IllegalArgumentException("Cannot delete active OwnTracks label. It is currently being managed by the OwnTracks app. You can delete it after it's completed.");
        }

        tripRepository.findByTimelineLabelIdAndUserId(id, userId).ifPresent(linkedTrip -> {
            if (deleteBoth) {
                tripRepository.delete(linkedTrip);
            } else {
                linkedTrip.setTimelineLabel(null);
                tripRepository.persist(linkedTrip);
            }
        });

        repository.delete(entity);
        log.info("Deleted timeline label {}", id);
    }

    private void syncLinkedTrip(UUID userId, TimelineLabelEntity timelineLabel) {
        tripRepository.findByTimelineLabelIdAndUserId(timelineLabel.getId(), userId).ifPresent(linkedTrip -> {
            linkedTrip.setName(timelineLabel.getName());
            linkedTrip.setStartTime(timelineLabel.getStartTime());
            linkedTrip.setEndTime(timelineLabel.getEndTime());
            linkedTrip.setColor(timelineLabel.getColor());

            if (linkedTrip.getStatus() != TripStatus.CANCELLED) {
                linkedTrip.setStatus(deriveTemporalStatus(timelineLabel.getStartTime(), timelineLabel.getEndTime()));
            }

            tripRepository.persist(linkedTrip);
        });
    }

    private TripStatus deriveTemporalStatus(Instant startTime, Instant endTime) {
        Instant now = Instant.now();
        if (now.isBefore(startTime)) {
            return TripStatus.UPCOMING;
        }
        if (now.isAfter(endTime)) {
            return TripStatus.COMPLETED;
        }
        return TripStatus.ACTIVE;
    }

    private boolean defaultShowAsPreset(Boolean showAsPreset) {
        return showAsPreset == null || showAsPreset;
    }

    /**
     * A label's range is free-form: it may sit entirely in the past (a completed
     * vacation) or entirely in the future (a planned one). Only the ordering and
     * the sanity bounds are enforced here.
     */
    private void validateNameAndRange(String name, Instant startTime, Instant endTime) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Label name cannot be empty");
        }

        if (startTime == null) {
            throw new IllegalArgumentException("Start time is required");
        }

        if (endTime == null) {
            throw new IllegalArgumentException("End time is required");
        }

        if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        // Check if date is reasonable (not before 1970)
        if (startTime.isBefore(EPOCH)) {
            throw new IllegalArgumentException("Start time is too far in the past");
        }
    }

    private TimelineLabelDto toDto(TimelineLabelEntity entity) {
        return TimelineLabelDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .name(entity.getName())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .source(entity.getSource())
                .isActive(entity.getIsActive())
                .color(entity.getColor())
                .showAsPreset(entity.getShowAsPreset() == null || entity.getShowAsPreset())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
