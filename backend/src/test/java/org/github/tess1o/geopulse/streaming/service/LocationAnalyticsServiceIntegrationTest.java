package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.streaming.model.dto.CityDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CityInCountryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CitySummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CountryDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CountrySummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationSearchResultDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TopPlaceInLocationDTO;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * City, country and search results are computed from the seeded stays below, where every number can be worked
 * out by hand. A second user with a stay in the same city checks that nothing leaks across users.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class LocationAnalyticsServiceIntegrationTest {

    @Inject
    LocationAnalyticsService service;

    @Inject
    TimelineSeeder seed;

    private UUID ownerId;
    private String token;
    private String alpha;
    private String beta;
    private String gamma;
    private String countryX;
    private String countryY;
    private long homeId;

    /*
     * Owner's stays:
     *   alpha (countryX): Home 3600s on Jan 5, Home 7200s on Jan 6, Cafe 1800s on Jan 5
     *   beta  (countryX): Museum 5400s on Jan 7
     *   gamma (countryY): Harbor 600s on Feb 1
     */
    @BeforeEach
    @Transactional
    void setUp() {
        token = TestIds.uniqueValue("geo").replaceAll("[^A-Za-z0-9]", "");
        alpha = token + " Alpha";
        beta = token + " Beta";
        gamma = token + " Gamma";
        countryX = token + "land";
        countryY = token + "stan";

        UserEntity owner = seed.user("analytics-owner", "UTC");
        ownerId = owner.getId();
        FavoritesEntity home = seed.favorite(owner, "Home " + token, alpha, countryX, 38.7200, -9.1400);
        FavoritesEntity cafe = seed.favorite(owner, "Cafe " + token, alpha, countryX, 38.7300, -9.1500);
        ReverseGeocodingLocationEntity museum = seed.geocoding(owner, "Museum " + token, beta, countryX, 41.1500, -8.6100);
        ReverseGeocodingLocationEntity harbor = seed.geocoding(owner, "Harbor " + token, gamma, countryY, 36.1400, -5.3500);
        homeId = home.getId();

        seed.stay(owner, home, Instant.parse("2026-01-05T08:00:00Z"), 3600);
        seed.stay(owner, home, Instant.parse("2026-01-06T08:00:00Z"), 7200);
        seed.stay(owner, cafe, Instant.parse("2026-01-05T12:00:00Z"), 1800);
        seed.stay(owner, museum, Instant.parse("2026-01-07T10:00:00Z"), 5400);
        seed.stay(owner, harbor, Instant.parse("2026-02-01T10:00:00Z"), 600);

        UserEntity other = seed.user("analytics-other", "UTC");
        FavoritesEntity otherPlace = seed.favorite(other, "Other " + token, alpha, countryX, 38.7210, -9.1410);
        seed.stay(other, otherPlace, Instant.parse("2026-01-05T09:00:00Z"), 9999);
    }

    @Test
    void getAllCities_aggregatesVisitsDurationAndPlacesPerCity() {
        List<CitySummaryDTO> cities = service.getAllCities(ownerId);

        assertThat(cities)
                .extracting(CitySummaryDTO::getCityName, CitySummaryDTO::getCountry, CitySummaryDTO::getVisitCount,
                        CitySummaryDTO::getTotalDuration, CitySummaryDTO::getUniquePlaces)
                .containsExactlyInAnyOrder(
                        tuple(alpha, countryX, 3L, 12600L, 2),
                        tuple(beta, countryX, 1L, 5400L, 1),
                        tuple(gamma, countryY, 1L, 600L, 1));
        assertThat(cities.get(0).getCityName()).as("most visited first").isEqualTo(alpha);
    }

    @Test
    void getAllCountries_aggregatesVisitsCitiesDurationAndPlaces() {
        List<CountrySummaryDTO> countries = service.getAllCountries(ownerId);

        assertThat(countries)
                .extracting(CountrySummaryDTO::getCountryName, CountrySummaryDTO::getVisitCount,
                        CountrySummaryDTO::getCityCount, CountrySummaryDTO::getTotalDuration,
                        CountrySummaryDTO::getUniquePlaces)
                .containsExactly(
                        tuple(countryX, 4L, 2, 18000L, 3),
                        tuple(countryY, 1L, 1, 600L, 1));
    }

    @Test
    void getCityDetails_returnsStatisticsAndTopPlacesForTheCity() {
        CityDetailsDTO city = service.getCityDetails(ownerId, alpha).orElseThrow();

        assertThat(city.getCountry()).isEqualTo(countryX);
        assertThat(city.getStatistics().getTotalVisits()).isEqualTo(3);
        assertThat(city.getStatistics().getTotalDuration()).isEqualTo(12600);
        assertThat(city.getStatistics().getAverageDuration()).isEqualTo(4200);
        assertThat(city.getStatistics().getMinDuration()).isEqualTo(1800);
        assertThat(city.getStatistics().getMaxDuration()).isEqualTo(7200);
        assertThat(city.getStatistics().getFirstVisit()).isEqualTo(Instant.parse("2026-01-05T08:00:00Z"));
        assertThat(city.getStatistics().getLastVisit()).isEqualTo(Instant.parse("2026-01-06T08:00:00Z"));
        assertThat(city.getStatistics().getUniquePlaces()).isEqualTo(2);
        assertThat(city.getTopPlaces())
                .extracting(TopPlaceInLocationDTO::getType, TopPlaceInLocationDTO::getId,
                        TopPlaceInLocationDTO::getVisitCount, TopPlaceInLocationDTO::getTotalDuration)
                .first()
                .isEqualTo(tuple("favorite", homeId, 2L, 10800L));
        assertThat(city.getTopPlaces()).hasSize(2);
    }

    @Test
    void getCountryDetails_returnsStatisticsAndItsCities() {
        CountryDetailsDTO country = service.getCountryDetails(ownerId, countryX).orElseThrow();

        assertThat(country.getStatistics().getTotalVisits()).isEqualTo(4);
        assertThat(country.getStatistics().getTotalDuration()).isEqualTo(18000);
        assertThat(country.getStatistics().getAverageDuration()).isEqualTo(4500);
        assertThat(country.getStatistics().getMinDuration()).isEqualTo(1800);
        assertThat(country.getStatistics().getMaxDuration()).isEqualTo(7200);
        assertThat(country.getStatistics().getFirstVisit()).isEqualTo(Instant.parse("2026-01-05T08:00:00Z"));
        assertThat(country.getStatistics().getLastVisit()).isEqualTo(Instant.parse("2026-01-07T10:00:00Z"));
        assertThat(country.getStatistics().getUniquePlaces()).isEqualTo(3);
        assertThat(country.getCities())
                .extracting(CityInCountryDTO::getCityName, CityInCountryDTO::getVisitCount,
                        CityInCountryDTO::getTotalDuration)
                .containsExactly(tuple(alpha, 3L, 12600L), tuple(beta, 1L, 5400L));
    }

    @Test
    void cityAndCountryWithoutVisitsAreEmpty() {
        assertThat(service.getCityDetails(ownerId, token + " Nowhere")).isEmpty();
        assertThat(service.getCountryDetails(ownerId, token + "ia")).isEmpty();
    }

    @Test
    void search_findsPlacesCitiesAndCountriesRankedByVisits() {
        List<LocationSearchResultDTO> results = service.search(ownerId, token, null);

        assertThat(results)
                .extracting(LocationSearchResultDTO::getType, LocationSearchResultDTO::getName,
                        LocationSearchResultDTO::getVisitCount)
                .containsExactlyInAnyOrder(
                        tuple("place", "Home " + token, 2),
                        tuple("place", "Cafe " + token, 1),
                        tuple("place", "Museum " + token, 1),
                        tuple("place", "Harbor " + token, 1),
                        tuple("city", alpha, 3),
                        tuple("city", beta, 1),
                        tuple("city", gamma, 1),
                        tuple("country", countryX, 4),
                        tuple("country", countryY, 1));
        assertThat(results.get(0)).extracting(LocationSearchResultDTO::getType, LocationSearchResultDTO::getVisitCount)
                .containsExactly("country", 4);
    }

    @Test
    void search_typeFilterReturnsOnlyThatType() {
        List<LocationSearchResultDTO> cities = service.search(ownerId, token, "city");
        assertThat(cities).extracting(LocationSearchResultDTO::getName).containsExactlyInAnyOrder(alpha, beta, gamma);
        assertThat(cities.get(0).getName()).isEqualTo(alpha);
        assertThat(service.search(ownerId, "Home " + token, "place"))
                .extracting(LocationSearchResultDTO::getCategory, LocationSearchResultDTO::getId)
                .containsExactly(tuple("favorite", homeId));
    }
}
