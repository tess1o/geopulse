package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.timelinelabels.model.entity.TimelineLabelEntity;
import org.github.tess1o.geopulse.timelinelabels.repository.TimelineLabelRepository;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.TripCollaboratorDto;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripCollaboratorDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripDto;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorAccessRole;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripCollaboratorRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_LABEL_NOT_FOUND;

@ApplicationScoped
@Slf4j
public class TripService {

    private static final String TRIP_LABEL_SOURCE = "trip";
    private static final String OWNTRACKS_SOURCE = "owntracks";

    private final TripRepository tripRepository;
    private final TripCollaboratorRepository tripCollaboratorRepository;
    private final TimelineLabelRepository timelineLabelRepository;
    private final UserRepository userRepository;
    private final TripAccessService tripAccessService;
    private final FriendshipRepository friendshipRepository;

    public TripService(TripRepository tripRepository,
                       TripCollaboratorRepository tripCollaboratorRepository,
                       TimelineLabelRepository timelineLabelRepository,
                       UserRepository userRepository,
                       TripAccessService tripAccessService,
                       FriendshipRepository friendshipRepository) {
        this.tripRepository = tripRepository;
        this.tripCollaboratorRepository = tripCollaboratorRepository;
        this.timelineLabelRepository = timelineLabelRepository;
        this.userRepository = userRepository;
        this.tripAccessService = tripAccessService;
        this.friendshipRepository = friendshipRepository;
    }

    public List<TripDto> getTrips(UUID userId, String statusFilter) {
        TripStatus filterStatus = parseStatusFilter(statusFilter);

        List<TripEntity> ownedTrips = tripRepository.findByUserId(userId);
        List<TripCollaboratorEntity> sharedMemberships = tripCollaboratorRepository.findByCollaboratorUserIdWithTrip(userId);

        Map<Long, TripDto> aggregated = new LinkedHashMap<>();

        for (TripEntity ownedTrip : ownedTrips) {
            TripDto dto = toDto(ownedTrip, true, "OWNER");
            if (filterStatus == null || dto.getStatus() == filterStatus) {
                aggregated.put(dto.getId(), dto);
            }
        }

        for (TripCollaboratorEntity membership : sharedMemberships) {
            TripEntity sharedTrip = membership.getTrip();
            UUID ownerId = sharedTrip.getUser().getId();
            if (!friendshipRepository.existsFriendship(ownerId, userId)) {
                continue;
            }

            TripDto dto = toDto(sharedTrip, false, membership.getAccessRole().name());
            if (filterStatus == null || dto.getStatus() == filterStatus) {
                aggregated.putIfAbsent(dto.getId(), dto);
            }
        }

        List<TripDto> result = new ArrayList<>(aggregated.values());
        result.sort(Comparator.comparing(TripDto::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TripDto::getId, Comparator.reverseOrder()));
        return result;
    }

    public TripDto getTrip(UUID userId, Long tripId) {
        TripAccessContext access = tripAccessService.requireReadAccess(userId, tripId);
        return toDto(access.trip(), access.owner(), access.resolvedAccessRole());
    }

    @Transactional
    public TripDto createTripFromTimelineLabel(UUID userId, Long timelineLabelId) {
        TimelineLabelEntity timelineLabel = timelineLabelRepository.findByIdAndUserId(timelineLabelId, userId)
                .orElseThrow(() -> new GeoPulseException(TIMELINE_LABEL_NOT_FOUND, "Timeline label not found"));

        if (timelineLabel.getEndTime() == null) {
            throw new IllegalArgumentException("Cannot convert active timeline label without end time");
        }

        tripRepository.findByTimelineLabelIdAndUserId(timelineLabelId, userId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("This timeline label is already linked to a trip");
                });

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        TripEntity entity = TripEntity.builder()
                .user(user)
                .timelineLabel(timelineLabel)
                .name(timelineLabel.getName())
                .startTime(timelineLabel.getStartTime())
                .endTime(timelineLabel.getEndTime())
                .status(deriveTemporalStatus(timelineLabel.getStartTime(), timelineLabel.getEndTime()))
                .color(timelineLabel.getColor())
                .notes(null)
                .build();

