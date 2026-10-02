package org.github.tess1o.geopulse.streaming.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineTestFixtures;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/location-analytics}.
 *
 * <p>Cities and countries come from {@code timeline_stays.city}/{@code .country}, which the timeline
 * engine fills from reverse geocoding — so {@link GeocodingTestMocks} supplies "Kyiv"/"Ukraine" and
 * a real regeneration run is required before anything is non-empty.
 *
 * <p>Deliberately not asserted: {@code size}, {@code sortBy}, {@code sortDirection}, {@code minVisits}
 * and {@code limit} are all silently clamped or defaulted here. Only {@code page < 0}, a short
 * {@code q}, bad dates and a bad bounding box produce errors.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class LocationAnalyticsContractTest {

    private static final String ANALYTICS = "/api/v1/location-analytics";
    private static final String PASSWORD = "password123";
    private static final String DAY_FROM = "2024-08-15T00:00:00Z";
    private static final String DAY_TO = "2024-08-16T00:00:00Z";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject GpsPointRepository gpsPointRepository;
    @Inject StreamingTimelineGenerationService timelineGenerationService;

    private UserEntity owner;
    private String ownerToken;
    private TimelineTestFixtures fixtures;

    @BeforeEach
    @Transactional
    void setUp() {
        GeocodingTestMocks.install();
        fixtures = TimelineTestFixtures.newScope();
        String email = TestIds.uniqueEmail("analytics-owner");
        owner = userService.registerUser(email, PASSWORD, "Analytics Owner", "UTC");
        ownerToken = authenticationService.authenticate(email, PASSWORD).getAccessToken();
    }

    @Test
    void citiesCountriesAndSearchReflectGeneratedStays() {
        seedTimeline();
        Names names = derivedNames();

        Response cities = authenticated(ownerToken).when().get(ANALYTICS + "/cities");
        assertThat(cities.statusCode()).isEqualTo(200);
        assertThat(cities.jsonPath().getList("cityName", String.class)).contains(names.city());

        Response countries = authenticated(ownerToken).when().get(ANALYTICS + "/countries");
        assertThat(countries.statusCode()).isEqualTo(200);
        assertThat(countries.jsonPath().getList("countryName", String.class)).contains(names.country());

        Response search = authenticated(ownerToken).queryParam("q", names.city()).when().get(ANALYTICS + "/search");
        assertThat(search.statusCode()).isEqualTo(200);
        assertThat(search.jsonPath().getList("name", String.class)).contains(names.city());

        assertThat(authenticated(ownerToken).when().get(ANALYTICS + "/cities/" + names.city()).statusCode())
                .isEqualTo(200);
        assertThat(authenticated(ownerToken).when().get(ANALYTICS + "/countries/" + names.country()).statusCode())
                .isEqualTo(200);
        assertThat(authenticated(ownerToken).when().get(ANALYTICS + "/countries/" + names.country() + "/cities")
                .statusCode()).isEqualTo(200);

        Response mapPlaces = authenticated(ownerToken)
                .queryParam("from", DAY_FROM).queryParam("to", DAY_TO)
                .when().get(ANALYTICS + "/map/places");
        assertThat(mapPlaces.statusCode()).isEqualTo(200);
    }

    @Test
    void visitListingsAndCsvExportsAreServed() {
        seedTimeline();
        Names names = derivedNames();

        Response cityVisits = authenticated(ownerToken).when().get(ANALYTICS + "/cities/" + names.city() + "/visits");
        assertThat(cityVisits.statusCode()).isEqualTo(200);
        assertThat(cityVisits.jsonPath().getInt("page")).isZero();

        Response countryVisits = authenticated(ownerToken).when()
                .get(ANALYTICS + "/countries/" + names.country() + "/visits");
        assertThat(countryVisits.statusCode()).isEqualTo(200);

        Response cityExport = authenticated(ownerToken).when()
                .get(ANALYTICS + "/cities/" + names.city() + "/visits/export");
        assertThat(cityExport.statusCode()).isEqualTo(200);
        assertThat(cityExport.contentType()).startsWith("text/csv");
        assertThat(cityExport.getHeader("Content-Disposition")).isNotBlank();

        Response countryExport = authenticated(ownerToken).when()
                .get(ANALYTICS + "/countries/" + names.country() + "/visits/export");
        assertThat(countryExport.statusCode()).isEqualTo(200);
        assertThat(countryExport.contentType()).startsWith("text/csv");
    }

    /**
     * Reads the city/country the engine actually recorded. A non-blank value is itself the
     * assertion that reverse-geocoded data reached {@code timeline_stays} — without it these fields
     * are null.
     */
    private Names derivedNames() {
        Response cities = authenticated(ownerToken).when().get(ANALYTICS + "/cities");
        assertThat(cities.jsonPath().getList("cityName", String.class))
                .as("the generated stays should carry a reverse-geocoded city")
                .isNotEmpty();
        String city = cities.jsonPath().getString("[0].cityName");
        assertThat(city).isNotBlank();

        Response countries = authenticated(ownerToken).when().get(ANALYTICS + "/countries");
        assertThat(countries.jsonPath().getList("countryName", String.class)).isNotEmpty();
        String country = countries.jsonPath().getString("[0].countryName");
        assertThat(country).isNotBlank();

        return new Names(city, country);
    }

    private record Names(String city, String country) {
    }

    @Test
    void exactErrorCodesForBadQueries() {
        // q must be at least 2 characters
        assertProblemEnvelope(authenticated(ownerToken).queryParam("q", "K").when().get(ANALYTICS + "/search"),
                400, "INVALID_LOCATION_SEARCH");
        assertProblemEnvelope(authenticated(ownerToken).when().get(ANALYTICS + "/search"),
                400, "INVALID_LOCATION_SEARCH");

        // from after to
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("from", DAY_TO).queryParam("to", DAY_FROM)
                        .when().get(ANALYTICS + "/map/places"),
                400, "INVALID_DATE_RANGE");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("from", "nonsense")
                        .when().get(ANALYTICS + "/map/places"),
                400, "INVALID_DATE_RANGE");

        // bbox is all-or-nothing and bounded
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("minLat", 40.0).queryParam("minLon", -74.0)
                        .when().get(ANALYTICS + "/map/places"),
                400, "INVALID_BOUNDING_BOX");
        // a complete bbox whose latitude is out of range
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("minLat", 200.0).queryParam("maxLat", 41.0)
                        .queryParam("minLon", -74.0).queryParam("maxLon", -73.0)
                        .when().get(ANALYTICS + "/map/places"),
                400, "INVALID_BOUNDING_BOX");

        // page is validated before the city is resolved, so an unknown name still yields INVALID_PAGE
        assertProblemEnvelope(authenticated(ownerToken).queryParam("page", -1)
                        .when().get(ANALYTICS + "/cities/Nowhereville/visits"),
                400, "INVALID_PAGE");

        assertProblemEnvelope(authenticated(ownerToken).when().get(ANALYTICS + "/cities/Nowhereville"),
                404, "CITY_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().get(ANALYTICS + "/countries/Nowhere"),
                404, "COUNTRY_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().get(ANALYTICS + "/cities/Nowhereville/visits/export"),
                404, "LOCATION_VISITS_NOT_FOUND");
    }

    private void seedTimeline() {
        QuarkusTransaction.requiringNew().run(() -> {
            TimelineTestFixtures.persist(gpsPointRepository, fixtures.homeToOfficeDay(
                    owner, 40.7589, -73.9851, 40.7505, -73.9934, Instant.parse("2024-08-15T08:00:00Z")));
            TimelineTestFixtures.regenerateTimeline(timelineGenerationService, owner.getId());
        });
    }

    private static RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
}
