package org.github.tess1o.geopulse.trips.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitOverrideRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemOverrideState;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemVisitSource;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class TripPlanItemServiceIntegrationTest {

    @Inject
    TripPlanItemService tripPlanItemService;
    @Inject
    TripPlanItemRepository tripPlanItemRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    UserService userService;
    @Inject
    CleanupHelper cleanupHelper;

    private UUID userId;
    private Long tripId;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupHelper.cleanupTripWorkspaceAndUsers();

        String email = "trip-plan-item-" + UUID.randomUUID() + "@example.com";
        UserEntity user = userService.registerUser(email, "password123", "Trip Plan Item Tester", "UTC");
        userId = user.getId();

        TripEntity trip = TripEntity.builder()
                .user(user)
                .name("Test Trip")
                .startTime(Instant.parse("2026-02-01T00:00:00Z"))
                .endTime(Instant.parse("2026-02-03T00:00:00Z"))
                .status(TripStatus.COMPLETED)
                .build();
        tripRepository.persist(trip);
        tripId = trip.getId();
    }

    @Test
    @Transactional
    void createTripPlanItem_shouldAssignDefaultOrderAndOptionalPriority() {
        TripPlanItemEntity existing = TripPlanItemEntity.builder()
                .trip(tripRepository.findById(tripId))
                .title("Existing")
                .priority(TripPlanItemPriority.MUST)
                .orderIndex(7)
                .build();
        tripPlanItemRepository.persist(existing);

        CreateTripPlanItemDto dto = new CreateTripPlanItemDto();
        dto.setTitle("  Sagrada Familia  ");
        dto.setNotes("Must visit");
        dto.setLatitude(41.4036);
        dto.setLongitude(2.1744);
        dto.setPlannedDay(LocalDate.of(2026, 2, 2));
        dto.setPriority(null);
        dto.setOrderIndex(null);

        TripPlanItemDto created = tripPlanItemService.createTripPlanItem(userId, tripId, dto);

        assertThat(created.getTitle()).isEqualTo("Sagrada Familia");
        assertThat(created.getPriority()).isEqualTo(TripPlanItemPriority.OPTIONAL);
        assertThat(created.getOrderIndex()).isEqualTo(1);
        assertThat(created.getIsVisited()).isFalse();
    }

    @Test
    @Transactional
    void updateTripPlanItem_shouldPersistManualOverrideState() {
        TripPlanItemEntity item = TripPlanItemEntity.builder()
                .trip(tripRepository.findById(tripId))
                .title("Old title")
                .priority(TripPlanItemPriority.OPTIONAL)
                .orderIndex(0)
                .build();
        tripPlanItemRepository.persist(item);

        Instant visitedAt = Instant.parse("2026-02-02T12:30:00Z");

        UpdateTripPlanItemDto updateDto = new UpdateTripPlanItemDto();
        updateDto.setTitle("  New title  ");
        updateDto.setNotes("Updated notes");
        updateDto.setLatitude(40.7580);
        updateDto.setLongitude(-73.9855);
        updateDto.setPlannedDay(LocalDate.of(2026, 2, 2));
        updateDto.setPriority(TripPlanItemPriority.MUST);
        updateDto.setOrderIndex(5);
        updateDto.setIsVisited(true);
        updateDto.setVisitConfidence(0.93);
        updateDto.setVisitSource(TripPlanItemVisitSource.MANUAL);
        updateDto.setVisitedAt(visitedAt);
        updateDto.setManualOverrideState(TripPlanItemOverrideState.CONFIRMED);

        TripPlanItemDto updated = tripPlanItemService.updateTripPlanItem(userId, tripId, item.getId(), updateDto);

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getPriority()).isEqualTo(TripPlanItemPriority.MUST);
        assertThat(updated.getOrderIndex()).isEqualTo(5);
        assertThat(updated.getIsVisited()).isTrue();
        assertThat(updated.getVisitSource()).isEqualTo(TripPlanItemVisitSource.MANUAL);
        assertThat(updated.getVisitConfidence()).isEqualTo(0.93);
        assertThat(updated.getVisitedAt()).isEqualTo(visitedAt);
        assertThat(updated.getManualOverrideState()).isEqualTo(TripPlanItemOverrideState.CONFIRMED);
    }

    @Test
    @Transactional
    void applyVisitOverride_shouldHandleConfirmRejectAndReset() {
        TripPlanItemEntity item = TripPlanItemEntity.builder()
                .trip(tripRepository.findById(tripId))
                .title("Visit override candidate")
                .priority(TripPlanItemPriority.OPTIONAL)
                .orderIndex(0)
                .isVisited(false)
                .visitConfidence(0.4)
                .build();
        tripPlanItemRepository.persist(item);

        Instant manualVisitTime = Instant.parse("2026-02-01T11:15:00Z");
        TripPlanItemDto confirmed = tripPlanItemService.applyVisitOverride(
                userId,
                tripId,
                item.getId(),
                new TripVisitOverrideRequestDto("CONFIRM_VISITED", manualVisitTime)
        );

        assertThat(confirmed.getIsVisited()).isTrue();
        assertThat(confirmed.getVisitSource()).isEqualTo(TripPlanItemVisitSource.MANUAL);
        assertThat(confirmed.getManualOverrideState()).isEqualTo(TripPlanItemOverrideState.CONFIRMED);
        assertThat(confirmed.getVisitedAt()).isEqualTo(manualVisitTime);

        TripPlanItemDto rejected = tripPlanItemService.applyVisitOverride(
                userId,
                tripId,
                item.getId(),
                new TripVisitOverrideRequestDto("REJECT_VISIT", null)
        );

        assertThat(rejected.getIsVisited()).isFalse();
        assertThat(rejected.getVisitSource()).isEqualTo(TripPlanItemVisitSource.MANUAL);
        assertThat(rejected.getManualOverrideState()).isEqualTo(TripPlanItemOverrideState.REJECTED);
        assertThat(rejected.getVisitedAt()).isNull();
        assertThat(rejected.getVisitConfidence()).isNull();

        TripPlanItemDto reset = tripPlanItemService.applyVisitOverride(
                userId,
                tripId,
                item.getId(),
                new TripVisitOverrideRequestDto("RESET_TO_AUTO", null)
        );

        assertThat(reset.getIsVisited()).isFalse();
        assertThat(reset.getVisitSource()).isNull();
        assertThat(reset.getManualOverrideState()).isNull();
        assertThat(reset.getVisitedAt()).isNull();
        assertThat(reset.getVisitConfidence()).isNull();
    }

}
