package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceStatisticsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceVisitDTO;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Place statistics and visit lists are computed from the seeded stays below.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class PlaceDetailsServiceIntegrationTest {

    private static final Instant JAN_5 = Instant.parse("2026-01-05T08:00:00Z");
    private static final Instant JAN_6 = Instant.parse("2026-01-06T08:00:00Z");
    private static final Instant JAN_7 = Instant.parse("2026-01-07T08:00:00Z");

    @Inject
    PlaceDetailsService service;

    @Inject
    TimelineSeeder seed;

    private UUID ownerId;
    private long homeId;
    private long museumId;
    private long unvisitedId;
    private long otherUsersFavoriteId;

    /*
     * Home (favorite): 3600s on Jan 5, 7200s on Jan 6, 1800s on Jan 7
     * Museum (geocoding): 1000s and 3000s on Jan 8
     * Unvisited (favorite): no stays
     */
    @BeforeEach
    @Transactional
    void setUp() {
        String token = TestIds.uniqueValue("place");
        UserEntity owner = seed.user("place-owner", "UTC");
        ownerId = owner.getId();
        FavoritesEntity home = seed.favorite(owner, "Home", "City " + token, "Country " + token, 50.4501, 30.5234);
        ReverseGeocodingLocationEntity museum = seed.geocoding(owner, "Museum " + token, "City " + token,
                "Country " + token, 50.4600, 30.5300);
        homeId = home.getId();
        museumId = museum.getId();
        unvisitedId = seed.favorite(owner, "Unvisited", null, null, 50.5000, 30.6000).getId();

        seed.stay(owner, home, JAN_5, 3600);
        seed.stay(owner, home, JAN_6, 7200);
        seed.stay(owner, home, JAN_7, 1800);
        seed.stay(owner, museum, Instant.parse("2026-01-08T09:00:00Z"), 1000);
        seed.stay(owner, museum, Instant.parse("2026-01-08T15:00:00Z"), 3000);

        UserEntity other = seed.user("place-other", "UTC");
        FavoritesEntity otherFavorite = seed.favorite(other, "Other home", null, null, 48.8566, 2.3522);
        seed.stay(other, otherFavorite, JAN_5, 500);
        otherUsersFavoriteId = otherFavorite.getId();
    }

    @Test
    void favoriteDetails_containVisitStatistics() {
        PlaceDetailsDTO details = service.getPlaceDetails("favorite", homeId, ownerId, "UTC").orElseThrow();

        assertThat(details.getLocationName()).isEqualTo("Home");
        assertThat(details.getType()).isEqualTo("favorite");
        assertThat(details.getGeometry().getLatitude()).isEqualTo(50.4501);
        assertThat(details.getGeometry().getLongitude()).isEqualTo(30.5234);
        PlaceStatisticsDTO statistics = details.getStatistics();
        assertThat(statistics.getTotalVisits()).isEqualTo(3);
        assertThat(statistics.getTotalDuration()).isEqualTo(12600);
        assertThat(statistics.getAverageDuration()).isEqualTo(4200);
        assertThat(statistics.getMinDuration()).isEqualTo(1800);
        assertThat(statistics.getMaxDuration()).isEqualTo(7200);
        assertThat(statistics.getFirstVisit()).isEqualTo(JAN_5);
        assertThat(statistics.getLastVisit()).isEqualTo(JAN_7);
    }

    @Test
    void geocodingDetails_containVisitStatistics() {
        PlaceDetailsDTO details = service.getPlaceDetails("geocoding", museumId, ownerId, "UTC").orElseThrow();

        assertThat(details.getType()).isEqualTo("geocoding");
        PlaceStatisticsDTO statistics = details.getStatistics();
        assertThat(statistics.getTotalVisits()).isEqualTo(2);
        assertThat(statistics.getTotalDuration()).isEqualTo(4000);
        assertThat(statistics.getAverageDuration()).isEqualTo(2000);
        assertThat(statistics.getMinDuration()).isEqualTo(1000);
        assertThat(statistics.getMaxDuration()).isEqualTo(3000);
    }

    @Test
    void placeWithoutVisits_hasZeroStatistics() {
        PlaceStatisticsDTO statistics = service.getPlaceDetails("favorite", unvisitedId, ownerId, "UTC")
                .orElseThrow().getStatistics();

        assertThat(statistics.getTotalVisits()).isZero();
        assertThat(statistics.getTotalDuration()).isZero();
        assertThat(statistics.getFirstVisit()).isNull();
    }

    @Test
    void anotherUsersFavorite_isNotFound() {
        assertThat(service.getPlaceDetails("favorite", otherUsersFavoriteId, ownerId, "UTC")).isEmpty();
        assertThat(service.getPlaceVisits("favorite", otherUsersFavoriteId, ownerId, 0, 10, "timestamp", "desc")
                .items()).isEmpty();
    }

    @Test
    void visits_arePagedNewestFirst() {
        PageResponse<PlaceVisitDTO> first = service.getPlaceVisits("favorite", homeId, ownerId, 0, 2, "timestamp", "desc");
        PageResponse<PlaceVisitDTO> second = service.getPlaceVisits("favorite", homeId, ownerId, 1, 2, "timestamp", "desc");

        assertThat(first.items()).extracting(PlaceVisitDTO::getTimestamp).containsExactly(JAN_7, JAN_6);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.items()).extracting(PlaceVisitDTO::getTimestamp).containsExactly(JAN_5);
    }

    @Test
    void visits_canBeSortedByDuration() {
        PageResponse<PlaceVisitDTO> visits = service.getPlaceVisits("favorite", homeId, ownerId, 0, 10,
                "stayDuration", "asc");

        assertThat(visits.items()).extracting(PlaceVisitDTO::getStayDuration).containsExactly(1800L, 3600L, 7200L);
        assertThat(visits.items()).extracting(PlaceVisitDTO::getLocationName).containsOnly("Home");
    }
}
