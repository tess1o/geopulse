package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.AddAreaToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where favorites overlap, which one a stay belongs to must not depend on the order they were saved in, and stay
 * detection, the batch place lookup of a timeline rebuild and the single lookup must all pick the same one: the most
 * specific (smallest) area.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class OverlappingFavoritesIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    FavoriteLocationService favorites;

    @Inject
    TimelineStayRepository stays;

    @Inject
    TimelineTripRepository trips;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void nestedAreasGiveTheSameTimelineWhicheverWasSavedFirst() {
        Track track = new Track();
        double[] office = track.place(49.8350, 24.0750);
        // Inside the campus, 200 m from the office and outside it
        double[] lab = track.place(49.8350, 24.0778);
        double[] home = track.place(49.8420, 24.0310);
        track.stay(office, at("08:00"), at("09:00"), 5)
                .stay(lab, at("09:05"), at("10:00"), 5)
                .drive(lab, home, at("10:01"), at("10:20"))
                .stay(home, at("10:21"), at("11:00"), 5);
        AddAreaToFavoritesDto campus = area("Campus", office, 400);
        AddAreaToFavoritesDto officeArea = area("Office", office, 60);

        UUID campusFirst = harness.createUser("overlap-campus-first");
        harness.ingest(campusFirst, track.messages());
        favorites.addFavorite(campusFirst, campus);
        favorites.addFavorite(campusFirst, officeArea);
        harness.fullRebuild(campusFirst);

        UUID officeFirst = harness.createUser("overlap-office-first");
        harness.ingest(officeFirst, track.messages());
        favorites.addFavorite(officeFirst, officeArea);
        favorites.addFavorite(officeFirst, campus);
        harness.fullRebuild(officeFirst);

        assertThat(timeline(campusFirst)).isEqualTo(timeline(officeFirst));
        assertThat(timeline(campusFirst))
                .anySatisfy(event -> assertThat(event).startsWith("STAY 2026-09-08T08:00:00Z").endsWith("'Office'"))
                .anySatisfy(event -> assertThat(event).contains("'Campus'"));
    }

    @Test
    void singleAndBatchLookupsPickTheSameFavorite() {
        UUID userId = harness.createUser("overlap-lookups");
        Track track = new Track();
        double[] centre = track.place(49.8350, 24.0750);
        favorites.addFavorite(userId, area("Campus", centre, 400));
        favorites.addFavorite(userId, area("Office", centre, 60));
        // 10 m outside the office's east edge, within the boundary tolerance, and 3 m from a saved point
        double[] nearEdge = track.place(49.8350, 24.0750 + 70 / metersPerDegreeLon(centre[0]));
        double[] doorway = track.place(49.8350, 24.0750 + 50 / metersPerDegreeLon(centre[0]));
        UUID other = harness.createUser("overlap-lookups-point");
        favorites.addFavorite(other, area("Office", centre, 60));
        favorites.addFavorite(other, new AddPointToFavoritesDto("Door", nearEdge[0], nearEdge[1] + 3 / metersPerDegreeLon(centre[0])));

        for (double[] place : List.of(centre, nearEdge, doorway)) {
            Point point = GeoUtils.createPoint(place[1], place[0]);
            assertThat(name(favorites.findByPoint(userId, point)))
                    .isEqualTo(name(favorites.findByPointsBatch(userId, List.of(point)).values().iterator().next()));
            assertThat(name(favorites.findByPoint(other, point)))
                    .isEqualTo(name(favorites.findByPointsBatch(other, List.of(point)).values().iterator().next()));
        }
        assertThat(name(favorites.findByPoint(userId, GeoUtils.createPoint(centre[1], centre[0])))).isEqualTo("Office");
    }

    /** Stays and trips as times, durations and place names: favorite ids differ between the two users. */
    private List<String> timeline(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            List<String> events = new ArrayList<>();
            stays.find("user.id = ?1 order by timestamp", userId).stream().forEach(stay -> events.add(
                    "STAY " + stay.getTimestamp() + " " + stay.getStayDuration() + "s '" + stay.getLocationName() + "'"));
            trips.find("user.id = ?1 order by timestamp", userId).stream().forEach(trip -> events.add(
                    "TRIP " + trip.getTimestamp() + " " + trip.getTripDuration() + "s"));
            return events.stream().sorted().toList();
        });
    }

    private static String name(FavoriteLocationsDto found) {
        if (found == null) {
            return null;
        }
        return !found.getAreas().isEmpty() ? found.getAreas().getFirst().getName() : found.getPoints().getFirst().getName();
    }

    private static AddAreaToFavoritesDto area(String name, double[] place, double halfSideMeters) {
        double dLat = halfSideMeters / 111_320.0;
        double dLon = halfSideMeters / metersPerDegreeLon(place[0]);
        return new AddAreaToFavoritesDto(name, place[0] + dLat, place[1] + dLon, place[0] - dLat, place[1] - dLon);
    }

    private static double metersPerDegreeLon(double lat) {
        return 111_320.0 * Math.cos(Math.toRadians(lat));
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-08T" + time + ":00Z");
    }
}