        tripRepository.persist(entity);
        log.info("Created trip {} from timeline label {} for user {}", entity.getId(), timelineLabelId, userId);
        return toDto(entity, true, "OWNER");
    }

    @Transactional
    public TripDto createTrip(UUID userId, CreateTripDto dto) {
        Instant startTime = dto.getStartTime();
        Instant endTime = dto.getEndTime();
        validateDatePair(startTime, endTime);

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        String tripName = dto.getName().trim();

        TripEntity entity;
        if (!hasBothDates(startTime, endTime)) {
            entity = TripEntity.builder()
                    .user(user)
                    .timelineLabel(null)
                    .name(tripName)
                    .startTime(null)
                    .endTime(null)
                    .status(TripStatus.UNPLANNED)
                    .color(dto.getColor())
                    .notes(dto.getNotes())
                    .build();
        } else {
            validateTripRange(startTime, endTime);

            TimelineLabelEntity timelineLabel = createManagedTimelineLabel(user, tripName, startTime, endTime, dto.getColor());

            entity = TripEntity.builder()
                    .user(user)
                    .timelineLabel(timelineLabel)
                    .name(tripName)
                    .startTime(startTime)
                    .endTime(endTime)
                    .status(deriveTemporalStatus(startTime, endTime))
                    .color(dto.getColor())
                    .notes(dto.getNotes())
                    .build();
        }

        tripRepository.persist(entity);
        log.info("Created trip {} for user {}", entity.getId(), userId);
        return toDto(entity, true, "OWNER");
    }

    @Transactional
    public TripDto updateTrip(UUID userId, Long tripId, UpdateTripDto dto) {
        Instant startTime = dto.getStartTime();
        Instant endTime = dto.getEndTime();
        validateDatePair(startTime, endTime);

        TripEntity entity = tripAccessService.requireOwnerAccess(userId, tripId).trip();

        applyTripMetadata(entity, dto.getName().trim(), dto.getColor(), dto.getNotes());

        boolean requestHasDates = hasBothDates(startTime, endTime);
        boolean existingUnplanned = isUnplanned(entity);

        if (existingUnplanned) {
            if (!requestHasDates) {
                if (dto.getStatus() != null && dto.getStatus() != TripStatus.UNPLANNED) {
                    throw new IllegalArgumentException("Unplanned trip status cannot be changed without scheduling dates");
                }

                entity.setStartTime(null);
                entity.setEndTime(null);
                entity.setStatus(TripStatus.UNPLANNED);
                if (entity.getTimelineLabel() != null) {
                    entity.setTimelineLabel(null);
                }

                tripRepository.persist(entity);
                log.info("Updated unplanned trip {} for user {}", tripId, userId);
                return toDto(entity, true, "OWNER");
            }

            validateTripRange(startTime, endTime);

            entity.setStartTime(startTime);
            entity.setEndTime(endTime);

            if (entity.getTimelineLabel() == null) {
                TimelineLabelEntity timelineLabel = createManagedTimelineLabel(
                        entity.getUser(),
                        entity.getName(),
                        entity.getStartTime(),
                        entity.getEndTime(),
                        entity.getColor()
                );
                entity.setTimelineLabel(timelineLabel);
            }

            if (dto.getStatus() == TripStatus.CANCELLED) {
                entity.setStatus(TripStatus.CANCELLED);
            } else {
                entity.setStatus(deriveTemporalStatus(entity.getStartTime(), entity.getEndTime()));
            }

            syncLinkedTimelineLabel(entity);
            tripRepository.persist(entity);
            log.info("Scheduled previously unplanned trip {} for user {}", tripId, userId);
            return toDto(entity, true, "OWNER");
        }

        if (!requestHasDates) {
            throw new IllegalArgumentException("Start time and end time are required for scheduled trips");
        }

        validateTripRange(startTime, endTime);

        entity.setStartTime(startTime);
        entity.setEndTime(endTime);

        if (dto.getStatus() == TripStatus.CANCELLED) {
            entity.setStatus(TripStatus.CANCELLED);
        } else if (entity.getStatus() != TripStatus.CANCELLED) {
            entity.setStatus(deriveTemporalStatus(entity.getStartTime(), entity.getEndTime()));
        }

        syncLinkedTimelineLabel(entity);
        tripRepository.persist(entity);
        log.info("Updated trip {} for user {}", tripId, userId);
        return toDto(entity, true, "OWNER");
    }

    @Transactional
    public TripDto unlinkTripFromTimelineLabel(UUID userId, Long tripId) {
        TripEntity entity = tripAccessService.requireOwnerAccess(userId, tripId).trip();

        entity.setTimelineLabel(null);
        tripRepository.persist(entity);
        log.info("Unlinked timeline label from trip {} for user {}", tripId, userId);
        return toDto(entity, true, "OWNER");
    }

    @Transactional
    public void deleteTrip(UUID userId, Long tripId, boolean deleteBoth) {
        TripEntity entity = tripAccessService.requireOwnerAccess(userId, tripId).trip();

        TimelineLabelEntity linkedLabel = entity.getTimelineLabel();
        entity.setTimelineLabel(null);

        tripRepository.delete(entity);
        if (deleteBoth && linkedLabel != null) {
            timelineLabelRepository.delete(linkedLabel);
        }

        log.info("Deleted trip {} for user {}", tripId, userId);
    }

    public TripEntity getTripEntityOrThrow(UUID userId, Long tripId) {
        return tripAccessService.requireReadAccess(userId, tripId).trip();
    }

    private TimelineLabelEntity createManagedTimelineLabel(UserEntity user, String tripName, Instant startTime, Instant endTime, String color) {
        TimelineLabelEntity timelineLabel = TimelineLabelEntity.builder()
                .user(user)
                .name(tripName)
                .startTime(startTime)
                .endTime(endTime)
                .source(TRIP_LABEL_SOURCE)
                .isActive(false)
                .color(color)
                .showAsPreset(true)
                .build();

        timelineLabelRepository.persist(timelineLabel);
        return timelineLabel;
    }

    private void syncLinkedTimelineLabel(TripEntity trip) {
        TimelineLabelEntity timelineLabel = trip.getTimelineLabel();
        if (timelineLabel == null) {
            return;
        }

        if (isUnplanned(trip)) {
            throw new IllegalArgumentException("Unplanned trips cannot be linked to timeline labels");
        }

        if (trip.getStartTime() == null || trip.getEndTime() == null) {
            throw new IllegalArgumentException("Linked trips require start and end time");
        }

        if (isOwntracksActive(timelineLabel)) {
            throw new IllegalArgumentException("Cannot update linked active OwnTracks timeline label");
        }

        timelineLabel.setName(trip.getName());
        timelineLabel.setStartTime(trip.getStartTime());
        timelineLabel.setEndTime(trip.getEndTime());
        timelineLabel.setColor(trip.getColor());
        timelineLabelRepository.persist(timelineLabel);
    }

    private boolean isOwntracksActive(TimelineLabelEntity timelineLabel) {
        return timelineLabel != null
                && OWNTRACKS_SOURCE.equalsIgnoreCase(timelineLabel.getSource())
                && Boolean.TRUE.equals(timelineLabel.getIsActive());
    }

    private TripStatus parseStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.trim().isEmpty()) {
            return null;
        }
        if ("ALL".equalsIgnoreCase(statusFilter.trim())) {
            return null;
        }
        try {
            return TripStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status filter: " + statusFilter, ex);
        }
    }

    private void validateTripRange(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
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

    public TripStatus resolveStatus(TripEntity entity) {
        if (entity.getStatus() == TripStatus.CANCELLED) {
            return TripStatus.CANCELLED;
        }

        if (isUnplanned(entity)) {
            return TripStatus.UNPLANNED;
        }

        return deriveTemporalStatus(entity.getStartTime(), entity.getEndTime());
    }

    private void validateDatePair(Instant startTime, Instant endTime) {
        if (hasSingleDate(startTime, endTime)) {
            throw new IllegalArgumentException("Start time and end time must both be provided");
        }
    }

    private boolean hasSingleDate(Instant startTime, Instant endTime) {
        return (startTime == null) != (endTime == null);
    }

    private boolean hasBothDates(Instant startTime, Instant endTime) {
        return startTime != null && endTime != null;
    }

    private boolean isUnplanned(TripEntity entity) {
        return entity.getStatus() == TripStatus.UNPLANNED
                || (entity.getStartTime() == null && entity.getEndTime() == null);
    }

    private void applyTripMetadata(TripEntity entity, String name, String color, String notes) {
        entity.setName(name);
        entity.setColor(color);
        entity.setNotes(notes);
    }

    public List<TripCollaboratorDto> getTripCollaborators(UUID userId, Long tripId) {
        tripAccessService.requireOwnerAccess(userId, tripId);
        return tripCollaboratorRepository.findByTripIdWithCollaborator(tripId).stream()
                .map(this::toCollaboratorDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TripCollaboratorDto upsertTripCollaborator(UUID ownerUserId,
                                                      Long tripId,
                                                      UUID friendUserId,
                                                      UpdateTripCollaboratorDto dto) {
        TripEntity trip = tripAccessService.requireOwnerAccess(ownerUserId, tripId).trip();
        if (dto == null || dto.getAccessRole() == null) {
            throw new IllegalArgumentException("accessRole is required");
        }

        if (friendUserId.equals(ownerUserId)) {
            throw new IllegalArgumentException("Owner cannot be added as collaborator");
        }
        if (!friendshipRepository.existsFriendship(ownerUserId, friendUserId)) {
            throw new IllegalArgumentException("Selected user is not your friend");
        }

        UserEntity collaboratorUser = userRepository.findById(friendUserId);
        if (collaboratorUser == null) {
            throw new IllegalArgumentException("Friend not found");
        }

        TripCollaboratorAccessRole requestedRole = dto.getAccessRole();
        TripCollaboratorEntity entity = tripCollaboratorRepository
                .findByTripIdAndCollaboratorUserId(tripId, friendUserId)
                .orElseGet(() -> TripCollaboratorEntity.builder()
                        .trip(trip)
                        .collaborator(collaboratorUser)
                        .build());

        entity.setAccessRole(requestedRole);
        tripCollaboratorRepository.persist(entity);

        log.info("Updated trip collaborator for trip {} owner {} friend {} role {}",
                tripId, ownerUserId, friendUserId, requestedRole);

        return toCollaboratorDto(entity);
    }

    @Transactional
    public void removeTripCollaborator(UUID ownerUserId, Long tripId, UUID friendUserId) {
        tripAccessService.requireOwnerAccess(ownerUserId, tripId);
        tripCollaboratorRepository.deleteByTripIdAndCollaboratorUserId(tripId, friendUserId);
        log.info("Removed trip collaborator for trip {} owner {} friend {}", tripId, ownerUserId, friendUserId);
    }

    private TripDto toDto(TripEntity entity, boolean isOwner, String accessRole) {
        return TripDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .ownerFullName(entity.getUser().getFullName())
                .timelineLabelId(entity.getTimelineLabel() != null ? entity.getTimelineLabel().getId() : null)
                .name(entity.getName())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .status(resolveStatus(entity))
                .color(entity.getColor())
                .notes(entity.getNotes())
                .isOwner(isOwner)
                .accessRole(accessRole)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TripCollaboratorDto toCollaboratorDto(TripCollaboratorEntity entity) {
        return TripCollaboratorDto.builder()
                .userId(entity.getCollaborator().getId())
                .fullName(entity.getCollaborator().getFullName())
                .email(entity.getCollaborator().getEmail())
                .avatar(entity.getCollaborator().getAvatar())
                .accessRole(entity.getAccessRole())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
