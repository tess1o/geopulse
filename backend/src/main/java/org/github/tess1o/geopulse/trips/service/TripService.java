package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;
import org.github.tess1o.geopulse.periods.repository.PeriodTagRepository;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class TripService {

    private static final String TRIP_PERIOD_SOURCE = "trip";
    private static final String OWNTRACKS_SOURCE = "owntracks";

    private final TripRepository tripRepository;
    private final PeriodTagRepository periodTagRepository;
    private final UserRepository userRepository;

    public TripService(TripRepository tripRepository,
                       PeriodTagRepository periodTagRepository,
                       UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.periodTagRepository = periodTagRepository;
        this.userRepository = userRepository;
    }

    public List<TripDto> getTrips(UUID userId, String statusFilter) {
        List<TripDto> trips = tripRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        TripStatus filterStatus = parseStatusFilter(statusFilter);
        if (filterStatus == null) {
            return trips;
        }

        return trips.stream()
                .filter(trip -> trip.getStatus() == filterStatus)
                .collect(Collectors.toList());
    }

    public TripDto getTrip(UUID userId, Long tripId) {
        TripEntity entity = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        return toDto(entity);
    }

    @Transactional
    public TripDto createTripFromPeriodTag(UUID userId, Long periodTagId) {
        PeriodTagEntity tag = periodTagRepository.findByIdAndUserId(periodTagId, userId)
                .orElseThrow(() -> new NotFoundException("Period tag not found"));

        if (tag.getEndTime() == null) {
            throw new IllegalArgumentException("Cannot convert active period tag without end time");
        }

        tripRepository.findByPeriodTagIdAndUserId(periodTagId, userId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("This period tag is already linked to a trip");
                });

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        TripEntity entity = TripEntity.builder()
                .user(user)
                .periodTag(tag)
                .name(tag.getTagName())
                .startTime(tag.getStartTime())
                .endTime(tag.getEndTime())
                .status(deriveTemporalStatus(tag.getStartTime(), tag.getEndTime()))
                .color(tag.getColor())
                .notes(null)
                .build();

        tripRepository.persist(entity);
        log.info("Created trip {} from period tag {} for user {}", entity.getId(), periodTagId, userId);
        return toDto(entity);
    }

    @Transactional
    public TripDto createTrip(UUID userId, CreateTripDto dto) {
        validateTripRange(dto.getStartTime(), dto.getEndTime());

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        PeriodTagEntity periodTag = createManagedPeriodTag(user, dto.getName(), dto.getStartTime(), dto.getEndTime(), dto.getColor());

        TripEntity entity = TripEntity.builder()
                .user(user)
                .periodTag(periodTag)
                .name(dto.getName().trim())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .status(deriveTemporalStatus(dto.getStartTime(), dto.getEndTime()))
                .color(dto.getColor())
                .notes(dto.getNotes())
                .build();

        tripRepository.persist(entity);
        log.info("Created trip {} for user {}", entity.getId(), userId);
        return toDto(entity);
    }

    @Transactional
    public TripDto updateTrip(UUID userId, Long tripId, UpdateTripDto dto) {
        validateTripRange(dto.getStartTime(), dto.getEndTime());

        TripEntity entity = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));

        entity.setName(dto.getName().trim());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setColor(dto.getColor());
        entity.setNotes(dto.getNotes());

        if (dto.getStatus() == TripStatus.CANCELLED) {
            entity.setStatus(TripStatus.CANCELLED);
        } else if (entity.getStatus() != TripStatus.CANCELLED) {
            entity.setStatus(deriveTemporalStatus(entity.getStartTime(), entity.getEndTime()));
        }

        syncLinkedPeriodTag(entity);
        tripRepository.persist(entity);
        log.info("Updated trip {} for user {}", tripId, userId);
        return toDto(entity);
    }

    @Transactional
    public TripDto unlinkTripFromPeriodTag(UUID userId, Long tripId) {
        TripEntity entity = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));

        entity.setPeriodTag(null);
        tripRepository.persist(entity);
        log.info("Unlinked period tag from trip {} for user {}", tripId, userId);
        return toDto(entity);
    }

    @Transactional
    public void deleteTrip(UUID userId, Long tripId, boolean deleteBoth) {
        TripEntity entity = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));

        PeriodTagEntity linkedTag = entity.getPeriodTag();
        entity.setPeriodTag(null);

        tripRepository.delete(entity);
        if (deleteBoth && linkedTag != null) {
            periodTagRepository.delete(linkedTag);
        }

        log.info("Deleted trip {} for user {}", tripId, userId);
    }

    public TripEntity getTripEntityOrThrow(UUID userId, Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));
    }

    private PeriodTagEntity createManagedPeriodTag(UserEntity user, String tripName, Instant startTime, Instant endTime, String color) {
        PeriodTagEntity tag = PeriodTagEntity.builder()
                .user(user)
                .tagName(tripName)
                .startTime(startTime)
                .endTime(endTime)
                .source(TRIP_PERIOD_SOURCE)
                .isActive(false)
                .color(color)
                .build();

        periodTagRepository.persist(tag);
        return tag;
    }

    private void syncLinkedPeriodTag(TripEntity trip) {
        PeriodTagEntity periodTag = trip.getPeriodTag();
        if (periodTag == null) {
            return;
        }

        if (isOwntracksActive(periodTag)) {
            throw new IllegalArgumentException("Cannot update linked active OwnTracks timeline label");
        }

        periodTag.setTagName(trip.getName());
        periodTag.setStartTime(trip.getStartTime());
        periodTag.setEndTime(trip.getEndTime());
        periodTag.setColor(trip.getColor());
        periodTagRepository.persist(periodTag);
    }

    private boolean isOwntracksActive(PeriodTagEntity periodTag) {
        return periodTag != null
                && OWNTRACKS_SOURCE.equalsIgnoreCase(periodTag.getSource())
                && Boolean.TRUE.equals(periodTag.getIsActive());
    }

    private TripStatus parseStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.trim().isEmpty()) {
            return null;
        }
        try {
            return TripStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status filter: " + statusFilter);
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
        return deriveTemporalStatus(entity.getStartTime(), entity.getEndTime());
    }

    private TripDto toDto(TripEntity entity) {
        return TripDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .periodTagId(entity.getPeriodTag() != null ? entity.getPeriodTag().getId() : null)
                .name(entity.getName())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .status(resolveStatus(entity))
                .color(entity.getColor())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
