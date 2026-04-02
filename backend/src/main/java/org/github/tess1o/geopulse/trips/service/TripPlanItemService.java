package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitOverrideRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemOverrideState;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemVisitSource;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class TripPlanItemService {

    private final TripAccessService tripAccessService;
    private final TripPlanItemRepository tripPlanItemRepository;

    public TripPlanItemService(TripAccessService tripAccessService,
                               TripPlanItemRepository tripPlanItemRepository) {
        this.tripAccessService = tripAccessService;
        this.tripPlanItemRepository = tripPlanItemRepository;
    }

    public List<TripPlanItemDto> getTripPlanItems(UUID userId, Long tripId) {
        tripAccessService.requireReadAccess(userId, tripId);
        return tripPlanItemRepository.findByTripId(tripId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TripPlanItemDto createTripPlanItem(UUID userId, Long tripId, CreateTripPlanItemDto dto) {
        TripEntity trip = tripAccessService.requirePlanEditAccess(userId, tripId).trip();

        int orderIndex = dto.getOrderIndex() != null
                ? dto.getOrderIndex()
                : (int) tripPlanItemRepository.countByTripId(tripId);

        TripPlanItemEntity entity = TripPlanItemEntity.builder()
                .trip(trip)
                .title(dto.getTitle().trim())
                .notes(dto.getNotes())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .plannedDay(dto.getPlannedDay())
                .priority(dto.getPriority() != null ? dto.getPriority() : TripPlanItemPriority.OPTIONAL)
                .orderIndex(orderIndex)
                .build();

        tripPlanItemRepository.persist(entity);
        log.info("Created trip plan item {} for trip {} and user {}", entity.getId(), tripId, userId);
        return toDto(entity);
    }

    @Transactional
    public TripPlanItemDto updateTripPlanItem(UUID userId, Long tripId, Long itemId, UpdateTripPlanItemDto dto) {
        tripAccessService.requirePlanEditAccess(userId, tripId);

        TripPlanItemEntity entity = tripPlanItemRepository.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> new NotFoundException("Trip plan item not found"));

        entity.setTitle(dto.getTitle().trim());
        entity.setNotes(dto.getNotes());
        entity.setLatitude(dto.getLatitude());
        entity.setLongitude(dto.getLongitude());
        entity.setPlannedDay(dto.getPlannedDay());
        if (dto.getPriority() != null) {
            entity.setPriority(dto.getPriority());
        }
        if (dto.getOrderIndex() != null) {
            entity.setOrderIndex(dto.getOrderIndex());
        }
        if (dto.getIsVisited() != null) {
            entity.setIsVisited(dto.getIsVisited());
        }
        entity.setVisitConfidence(dto.getVisitConfidence());
        entity.setVisitSource(dto.getVisitSource());
        entity.setVisitedAt(dto.getVisitedAt());
        entity.setManualOverrideState(dto.getManualOverrideState());

        tripPlanItemRepository.persist(entity);
        log.info("Updated trip plan item {} for trip {} and user {}", itemId, tripId, userId);
        return toDto(entity);
    }

    @Transactional
    public void deleteTripPlanItem(UUID userId, Long tripId, Long itemId) {
        tripAccessService.requirePlanEditAccess(userId, tripId);

        TripPlanItemEntity entity = tripPlanItemRepository.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> new NotFoundException("Trip plan item not found"));

        tripPlanItemRepository.delete(entity);
        log.info("Deleted trip plan item {} for trip {} and user {}", itemId, tripId, userId);
    }

    @Transactional
    public TripPlanItemDto applyVisitOverride(UUID userId, Long tripId, Long itemId, TripVisitOverrideRequestDto request) {
        tripAccessService.requirePlanEditAccess(userId, tripId);

        TripPlanItemEntity entity = tripPlanItemRepository.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> new NotFoundException("Trip plan item not found"));

        String action = request != null && request.getAction() != null ? request.getAction().trim().toUpperCase() : "";

        switch (action) {
            case "CONFIRM_VISITED" -> {
                entity.setIsVisited(true);
                entity.setVisitSource(TripPlanItemVisitSource.MANUAL);
                entity.setManualOverrideState(TripPlanItemOverrideState.CONFIRMED);
                entity.setVisitedAt(request.getVisitedAt() != null ? request.getVisitedAt() : Instant.now());
            }
            case "REJECT_VISIT" -> {
                entity.setIsVisited(false);
                entity.setVisitSource(TripPlanItemVisitSource.MANUAL);
                entity.setManualOverrideState(TripPlanItemOverrideState.REJECTED);
                entity.setVisitedAt(null);
                entity.setVisitConfidence(null);
            }
            case "RESET_TO_AUTO" -> {
                entity.setIsVisited(false);
                entity.setVisitSource(null);
                entity.setManualOverrideState(null);
                entity.setVisitedAt(null);
                entity.setVisitConfidence(null);
            }
            default -> throw new IllegalArgumentException("Unknown visit override action: " + action);
        }

        tripPlanItemRepository.persist(entity);
        log.info("Applied visit override '{}' to plan item {} in trip {} for user {}", action, itemId, tripId, userId);
        return toDto(entity);
    }

    private TripPlanItemDto toDto(TripPlanItemEntity entity) {
        return TripPlanItemDto.builder()
                .id(entity.getId())
                .tripId(entity.getTrip().getId())
                .title(entity.getTitle())
                .notes(entity.getNotes())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .plannedDay(entity.getPlannedDay())
                .priority(entity.getPriority())
                .orderIndex(entity.getOrderIndex())
                .isVisited(entity.getIsVisited())
                .visitConfidence(entity.getVisitConfidence())
                .visitSource(entity.getVisitSource())
                .visitedAt(entity.getVisitedAt())
                .manualOverrideState(entity.getManualOverrideState())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
