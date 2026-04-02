package org.github.tess1o.geopulse.trips.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TripWorkspaceDataServiceIntegrationTest {

    @Inject
    TripWorkspaceDataService tripWorkspaceDataService;

    @Inject
    TripRepository tripRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    UserService userService;

    private UUID userId;
    private Long unplannedTripId;

    @BeforeEach
    @Transactional
    void setUp() {
        String email = "trip-workspace-" + UUID.randomUUID() + "@example.com";
        UserEntity user = userService.registerUser(email, "password123", "Trip Workspace Tester", "UTC");
        userId = user.getId();

        TripEntity unplanned = TripEntity.builder()
                .user(userRepository.findById(userId))
                .name("Unplanned workspace")
                .status(TripStatus.UNPLANNED)
                .build();
        tripRepository.persist(unplanned);
        unplannedTripId = unplanned.getId();
    }

    @Test
    void getTripTimeline_shouldReturnEmptyTimelineForUnplannedTrip() {
        MovementTimelineDTO timeline = tripWorkspaceDataService.getTripTimeline(userId, unplannedTripId, null, null);

        assertThat(timeline).isNotNull();
        assertThat(timeline.getUserId()).isEqualTo(userId);
        assertThat(timeline.getStays()).isEmpty();
        assertThat(timeline.getTrips()).isEmpty();
        assertThat(timeline.getDataGaps()).isEmpty();
    }

    @Test
    void getTripPath_shouldReturnEmptyPathForUnplannedTrip() {
        GpsPointPathDTO path = tripWorkspaceDataService.getTripPath(userId, unplannedTripId, null, null);

        assertThat(path).isNotNull();
        assertThat(path.getUserId()).isEqualTo(userId);
        assertThat(path.getPoints()).isEmpty();
        assertThat(path.getPointCount()).isEqualTo(0);
        assertThat(path.getSegments()).isEmpty();
    }
}
