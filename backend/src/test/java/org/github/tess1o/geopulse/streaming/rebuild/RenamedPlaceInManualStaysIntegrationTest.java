package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.EditFavoriteDto;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingUpdateDTO;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renaming a place renames every visit to it. Stays made by hand keep the place they were made at, so a rebuild must
 * recreate them under the new name too, not under the one the place had when the stay was made.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class RenamedPlaceInManualStaysIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    FavoriteLocationService favoriteLocationService;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    ReverseGeocodingManagementService geocodingManagementService;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    TimelineStayRepository stayRepository;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void splitStayAtARenamedFavoriteKeepsTheNewNameAfterARebuild() {
        UUID userId = day.ingest(harness, "rename-favorite-split");
        favoriteLocationService.addFavorite(userId, new AddPointToFavoritesDto("Bakery", day.bakery[0], day.bakery[1]));
        long favoriteId = QuarkusTransaction.requiringNew().call(() ->
                favoritesRepository.find("user.id = ?1", userId).firstResult().getId());
        // No name typed: the stay is named after the favorite.
        edits.split(userId, at("08:16"), at("08:20"), day.bakery, null);
        assertThat(stayAt(userId, "08:16").getLocationName()).isEqualTo("Bakery");

        favoriteLocationService.updateFavorite(userId, favoriteId,
                new EditFavoriteDto("Corner Bakery", null, null, null, null, null, null));
        assertThat(stayAt(userId, "08:16").getLocationName()).as("right after the rename").isEqualTo("Corner Bakery");

        harness.fullRebuild(userId);
        assertThat(stayAt(userId, "08:16").getLocationName()).as("after a rebuild").isEqualTo("Corner Bakery");
    }

    @Test
    void splitStayKeepsANameTypedForItWhenItsFavoriteIsRenamed() {
        UUID userId = day.ingest(harness, "rename-favorite-typed");
        favoriteLocationService.addFavorite(userId, new AddPointToFavoritesDto("Bakery", day.bakery[0], day.bakery[1]));
        long favoriteId = QuarkusTransaction.requiringNew().call(() ->
                favoritesRepository.find("user.id = ?1", userId).firstResult().getId());
        edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Coffee with Anna");

        favoriteLocationService.updateFavorite(userId, favoriteId,
                new EditFavoriteDto("Corner Bakery", null, null, null, null, null, null));
        harness.fullRebuild(userId);

        assertThat(stayAt(userId, "08:16").getLocationName()).isEqualTo("Coffee with Anna");
    }

    @Test
    void splitStayAtARenamedGeocodedPlaceKeepsTheNewNameAfterARebuild() {
        UUID userId = day.ingest(harness, "rename-geocoding-split");
        edits.split(userId, at("08:27"), at("08:31"), day.pharmacy, null);
        TimelineStayEntity pharmacy = stayAt(userId, "08:27");

        // The place is shared with other users; editing it makes the user's own copy.
        geocodingManagementService.updateGeocodingResult(userId, pharmacy.getGeocodingLocation().getId(),
                new ReverseGeocodingUpdateDTO("Corner Pharmacy", null, null));
        assertThat(stayAt(userId, "08:27").getLocationName()).as("right after the edit").isEqualTo("Corner Pharmacy");

        harness.fullRebuild(userId);
        assertThat(stayAt(userId, "08:27").getLocationName()).as("after a rebuild").isEqualTo("Corner Pharmacy");
    }

    @Test
    void gapStayAtARenamedGeocodedPlaceKeepsTheNewNameAfterARebuild() {
        UUID userId = day.ingest(harness, "rename-geocoding-gap");
        // The phone is off from noon; the user was at a client's, a place known from an earlier visit.
        harness.ingest(userId, day.track
                .drive(day.office, day.pharmacy, at("16:00"), at("16:10"))
                .stay(day.pharmacy, at("16:11"), at("17:00"), 10)
                .messages());
        harness.fullRebuild(userId);
        long pharmacyPlace = stayAt(userId, "16:11").getGeocodingLocation().getId();
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).stream()
                        .filter(candidate -> candidate.getEndTime().equals(at("16:00")))
                        .findFirst()
                        .orElseThrow());
        DataGapStayOverrideRequest atTheClient = new DataGapStayOverrideRequest();
        atTheClient.setLocationStrategy("SELECTED_LOCATION");
        atTheClient.setGeocodingId(pharmacyPlace);
        QuarkusTransaction.requiringNew().run(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), atTheClient));
        Instant gapStayStart = gap.getStartTime();

        geocodingManagementService.updateGeocodingResult(userId, pharmacyPlace,
                new ReverseGeocodingUpdateDTO("Client office", null, null));
        assertThat(stayStarting(userId, gapStayStart).getLocationName()).as("right after the edit")
                .isEqualTo("Client office");

        harness.fullRebuild(userId);
        assertThat(stayStarting(userId, gapStayStart).getLocationName()).as("after a rebuild")
                .isEqualTo("Client office");
    }

    private TimelineStayEntity stayAt(UUID userId, String time) {
        return stayStarting(userId, at(time));
    }

    private TimelineStayEntity stayStarting(UUID userId, Instant start) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TimelineStayEntity stay = stayRepository.find("user.id = ?1 and timestamp = ?2", userId, start)
                    .firstResultOptional()
                    .orElseThrow(() -> new AssertionError("No stay starts at " + start + ": "
                            + harness.snapshot(userId).stays()));
            if (stay.getGeocodingLocation() != null) {
                stay.getGeocodingLocation().getId();
            }
            return stay;
        });
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
