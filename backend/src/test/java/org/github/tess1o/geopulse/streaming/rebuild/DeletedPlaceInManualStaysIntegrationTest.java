package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stay made by hand at a favorite stays where it was when the favorite is deleted: the timeline is rebuilt after the
 * deletion, and the stay is recreated at the same place, now named the way any other place there would be.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class DeletedPlaceInManualStaysIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    FavoriteLocationService favoriteLocationService;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void gapStayAtADeletedFavoriteIsRecreatedAtTheSamePlace() {
        UUID userId = day.ingest(harness, "delete-favorite-gap");
        harness.ingest(userId, day.track
                .drive(day.office, day.pharmacy, at("16:00"), at("16:10"))
                .stay(day.pharmacy, at("16:11"), at("17:00"), 10)
                .messages());
        harness.fullRebuild(userId);
        long gym = addFavorite(userId, "Gym", day.bakery);
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).stream()
                        .filter(candidate -> candidate.getEndTime().equals(at("16:00")))
                        .findFirst()
                        .orElseThrow());
        DataGapStayOverrideRequest atTheGym = new DataGapStayOverrideRequest();
        atTheGym.setLocationStrategy("SELECTED_LOCATION");
        atTheGym.setFavoriteId(gym);
        QuarkusTransaction.requiringNew().run(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), atTheGym));
        String gapStay = "STAY " + gap.getStartTime() + " ";
        assertThat(harness.snapshot(userId).stays()).anySatisfy(stay -> assertThat(stay).startsWith(gapStay)
                .contains("'Gym'"));

        favoriteLocationService.deleteFavorite(userId, gym);
        harness.fullRebuild(userId);

        assertThat(harness.snapshot(userId).stays())
                .as("stays after deleting the favorite and rebuilding")
                .anySatisfy(stay -> assertThat(stay).startsWith(gapStay)
                        .contains(String.format(java.util.Locale.ROOT, "(%.6f,%.6f)", day.bakery[0], day.bakery[1]))
                        .doesNotContain("'Gym'"));
    }

    @Test
    void splitStayAtADeletedFavoriteIsRecreatedUnderThePlacesOwnName() {
        UUID userId = day.ingest(harness, "delete-favorite-split");
        long bakery = addFavorite(userId, "Bakery", day.bakery);
        edits.split(userId, at("08:16"), at("08:20"), day.bakery, null);
        assertThat(harness.snapshot(userId).stays()).anySatisfy(stay -> assertThat(stay).contains("'Bakery'"));

        favoriteLocationService.deleteFavorite(userId, bakery);
        harness.fullRebuild(userId);

        assertThat(harness.snapshot(userId).stays())
                .as("stays after deleting the favorite and rebuilding")
                .anySatisfy(stay -> assertThat(stay).startsWith("STAY " + at("08:16") + " ")
                        .doesNotContain("'Bakery'")
                        .contains("favorite=null"));
    }

    private long addFavorite(UUID userId, String name, double[] place) {
        favoriteLocationService.addFavorite(userId, new AddPointToFavoritesDto(name, place[0], place[1]));
        return QuarkusTransaction.requiringNew().call(() ->
                favoritesRepository.find("user.id = ?1 and name = ?2", userId, name).firstResult().getId());
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
