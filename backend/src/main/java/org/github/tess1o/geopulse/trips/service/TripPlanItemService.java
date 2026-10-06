package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.ReorderTripPlanItemsDto;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_PLAN_ITEM_NOT_FOUND;

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
                : tripPlanItemRepository.nextOrderIndex(tripId);

        TripPlanItemEntity entity = TripPlanItemEntity.builder()
                .trip(trip)
                .title(dto.getTitle().trim())
                .notes(dto.getNotes())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .plannedDay(dto.getPlannedDay())
                .priority(dto.getPriority() != null ? dto.getPriority() : TripPlanItemPriority.OPTIONAL)
                .travelMode(dto.getTravelMode())
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
                .orElseThrow(() -> new GeoPulseException(TRIP_PLAN_ITEM_NOT_FOUND, "Trip plan item not found"));

        boolean dayChanged = !Objects.equals(entity.getPlannedDay(), dto.getPlannedDay());

        entity.setTitle(dto.getTitle().trim());
        entity.setNotes(dto.getNotes());
        entity.setLatitude(dto.getLatitude());
        entity.setLongitude(dto.getLongitude());
        entity.setPlannedDay(dto.getPlannedDay());
        if (dto.getPriority() != null) {
            entity.setPriority(dto.getPriority());
        }
        // Always applied: null is a real choice here ("automatic"), not "unchanged".
        entity.setTravelMode(dto.getTravelMode());
        if (dto.getOrderIndex() != null) {
            entity.setOrderIndex(dto.getOrderIndex());
        } else if (dayChanged) {
            // Moving a stop to another day without an explicit position appends it to that day,
            // rather than dropping it wherever its old position happens to fall.
            entity.setOrderIndex(tripPlanItemRepository.nextOrderIndex(tripId));
        }

        // Visit evidence (isVisited, visitConfidence, visitSource, visitedAt,
        // manualOverrideState) is deliberately NOT touched here. It is owned by the
        // auto-matcher and by the visit-override endpoint; an edit that only renames a
        // stop must not erase how it was matched, and must not silently clear a user's
        // manual override (which would re-arm the matcher to overwrite their decision).
        // Those fields are no longer part of UpdateTripPlanItemDto at all.

        tripPlanItemRepository.persist(entity);
        log.info("Updated trip plan item {} for trip {} and user {}", itemId, tripId, userId);
        return toDto(entity);
    }

    /**
     * Applies a complete ordering, as produced by drag-and-drop: each item takes its list position
     * as its order index and the day it was dropped on. The list must name exactly the trip's
     * current items - a stale list (a collaborator added or removed a stop meanwhile) is rejected
     * so it cannot silently reorder around, or drop, an item the client has not seen.
     */
    @Transactional
    public List<TripPlanItemDto> reorderTripPlanItems(UUID userId, Long tripId, ReorderTripPlanItemsDto dto) {
        tripAccessService.requirePlanEditAccess(userId, tripId);

        List<ReorderTripPlanItemsDto.Entry> entries = dto != null && dto.getItems() != null ? dto.getItems() : List.of();
        List<TripPlanItemEntity> current = tripPlanItemRepository.findByTripId(tripId);
        Map<Long, TripPlanItemEntity> byId = current.stream()
                .collect(Collectors.toMap(TripPlanItemEntity::getId, Function.identity()));

        Set<Long> requestedIds = new HashSet<>();
        for (ReorderTripPlanItemsDto.Entry entry : entries) {
            if (entry == null || entry.getId() == null || !requestedIds.add(entry.getId())) {
                throw new IllegalArgumentException("Reorder list contains a missing or duplicate item id");
            }
        }
        if (!requestedIds.equals(byId.keySet())) {
            throw new IllegalArgumentException("Reorder list does not match the trip's current plan items");
        }

        for (int position = 0; position < entries.size(); position++) {
            ReorderTripPlanItemsDto.Entry entry = entries.get(position);
            TripPlanItemEntity entity = byId.get(entry.getId());
            entity.setOrderIndex(position);
            entity.setPlannedDay(entry.getPlannedDay());
        }

        tripPlanItemRepository.flush();
        log.info("Reordered {} plan items in trip {} for user {}", entries.size(), tripId, userId);
        return tripPlanItemRepository.findByTripId(tripId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTripPlanItem(UUID userId, Long tripId, Long itemId) {
        tripAccessService.requirePlanEditAccess(userId, tripId);

        TripPlanItemEntity entity = tripPlanItemRepository.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> new GeoPulseException(TRIP_PLAN_ITEM_NOT_FOUND, "Trip plan item not found"));

        tripPlanItemRepository.delete(entity);
        log.info("Deleted trip plan item {} for trip {} and user {}", itemId, tripId, userId);
    }

    @Transactional
    public TripPlanItemDto applyVisitOverride(UUID userId, Long tripId, Long itemId, TripVisitOverrideRequestDto request) {
        tripAccessService.requirePlanEditAccess(userId, tripId);

        TripPlanItemEntity entity = tripPlanItemRepository.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> new GeoPulseException(TRIP_PLAN_ITEM_NOT_FOUND, "Trip plan item not found"));

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
                .travelMode(entity.getTravelMode())
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
