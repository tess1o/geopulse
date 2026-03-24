package org.github.tess1o.geopulse.trips.service;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitSuggestionDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemOverrideState;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemVisitSource;
import org.github.tess1o.geopulse.trips.model.entity.TripPlaceVisitMatchEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.github.tess1o.geopulse.trips.repository.TripPlaceVisitMatchRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TripVisitAutoMatchServiceIntegrationTest {
    @Inject
    TripVisitAutoMatchService tripVisitAutoMatchService;
    @Inject
    TripRepository tripRepository;
    @Inject
    TripPlanItemRepository tripPlanItemRepository;
    @Inject
    TripPlaceVisitMatchRepository tripPlaceVisitMatchRepository;
    @Inject
    TimelineStayRepository timelineStayRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    UserService userService;
    private UUID userId;
    private Long tripId;
    @BeforeEach
    @Transactional
    void setUp() {
        String email = "trip-visit-match-" + UUID.randomUUID() + "@example.com";
        UserEntity user = userService.registerUser(email, "password123", "Trip Match Tester", "UTC");
        userId = user.getId();
        TripEntity trip = TripEntity.builder()
                .user(user)
                .name("Visit Match Trip")
                .startTime(Instant.parse("2026-02-01T00:00:00Z"))
                .endTime(Instant.parse("2026-02-02T00:00:00Z"))
                .status(TripStatus.COMPLETED)
                .build();
        tripRepository.persist(trip);
        tripId = trip.getId();
    }
    @Test
    @Transactional
    void evaluate_shouldPersistAndApplyAutoMatch() {
        TripPlanItemEntity item = createPlanItem("Kyiv Center", 50.45010, 30.52340);
        TimelineStayEntity stay = createStay("Kyiv Center", 50.45018, 30.52346, 1200, Instant.parse("2026-02-01T10:00:00Z"));
        List<TripVisitSuggestionDto> suggestions = tripVisitAutoMatchService.evaluate(userId, tripId, true);
        assertThat(suggestions).hasSize(1);
        TripVisitSuggestionDto suggestion = suggestions.getFirst();
        assertThat(suggestion.getDecision()).isEqualTo("AUTO_MATCHED");
        assertThat(suggestion.getApplied()).isTrue();
        assertThat(suggestion.getMatchedStayId()).isEqualTo(stay.getId());
        assertThat(suggestion.getConfidence()).isGreaterThanOrEqualTo(0.99);
        TripPlanItemEntity persistedItem = tripPlanItemRepository.findById(item.getId());
        assertThat(persistedItem.getIsVisited()).isTrue();
        assertThat(persistedItem.getVisitSource()).isEqualTo(TripPlanItemVisitSource.AUTO);
        assertThat(persistedItem.getVisitedAt()).isEqualTo(stay.getTimestamp());
        List<TripPlaceVisitMatchEntity> matches = tripPlaceVisitMatchRepository.findByTripId(tripId);
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().getDecision()).isEqualTo("AUTO_MATCHED");
        assertThat(matches.getFirst().getStay().getId()).isEqualTo(stay.getId());
    }
    @Test
    @Transactional
    void getStoredSuggestions_shouldReturnLatestStoredMatchPerPlanItem() {
        TripPlanItemEntity item = createPlanItem("Waterloo Station", 51.50330, -0.11470);
        item.setIsVisited(true);
        item.setVisitSource(TripPlanItemVisitSource.AUTO);
        TimelineStayEntity oldStay = createStay("Waterloo", 51.50310, -0.11420, 300, Instant.parse("2026-02-01T09:00:00Z"));
        TimelineStayEntity latestStay = createStay("Waterloo", 51.50335, -0.11480, 900, Instant.parse("2026-02-01T11:00:00Z"));
        tripPlaceVisitMatchRepository.persist(TripPlaceVisitMatchEntity.builder()
                .trip(tripRepository.findById(tripId))
                .planItem(item)
                .stay(oldStay)
                .distanceMeters(80.0)
                .dwellSeconds(oldStay.getStayDuration())
                .confidence(0.65)
                .decision("SUGGESTED")
                .build());
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        tripPlaceVisitMatchRepository.persist(TripPlaceVisitMatchEntity.builder()
                .trip(tripRepository.findById(tripId))
                .planItem(item)
                .stay(latestStay)
                .distanceMeters(12.0)
                .dwellSeconds(latestStay.getStayDuration())
                .confidence(0.95)
                .decision("AUTO_MATCHED")
                .build());
        List<TripVisitSuggestionDto> suggestions = tripVisitAutoMatchService.getStoredSuggestions(userId, tripId);
        assertThat(suggestions).hasSize(1);
        TripVisitSuggestionDto suggestion = suggestions.getFirst();
        assertThat(suggestion.getDecision()).isEqualTo("AUTO_MATCHED");
        assertThat(suggestion.getMatchedStayId()).isEqualTo(latestStay.getId());
        assertThat(suggestion.getConfidence()).isEqualTo(0.95);
        assertThat(suggestion.getApplied()).isTrue();
    }
    @Test
    @Transactional
    void evaluate_shouldReturnNoCoordinatesWithoutPersistingMatch() {
        TripPlanItemEntity item = TripPlanItemEntity.builder()
                .trip(tripRepository.findById(tripId))
                .title("No coordinates item")
                .priority(TripPlanItemPriority.OPTIONAL)
                .orderIndex(0)
                .manualOverrideState(TripPlanItemOverrideState.CONFIRMED)
                .build();
        item.setLatitude(null);
        item.setLongitude(null);
        tripPlanItemRepository.persist(item);
        List<TripVisitSuggestionDto> suggestions = tripVisitAutoMatchService.evaluate(userId, tripId, true);
        assertThat(suggestions).hasSize(1);
        TripVisitSuggestionDto suggestion = suggestions.getFirst();
        assertThat(suggestion.getDecision()).isEqualTo("NO_COORDINATES");
        assertThat(suggestion.getApplied()).isFalse();
        assertThat(tripPlaceVisitMatchRepository.findByTripId(tripId)).isEmpty();
    }
    private TripPlanItemEntity createPlanItem(String title, double lat, double lon) {
        TripPlanItemEntity item = TripPlanItemEntity.builder()
                .trip(tripRepository.findById(tripId))
                .title(title)
                .priority(TripPlanItemPriority.OPTIONAL)
                .orderIndex(0)
                .build();
        item.setLatitude(lat);
        item.setLongitude(lon);
        tripPlanItemRepository.persist(item);
        return item;
    }
    private TimelineStayEntity createStay(String locationName,
                                          double lat,
                                          double lon,
                                          long durationSeconds,
                                          Instant timestamp) {
        TimelineStayEntity stay = TimelineStayEntity.builder()
                .user(userRepository.findById(userId))
                .timestamp(timestamp)
                .stayDuration(durationSeconds)
                .location(GeoUtils.createPoint(lon, lat))
                .locationName(locationName)
                .build();
        timelineStayRepository.persist(stay);
        return stay;
    }
}
