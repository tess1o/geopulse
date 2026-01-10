package org.github.tess1o.geopulse.periods.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.periods.model.dto.*;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;
import org.github.tess1o.geopulse.periods.repository.PeriodTagRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class PeriodTagService {

    private final PeriodTagRepository repository;
    private final UserRepository userRepository;

    public PeriodTagService(PeriodTagRepository repository,
                            UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public List<PeriodTagDto> getPeriodTags(UUID userId) {
        log.debug("Fetching period tags for user {}", userId);
        List<PeriodTagEntity> entities = repository.findByUserId(userId);
        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<PeriodTagDto> getActiveTag(UUID userId) {
        log.debug("Fetching active period tag for user {}", userId);
        return repository.findActiveByUserId(userId)
                .map(this::toDto);
    }

    public List<PeriodTagDto> getPeriodTagsForTimeRange(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Fetching period tags for user {} in range {} to {}", userId, startTime, endTime);
        List<PeriodTagEntity> entities = repository.findByUserIdAndTimeRange(userId, startTime, endTime);
        return entities.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PeriodTagDto> checkOverlaps(UUID userId, Instant startTime, Instant endTime, Long excludeId) {
        log.debug("Checking for overlapping period tags for user {}", userId);
        List<PeriodTagEntity> overlapping = repository.findOverlapping(userId, startTime, endTime, excludeId);
        return overlapping.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PeriodTagDto createPeriodTag(UUID userId, CreatePeriodTagDto dto) {
        log.info("Creating period tag '{}' for user {}", dto.getTagName(), userId);

        // Validate
        validatePeriodTag(dto);

        // Get user entity
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        // Create entity
        PeriodTagEntity entity = PeriodTagEntity.builder()
                .user(user)
                .tagName(dto.getTagName())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .source("manual")  // Manual tags created by user
                .isActive(false)   // Manual tags are not active
                .color(dto.getColor())
                .build();

        repository.persist(entity);

        log.info("Created period tag {}", entity.getId());
        return toDto(entity);
    }

    @Transactional
    public PeriodTagDto updatePeriodTag(UUID userId, Long id, UpdatePeriodTagDto dto) {
        log.info("Updating period tag {} for user {}", id, userId);

        // Find existing
        PeriodTagEntity entity = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Period tag not found"));

        // Prevent updating ACTIVE OwnTracks tags (completed ones can be edited)
        if ("owntracks".equals(entity.getSource()) && Boolean.TRUE.equals(entity.getIsActive())) {
            throw new IllegalArgumentException("Cannot update active OwnTracks tag. It is currently being managed by the OwnTracks app. You can edit it after it's completed.");
        }

        // Validate
        validatePeriodTag(dto);

        // Update fields
        entity.setTagName(dto.getTagName());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setColor(dto.getColor());

        repository.persist(entity);

        log.info("Updated period tag {}", id);
        return toDto(entity);
    }

    @Transactional
    public void deletePeriodTag(UUID userId, Long id) {
        log.info("Deleting period tag {} for user {}", id, userId);

        PeriodTagEntity entity = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Period tag not found"));

        // Prevent deleting ACTIVE OwnTracks tags (completed ones can be deleted)
        if ("owntracks".equals(entity.getSource()) && Boolean.TRUE.equals(entity.getIsActive())) {
            throw new IllegalArgumentException("Cannot delete active OwnTracks tag. It is currently being managed by the OwnTracks app. You can delete it after it's completed.");
        }

        repository.delete(entity);
        log.info("Deleted period tag {}", id);
    }

    // Validation
    private void validatePeriodTag(CreatePeriodTagDto dto) {
        if (dto.getTagName() == null || dto.getTagName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }

        if (dto.getStartTime() == null) {
            throw new IllegalArgumentException("Start time is required");
        }

        // End time validation - only manual tags require end time
        if ("owntracks".equals(dto.getSource())) {
            // OwnTracks tags can have null end time (active tags)
            if (dto.getEndTime() != null &&
                    (dto.getEndTime().isBefore(dto.getStartTime()) || dto.getEndTime().equals(dto.getStartTime()))) {
                throw new IllegalArgumentException("End time must be after start time");
            }
        } else {
            // Manual tags require end time
            if (dto.getEndTime() == null) {
                throw new IllegalArgumentException("End time is required for manual tags");
            }
            if (dto.getEndTime().isBefore(dto.getStartTime()) || dto.getEndTime().equals(dto.getStartTime())) {
                throw new IllegalArgumentException("End time must be after start time");
            }
        }

        Instant now = Instant.now();
        if (dto.getStartTime().isAfter(now)) {
            throw new IllegalArgumentException("Start time cannot be in the future");
        }

        // Check if date is reasonable (not before 1970)
        Instant epoch = Instant.ofEpochMilli(0);
        if (dto.getStartTime().isBefore(epoch)) {
            throw new IllegalArgumentException("Start time is too far in the past");
        }
    }

    private void validatePeriodTag(UpdatePeriodTagDto dto) {
        if (dto.getTagName() == null || dto.getTagName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }

        if (dto.getStartTime() == null) {
            throw new IllegalArgumentException("Start time is required");
        }

        // Manual tags (only ones that can be updated) require end time
        if (dto.getEndTime() == null) {
            throw new IllegalArgumentException("End time is required");
        }

        if (dto.getEndTime().isBefore(dto.getStartTime()) || dto.getEndTime().equals(dto.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        Instant now = Instant.now();
        if (dto.getStartTime().isAfter(now)) {
            throw new IllegalArgumentException("Start time cannot be in the future");
        }

        Instant epoch = Instant.ofEpochMilli(0);
        if (dto.getStartTime().isBefore(epoch)) {
            throw new IllegalArgumentException("Start time is too far in the past");
        }
    }

    // Mapping
    private PeriodTagDto toDto(PeriodTagEntity entity) {
        return PeriodTagDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .tagName(entity.getTagName())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .source(entity.getSource())
                .isActive(entity.getIsActive())
                .color(entity.getColor())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
