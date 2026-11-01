package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayNameOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditApplicationRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayNameOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class UnifiedTimelineEditRebuildIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject StreamingTimelineGenerationService generationService;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject TimelineEditApplicationRepository applications;
    @Inject TimelineStayNameOverrideRepository legacyNames;
    @Inject EntityManager entityManager;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void manualMovementSourceSurvivesRebuildAndClearsWhenUndone() throws Exception {
        UUID userId = day.ingest(harness, "unified-manual-source");
        String originalType = QuarkusTransaction.requiringNew().call(() -> trips.find("user.id", userId)
                .firstResult().getMovementType());
        persistEdit(userId, 1, TimelineEditKind.SET_MOVEMENT_TYPE, at("08:01"), at("08:46"), Map.of("movementType", "WALK"));
        harness.fullRebuild(userId);
        QuarkusTransaction.requiringNew().run(() -> {
            var trip = trips.find("user.id", userId).firstResult();
            assertThat(trip.getMovementType()).isEqualTo("WALK");
            assertThat(trip.getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);
            trip.setMovementTypeSource(MovementTypeSource.AUTO);
        });
        String repair;
        try (var input = getClass().getResourceAsStream("/db/migration/V36.83.0__Restore_manual_movement_source.sql")) {
            repair = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery(repair).executeUpdate();
            entityManager.clear();
            assertThat(trips.find("user.id", userId).firstResult().getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);
            entityManager.createQuery("update TimelineEditEntity set status = :status where user.id = :userId")
                    .setParameter("status", TimelineEditStatus.REVERTED).setParameter("userId", userId).executeUpdate();
        });
        harness.fullRebuild(userId);
        QuarkusTransaction.requiringNew().run(() -> {
            var trip = trips.find("user.id", userId).firstResult();
            assertThat(trip.getMovementType()).isEqualTo(originalType);
            assertThat(trip.getMovementTypeSource()).isEqualTo(MovementTypeSource.AUTO);
        });
    }

    @Test
    void attributeEditsSurviveAFullRebuildAndMapToTheCurrentStay() {
        UUID userId = day.ingest(harness, "unified-attributes");
        persistEdit(userId, 1, TimelineEditKind.SET_PLACE, at("08:46"), at("12:00"), Map.of(
                "locationName", "Work", "latitude", 49.836, "longitude", 24.076));
        persistEdit(userId, 2, TimelineEditKind.SET_VISIT_NAME, at("08:46"), at("12:00"),
                Map.of("visitName", "Planning day"));

        harness.fullRebuild(userId);

        TimelineStayEntity edited = QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1", userId).stream()
                .filter(stay -> stay.getTimestamp().equals(at("08:46")))
                .findFirst().orElseThrow());
        assertThat(edited.getLocationName()).isEqualTo("Work");
        assertThat(edited.getVisitName()).isEqualTo("Planning day");
        assertThat(QuarkusTransaction.requiringNew().call(() -> applications.count("edit.user.id", userId)))
                .isEqualTo(2);
    }

    @Test
    void structuralOutputCanBeEditedAndRestartedInsideItsSourceInterval() {
        UUID userId = day.ingest(harness, "unified-nested");
        persistEdit(userId, 1, TimelineEditKind.FORCE_STAY, at("08:16"), at("08:20"), Map.of(
                "locationName", "Bakery", "latitude", day.bakery[0], "longitude", day.bakery[1]));
        persistEdit(userId, 2, TimelineEditKind.SET_VISIT_NAME, at("08:16"), at("08:20"),
                Map.of("visitName", "Breakfast"));

        harness.fullRebuild(userId);
        generationService.generateTimelineFromTimestamp(userId, at("08:18"));

        TimelineStayEntity edited = QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1", userId).stream()
                .filter(stay -> stay.getTimestamp().equals(at("08:16")))
                .findFirst().orElseThrow());
        assertThat(edited.getLocationName()).isEqualTo("Bakery");
        assertThat(edited.getVisitName()).isEqualTo("Breakfast");
        assertThat(edited.getStayDuration()).isEqualTo(240);
        harness.assertMatchesFullRebuild(userId, "nested timeline edits after restart inside edited interval");
    }

    @Test
    void migratedLegacyRowsRemainForEquivalenceButAreNotReplayed() {
        UUID userId = harness.createUser("unified-migrated-legacy");
        QuarkusTransaction.requiringNew().run(() -> entityManager.persist(TimelineStayNameOverrideEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .stayStartTime(at("08:00"))
                .stayEndTime(at("09:00"))
                .locationName("Migrated")
                .migratedToTimelineEdits(true)
                .build()));

        assertThat(QuarkusTransaction.requiringNew().call(() -> legacyNames.findByUserId(userId))).isEmpty();
        assertThat(QuarkusTransaction.requiringNew().call(() -> legacyNames.count("user.id", userId))).isEqualTo(1);
    }

    @Test
    void placeRenameUsesTheLiveReferenceWhileVisitNameRemainsIndependent() {
        UUID userId = day.ingest(harness, "unified-place-rename");
        Long favoriteId = QuarkusTransaction.requiringNew().call(() -> {
            FavoritesEntity favorite = FavoritesEntity.builder()
                    .user(entityManager.getReference(UserEntity.class, userId))
                    .geometry(GeoUtils.createPoint(day.office[1], day.office[0]))
                    .name("Old office")
                    .type(FavoriteLocationType.POINT)
                    .build();
            entityManager.persist(favorite);
            entityManager.flush();
            entityManager.persist(TimelineEditEntity.builder()
                    .user(entityManager.getReference(UserEntity.class, userId))
                    .seq(1).kind(TimelineEditKind.SET_PLACE)
                    .anchorStart(at("08:46")).anchorEnd(at("12:00"))
                    .affectedFrom(at("08:46")).affectedTo(at("12:00"))
                    .payload(new LinkedHashMap<>(Map.of("locationName", "Old office")))
                    .placeFavorite(favorite).placeLatitude(day.office[0]).placeLongitude(day.office[1])
                    .placeName("Old office").status(TimelineEditStatus.ACTIVE).build());
            return favorite.getId();
        });
        persistEdit(userId, 2, TimelineEditKind.SET_VISIT_NAME, at("08:46"), at("12:00"),
                Map.of("visitName", "Planning day"));
        QuarkusTransaction.requiringNew().run(() -> {
            FavoritesEntity favorite = entityManager.find(FavoritesEntity.class, favoriteId);
            favorite.setName("Renamed office");
        });

        harness.fullRebuild(userId);

        TimelineStayEntity edited = QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1 and timestamp = ?2", userId, at("08:46")).firstResult());
        assertThat(edited.getLocationName()).isEqualTo("Renamed office");
        assertThat(edited.getVisitName()).isEqualTo("Planning day");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 15})
    void liveReplayWithDifferentBatchSizesMatchesAFullRebuild(int batchSize) {
        UUID userId = harness.createUser("unified-live-" + batchSize);
        persistEdit(userId, 1, TimelineEditKind.SET_MOVEMENT_TYPE, at("08:01"), at("08:46"),
                Map.of("movementType", "TRAIN"));
        List<List<org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage>> batches
                = new ArrayList<>();
        List<org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage> messages = day.track
                .stay(day.home, at("06:00"), at("08:00"), 5)
                .drive(day.home, day.bakery, at("08:01"), at("08:15"))
                .stay(day.bakery, at("08:16"), at("08:20"), 1)
                .drive(day.bakery, day.pharmacy, at("08:21"), at("08:26"))
                .stay(day.pharmacy, at("08:27"), at("08:31"), 1)
                .drive(day.pharmacy, day.office, at("08:32"), at("08:45"))
                .stay(day.office, at("08:46"), at("12:00"), 10)
                .messages();
        for (int index = 0; index < messages.size(); index += batchSize) {
            batches.add(messages.subList(index, Math.min(index + batchSize, messages.size())));
        }

        harness.replayLive(userId, batches);

        boolean hasTrain = QuarkusTransaction.requiringNew().call(() -> trips
                .find("user.id = ?1", userId).stream()
                .anyMatch(trip -> "TRAIN".equals(trip.getMovementType())));
        assertThat(hasTrain).isTrue();
        harness.assertMatchesFullRebuild(userId, "unified movement edit with live batch size " + batchSize);
    }

    private void persistEdit(UUID userId, long seq, TimelineEditKind kind, Instant from, Instant to,
                             Map<String, Object> payload) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.persist(TimelineEditEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .seq(seq)
                .kind(kind)
                .anchorStart(from)
                .anchorEnd(to)
                .affectedFrom(from)
                .affectedTo(to)
                .payload(new LinkedHashMap<>(payload))
                .status(TimelineEditStatus.ACTIVE)
                .build()));
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
