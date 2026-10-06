package org.github.tess1o.geopulse.trips.service;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
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
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
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
    private UUID userId;
    private Long tripId;
    @BeforeEach
    @Transactional
    void setUp() {
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
        // Appended after the highest position, not at the item count, which collides after deletes.
        assertThat(created.getOrderIndex()).isEqualTo(8);
        assertThat(created.getIsVisited()).isFalse();
    }
    @Test
    @Transactional
    void updateTripPlanItem_shouldPreserveVisitEvidenceAndManualOverride() {
        Instant visitedAt = Instant.parse("2026-02-02T12:30:00Z");
        TripPlanItemEntity item = TripPlanItemEntity.builder()
                .trip(tripRepository.findById(tripId))
                .title("Old title")
                .priority(TripPlanItemPriority.OPTIONAL)
                .orderIndex(0)
                // Evidence as the auto-matcher or a user's manual override would leave it.
                .isVisited(true)
                .visitConfidence(0.93)
                .visitSource(TripPlanItemVisitSource.MANUAL)
                .visitedAt(visitedAt)
                .manualOverrideState(TripPlanItemOverrideState.CONFIRMED)
                .build();
        tripPlanItemRepository.persist(item);

        UpdateTripPlanItemDto updateDto = new UpdateTripPlanItemDto();
        updateDto.setTitle("  New title  ");
        updateDto.setNotes("Updated notes");
        updateDto.setLatitude(40.7580);
        updateDto.setLongitude(-73.9855);
        updateDto.setPlannedDay(LocalDate.of(2026, 2, 2));
        updateDto.setPriority(TripPlanItemPriority.MUST);
        updateDto.setOrderIndex(5);

        TripPlanItemDto updated = tripPlanItemService.updateTripPlanItem(userId, tripId, item.getId(), updateDto);

        // The editable fields change...
        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getPriority()).isEqualTo(TripPlanItemPriority.MUST);
        assertThat(updated.getOrderIndex()).isEqualTo(5);

        // ...but renaming a stop must not erase how it was matched, nor clear the user's
        // manual override. Clearing the override would re-arm the auto-matcher to
        // overwrite the decision they made explicitly.
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

    private TripPlanItemEntity persistItem(String title, LocalDate plannedDay, int orderIndex) {
        TripPlanItemEntity item = TripPlanItemEntity.builder()
                .trip(tripRepository.findById(tripId))
                .title(title)
                .plannedDay(plannedDay)
                .priority(TripPlanItemPriority.OPTIONAL)
                .orderIndex(orderIndex)
                .build();
        tripPlanItemRepository.persist(item);
        return item;
    }

    @Test
    @Transactional
    void getTripPlanItems_shouldOrderByDayThenPositionWithUnscheduledLast() {
        persistItem("Unscheduled", null, 0);
        persistItem("Day 2 second", LocalDate.of(2026, 2, 2), 1);
        persistItem("Day 1", LocalDate.of(2026, 2, 1), 9);
        persistItem("Day 2 first", LocalDate.of(2026, 2, 2), 0);

        List<String> titles = tripPlanItemService.getTripPlanItems(userId, tripId).stream()
                .map(TripPlanItemDto::getTitle)
                .toList();

        // An earlier day always comes first, whatever its position number.
        assertThat(titles).containsExactly("Day 1", "Day 2 first", "Day 2 second", "Unscheduled");
    }

    @Test
    @Transactional
    void reorderTripPlanItems_shouldApplyPositionsAndDays() {
        TripPlanItemEntity a = persistItem("A", LocalDate.of(2026, 2, 1), 0);
        TripPlanItemEntity b = persistItem("B", LocalDate.of(2026, 2, 1), 1);
        TripPlanItemEntity c = persistItem("C", null, 2);

        // C dragged onto day 1 ahead of A; B dragged to day 2.
        List<TripPlanItemDto> result = tripPlanItemService.reorderTripPlanItems(userId, tripId,
                new ReorderTripPlanItemsDto(List.of(
                        new ReorderTripPlanItemsDto.Entry(c.getId(), LocalDate.of(2026, 2, 1)),
                        new ReorderTripPlanItemsDto.Entry(a.getId(), LocalDate.of(2026, 2, 1)),
                        new ReorderTripPlanItemsDto.Entry(b.getId(), LocalDate.of(2026, 2, 2))
                )));

        assertThat(result).extracting(TripPlanItemDto::getTitle).containsExactly("C", "A", "B");
        assertThat(result).extracting(TripPlanItemDto::getOrderIndex).containsExactly(0, 1, 2);
        assertThat(result).extracting(TripPlanItemDto::getPlannedDay).containsExactly(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2));
    }

    // Not @Transactional: each rejected call must roll back its own transaction, as it would in
    // production, instead of marking a shared test transaction rollback-only.
    @Test
    void reorderTripPlanItems_shouldRejectStaleOrDuplicateLists() {
        Long aId = QuarkusTransaction.requiringNew().call(() -> persistItem("A", null, 0).getId());
        Long bId = QuarkusTransaction.requiringNew().call(() -> persistItem("B", null, 1).getId());

        // Missing an item: a collaborator added B after this client loaded the plan.
        assertThatThrownBy(() -> tripPlanItemService.reorderTripPlanItems(userId, tripId,
                new ReorderTripPlanItemsDto(List.of(new ReorderTripPlanItemsDto.Entry(aId, null)))))
                .isInstanceOf(IllegalArgumentException.class);

        // Duplicate id.
        assertThatThrownBy(() -> tripPlanItemService.reorderTripPlanItems(userId, tripId,
                new ReorderTripPlanItemsDto(List.of(
                        new ReorderTripPlanItemsDto.Entry(aId, null),
                        new ReorderTripPlanItemsDto.Entry(aId, null)))))
                .isInstanceOf(IllegalArgumentException.class);

        // An id from elsewhere.
        assertThatThrownBy(() -> tripPlanItemService.reorderTripPlanItems(userId, tripId,
                new ReorderTripPlanItemsDto(List.of(
                        new ReorderTripPlanItemsDto.Entry(aId, null),
                        new ReorderTripPlanItemsDto.Entry(bId + 100_000, null)))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(tripPlanItemService.getTripPlanItems(userId, tripId))
                .extracting(TripPlanItemDto::getOrderIndex)
                .containsExactly(0, 1);
    }

    @Test
    @Transactional
    void updateTripPlanItem_shouldAppendToNewDayWhenNoPositionGiven() {
        persistItem("Day 2 existing", LocalDate.of(2026, 2, 2), 4);
        TripPlanItemEntity moving = persistItem("Moving", LocalDate.of(2026, 2, 1), 0);

        UpdateTripPlanItemDto updateDto = new UpdateTripPlanItemDto();
        updateDto.setTitle("Moving");
        updateDto.setPlannedDay(LocalDate.of(2026, 2, 2));
        updateDto.setOrderIndex(null);

        TripPlanItemDto updated = tripPlanItemService.updateTripPlanItem(userId, tripId, moving.getId(), updateDto);

        assertThat(updated.getOrderIndex()).isEqualTo(5);
        assertThat(tripPlanItemService.getTripPlanItems(userId, tripId))
                .extracting(TripPlanItemDto::getTitle)
                .containsExactly("Day 2 existing", "Moving");
    }
}
