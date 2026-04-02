package org.github.tess1o.geopulse.trips.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.periods.repository.PeriodTagRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TripServiceIntegrationTest {

    @Inject
    TripService tripService;

    @Inject
    TripRepository tripRepository;

    @Inject
    PeriodTagRepository periodTagRepository;

    @Inject
    UserService userService;

    private UUID userId;

    @BeforeEach
    @Transactional
    void setUp() {
        String email = "trip-service-" + UUID.randomUUID() + "@example.com";
        UserEntity user = userService.registerUser(email, "password123", "Trip Service Tester", "UTC");
        userId = user.getId();
    }

    @Test
    @Transactional
    void createTrip_shouldCreateUnplannedWhenDatesMissing() {
        CreateTripDto dto = new CreateTripDto();
        dto.setName("Future ideas");
        dto.setColor("#FF6B6B");
        dto.setNotes("Bucket list");

        TripDto created = tripService.createTrip(userId, dto);

        assertThat(created.getStatus()).isEqualTo(TripStatus.UNPLANNED);
        assertThat(created.getStartTime()).isNull();
        assertThat(created.getEndTime()).isNull();
        assertThat(created.getPeriodTagId()).isNull();

        TripEntity persisted = tripRepository.findById(created.getId());
        assertThat(persisted.getStatus()).isEqualTo(TripStatus.UNPLANNED);
        assertThat(persisted.getPeriodTag()).isNull();
    }

    @Test
    @Transactional
    void createTrip_shouldCreateScheduledTripWithLinkedPeriodTagWhenDatesPresent() {
        CreateTripDto dto = new CreateTripDto();
        dto.setName("Berlin 2099");
        dto.setStartTime(Instant.parse("2099-06-01T00:00:00Z"));
        dto.setEndTime(Instant.parse("2099-06-05T00:00:00Z"));
        dto.setColor("#1E90FF");

        TripDto created = tripService.createTrip(userId, dto);

        assertThat(created.getStatus()).isEqualTo(TripStatus.UPCOMING);
        assertThat(created.getStartTime()).isNotNull();
        assertThat(created.getEndTime()).isNotNull();
        assertThat(created.getPeriodTagId()).isNotNull();
        assertThat(periodTagRepository.findById(created.getPeriodTagId())).isNotNull();
    }

    @Test
    @Transactional
    void updateTrip_shouldSchedulePreviouslyUnplannedTrip() {
        CreateTripDto createDto = new CreateTripDto();
        createDto.setName("Ideas only");
        createDto.setColor("#9C27B0");

        TripDto created = tripService.createTrip(userId, createDto);

        UpdateTripDto updateDto = new UpdateTripDto();
        updateDto.setName("Ideas now planned");
        updateDto.setStartTime(Instant.parse("2099-08-01T00:00:00Z"));
        updateDto.setEndTime(Instant.parse("2099-08-07T00:00:00Z"));
        updateDto.setColor("#9C27B0");
        updateDto.setNotes("Now booked");

        TripDto updated = tripService.updateTrip(userId, created.getId(), updateDto);

        assertThat(updated.getStatus()).isEqualTo(TripStatus.UPCOMING);
        assertThat(updated.getStartTime()).isEqualTo(updateDto.getStartTime());
        assertThat(updated.getEndTime()).isEqualTo(updateDto.getEndTime());
        assertThat(updated.getPeriodTagId()).isNotNull();
    }

    @Test
    @Transactional
    void updateTrip_shouldRejectUnschedulingScheduledTrip() {
        CreateTripDto createDto = new CreateTripDto();
        createDto.setName("Already scheduled");
        createDto.setStartTime(Instant.parse("2099-10-01T00:00:00Z"));
        createDto.setEndTime(Instant.parse("2099-10-02T00:00:00Z"));

        TripDto created = tripService.createTrip(userId, createDto);

        UpdateTripDto updateDto = new UpdateTripDto();
        updateDto.setName("Try unschedule");
        updateDto.setColor("#00AA00");
        updateDto.setNotes("Should fail");

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> tripService.updateTrip(userId, created.getId(), updateDto)
        );

        assertThat(thrown.getMessage()).isEqualTo("Start time and end time are required for scheduled trips");
    }

    @Test
    @Transactional
    void createTrip_shouldRejectPartialDatePair() {
        CreateTripDto dto = new CreateTripDto();
        dto.setName("Invalid partial");
        dto.setStartTime(Instant.parse("2099-11-01T00:00:00Z"));

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> tripService.createTrip(userId, dto)
        );

        assertThat(thrown.getMessage()).isEqualTo("Start time and end time must both be provided");
    }

    @Test
    @Transactional
    void getTrips_shouldFilterByUnplannedStatus() {
        CreateTripDto unplanned = new CreateTripDto();
        unplanned.setName("Wishlist");
        tripService.createTrip(userId, unplanned);

        CreateTripDto scheduled = new CreateTripDto();
        scheduled.setName("Booked");
        scheduled.setStartTime(Instant.parse("2099-12-01T00:00:00Z"));
        scheduled.setEndTime(Instant.parse("2099-12-03T00:00:00Z"));
        tripService.createTrip(userId, scheduled);

        List<TripDto> filtered = tripService.getTrips(userId, "UNPLANNED");

        assertThat(filtered).hasSize(1);
        assertThat(filtered.getFirst().getName()).isEqualTo("Wishlist");
        assertThat(filtered.getFirst().getStatus()).isEqualTo(TripStatus.UNPLANNED);
    }
}
