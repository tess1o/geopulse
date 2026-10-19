package org.github.tess1o.geopulse.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.timelinelabels.model.dto.CreateTimelineLabelDto;
import org.github.tess1o.geopulse.timelinelabels.service.TimelineLabelService;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.service.TripPlanItemService;
import org.github.tess1o.geopulse.trips.service.TripService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the MCP tools over HTTP for a user in Europe/Kyiv (UTC+2 in March) and checks the exact data they
 * return. Dates passed to the tools are local days, so several seeded items sit just before or after local
 * midnight, where a tool that used UTC days would return a different answer.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class McpToolDataTest {

    private static final String TIMEZONE = "Europe/Kyiv";
    private static final double KYIV_LAT = 50.4501;
    private static final double KYIV_LON = 30.5234;

    private final McpTestClient mcp = new McpTestClient();

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    TimelineSeeder seed;

    @Inject
    TimelineLabelService timelineLabelService;

    @Inject
    TripService tripService;

    @Inject
    TripPlanItemService tripPlanItemService;

    private String token;
    private String homeCountry;
    private String awayCountry;
    private String tripName;
    private long homeId;
    private long tripId;

    /*
     * Local times are Europe/Kyiv (UTC+2).
     * Stays:  Home (homeCountry) Mar 2 08:00 3600s and Mar 5 08:00 7200s; Office (homeCountry) Mar 2 10:00 1800s;
     *         Hotel (awayCountry) Mar 1 00:30 300s (still Feb 28 in UTC) and Mar 10 12:00 600s.
     * Trips:  WALK 1 km at Mar 2 00:30 (Mar 1 in UTC), CAR 5 km at Mar 3 00:30 (Mar 2 in UTC).
     * Notes:  Early Mar 1 23:30, Midnight Mar 2 00:30, Late Mar 2 23:30, Next Mar 3 00:30.
     * Weather: Kyiv 4.0° Mar 2 00:30, Lviv 8.0° Mar 2 14:00, Kyiv 1.0° Mar 3 00:30.
     * Geofence events: ENTER "Arrived" Mar 2 09:00, LEAVE "Left" Mar 2 18:00, ENTER "Arrived again" Mar 3 09:00.
     * Label "Spring break" Mar 10 00:00 – Mar 12 23:59:59; planned trip with one plan item in 2030.
     */
    @BeforeEach
    @Transactional
    void setUp() {
        String unique = TestIds.uniqueValue("mcpdata");
        homeCountry = "Homeland " + unique;
        awayCountry = "Awayland " + unique;

        UserEntity user = seed.user("mcp-data", TIMEZONE);
        FavoritesEntity home = seed.favorite(user, "Home", "Kyiv " + unique, homeCountry, KYIV_LAT, KYIV_LON);
        FavoritesEntity office = seed.favorite(user, "Office", "Kyiv " + unique, homeCountry, 50.4400, 30.5100);
        ReverseGeocodingLocationEntity hotel = seed.geocoding(user, "Hotel " + unique, "Lviv " + unique, awayCountry,
                49.8397, 24.0297);
        homeId = home.getId();

        seed.stay(user, home, Instant.parse("2026-03-02T06:00:00Z"), 3600);
        seed.stay(user, home, Instant.parse("2026-03-05T06:00:00Z"), 7200);
        seed.stay(user, office, Instant.parse("2026-03-02T08:00:00Z"), 1800);
        seed.stay(user, hotel, Instant.parse("2026-02-28T22:30:00Z"), 300);
        seed.stay(user, hotel, Instant.parse("2026-03-10T10:00:00Z"), 600);
        seed.trip(user, Instant.parse("2026-03-01T22:30:00Z"), 1000, 600, "WALK");
        seed.trip(user, Instant.parse("2026-03-02T22:30:00Z"), 5000, 900, "CAR");

        seed.note(user, "Early", Instant.parse("2026-03-01T21:30:00Z"), KYIV_LAT, KYIV_LON);
        seed.note(user, "Midnight", Instant.parse("2026-03-01T22:30:00Z"), KYIV_LAT, KYIV_LON);
        seed.note(user, "Late", Instant.parse("2026-03-02T21:30:00Z"), KYIV_LAT, KYIV_LON);
        seed.note(user, "Next", Instant.parse("2026-03-02T22:30:00Z"), KYIV_LAT, KYIV_LON);

        seed.weatherSample(user, Instant.parse("2026-03-01T22:30:00Z"), KYIV_LAT, KYIV_LON, 4.0);
        seed.weatherSample(user, Instant.parse("2026-03-02T12:00:00Z"), 49.8397, 24.0297, 8.0);
        seed.weatherSample(user, Instant.parse("2026-03-02T22:30:00Z"), KYIV_LAT, KYIV_LON, 1.0);

        GeofenceRuleEntity work = seed.geofenceRule(user, "Work");
        seed.geofenceEvent(user, work, GeofenceEventType.ENTER, "Arrived", Instant.parse("2026-03-02T07:00:00Z"));
        seed.geofenceEvent(user, work, GeofenceEventType.LEAVE, "Left", Instant.parse("2026-03-02T16:00:00Z"));
        seed.geofenceEvent(user, work, GeofenceEventType.ENTER, "Arrived again", Instant.parse("2026-03-03T07:00:00Z"));

        CreateTimelineLabelDto label = new CreateTimelineLabelDto();
        label.setName("Spring break");
        label.setStartTime(Instant.parse("2026-03-09T22:00:00Z"));
        label.setEndTime(Instant.parse("2026-03-12T21:59:59Z"));
        timelineLabelService.createTimelineLabel(user.getId(), label);

        tripName = "Rome " + unique;
        CreateTripDto trip = new CreateTripDto();
        trip.setName(tripName);
        trip.setStartTime(Instant.parse("2030-04-01T00:00:00Z"));
        trip.setEndTime(Instant.parse("2030-04-05T00:00:00Z"));
        tripId = tripService.createTrip(user.getId(), trip).getId();
        CreateTripPlanItemDto item = new CreateTripPlanItemDto();
        item.setTitle("Colosseum");
        item.setLatitude(41.8902);
        item.setLongitude(12.4922);
        item.setPlannedDay(LocalDate.parse("2030-04-02"));
        tripPlanItemService.createTripPlanItem(user.getId(), tripId, item);

        token = apiTokenService.createToken(user.getId(), "MCP data", Instant.now().plusSeconds(3600),
                List.of(ApiTokenScope.READ.value()), "127.0.0.1").getToken();
    }

    @Test
    void searchNotes_coversTheLocalDay() throws Exception {
        JsonNode result = mcp.callToolJson(token, "searchNotes", Map.of("startDate", "2026-03-02", "endDate", "2026-03-02"));

        assertThat(texts(result.path("notes"), "title")).containsExactly("Midnight", "Late");
    }

    @Test
    void getStatistics_coversTheLocalDay() throws Exception {
        JsonNode stats = mcp.callToolJson(token, "getStatistics", Map.of("startDate", "2026-03-02", "endDate", "2026-03-02"));

        assertThat(stats.path("totalDistanceMeters").asDouble()).as("only the 1 km walk at 00:30 local").isEqualTo(1000.0);
        assertThat(stats.path("timeMovingSeconds").asLong()).isEqualTo(600);
    }

    @Test
    void getWeather_coversTheLocalDayAndFiltersByRadius() throws Exception {
        JsonNode day = mcp.callToolJson(token, "getWeather", Map.of("startDate", "2026-03-02", "endDate", "2026-03-02"));
        JsonNode nearKyiv = mcp.callToolJson(token, "getWeather", Map.of("startDate", "2026-03-02", "endDate", "2026-03-02",
                "latitude", KYIV_LAT, "longitude", KYIV_LON, "radiusKm", 10));
        JsonNode capped = mcp.callToolJson(token, "getWeather", Map.of("startDate", "2026-03-01", "endDate", "2026-03-03",
                "limit", 2));

        assertThat(doubles(day.path("samples"), "temperature")).containsExactly(4.0, 8.0);
        assertThat(day.path("totalSamples").asInt()).isEqualTo(2);
        assertThat(doubles(nearKyiv.path("samples"), "temperature")).containsExactly(4.0);
        assertThat(doubles(capped.path("samples"), "temperature")).as("first and last kept").containsExactly(4.0, 1.0);
        assertThat(capped.path("totalSamples").asInt()).isEqualTo(3);
        assertThat(capped.path("thinned").asBoolean()).isTrue();
    }

    @Test
    void listGeofenceEvents_coversTheLocalDayNewestFirst() throws Exception {
        JsonNode day = mcp.callToolJson(token, "listGeofenceEvents", Map.of("startDate", "2026-03-02", "endDate", "2026-03-02"));
        JsonNode enters = mcp.callToolJson(token, "listGeofenceEvents", Map.of("eventType", "ENTER"));

        assertThat(texts(day.path("items"), "title")).containsExactly("Left", "Arrived");
        assertThat(texts(enters.path("items"), "title")).containsExactly("Arrived again", "Arrived");
    }

    @Test
    void listTimelineLabels_overlapUsesLocalDays() throws Exception {
        List<JsonNode> lastDay = mcp.callToolItems(token, "listTimelineLabels",
                Map.of("startDate", "2026-03-12", "endDate", "2026-03-12"));
        List<JsonNode> dayAfter = mcp.callToolItems(token, "listTimelineLabels",
                Map.of("startDate", "2026-03-13", "endDate", "2026-03-13"));

        assertThat(lastDay).extracting(label -> label.path("name").asText()).containsExactly("Spring break");
        assertThat(dayAfter).isEmpty();
    }

    @Test
    void listTripsAndGetTripDetails_returnThePlannedTrip() throws Exception {
        List<JsonNode> upcoming = mcp.callToolItems(token, "listTrips", Map.of("status", "UPCOMING"));
        List<JsonNode> completed = mcp.callToolItems(token, "listTrips", Map.of("status", "COMPLETED"));
        JsonNode details = mcp.callToolJson(token, "getTripDetails", Map.of("tripId", tripId));

        assertThat(upcoming).extracting(trip -> trip.path("name").asText()).containsExactly(tripName);
        assertThat(completed).isEmpty();
        assertThat(details.path("trip").path("name").asText()).isEqualTo(tripName);
        assertThat(details.path("summary").path("planItemsTotal").asInt()).isEqualTo(1);
        assertThat(details.path("summary").path("planItemsVisited").asInt()).isZero();
        assertThat(texts(details.path("planItems"), "title")).containsExactly("Colosseum");
    }

    @Test
    void explainTimelineEvent_explainsAStayAndATripFromTheTimeline() throws Exception {
        List<JsonNode> stays = mcp.callToolItems(token, "getVisitedLocations",
                Map.of("startDate", "2026-03-02", "endDate", "2026-03-02"));
        JsonNode office = stays.stream().filter(stay -> stay.path("locationName").asText().equals("Office"))
                .findFirst().orElseThrow();
        List<JsonNode> trips = mcp.callToolItems(token, "getTripMovements",
                Map.of("startDate", "2026-03-02", "endDate", "2026-03-03"));
        JsonNode car = trips.stream().filter(trip -> trip.path("movementType").asText().equals("CAR"))
                .findFirst().orElseThrow();

        JsonNode stayExplanation = mcp.callToolJson(token, "explainTimelineEvent",
                Map.of("kind", "STAY", "id", office.path("id").asLong()));
        JsonNode tripExplanation = mcp.callToolJson(token, "explainTimelineEvent",
                Map.of("kind", "TRIP", "id", car.path("id").asLong()));

        assertThat(stayExplanation.path("kind").asText()).isEqualTo("STAY");
        assertThat(stayExplanation.path("id").asLong()).isEqualTo(office.path("id").asLong());
        assertThat(stayExplanation.path("startTime").asText()).isEqualTo("2026-03-02T08:00:00Z");
        assertThat(stayExplanation.path("endTime").asText()).isEqualTo("2026-03-02T08:30:00Z");
        assertThat(tripExplanation.path("kind").asText()).isEqualTo("TRIP");
        assertThat(tripExplanation.path("startTime").asText()).isEqualTo("2026-03-02T22:30:00Z");
    }

    @Test
    void getPlaceVisits_pagesAndSortsTheVisits() throws Exception {
        JsonNode byDuration = mcp.callToolJson(token, "getPlaceVisits", Map.of("type", "favorite", "id", homeId,
                "sortBy", "STAYDURATION", "sortDirection", "ASC"));
        JsonNode firstPage = mcp.callToolJson(token, "getPlaceVisits", Map.of("type", "favorite", "id", homeId, "size", 1));

        assertThat(longs(byDuration.path("items"), "stayDuration")).containsExactly(3600L, 7200L);
        assertThat(firstPage.path("items")).hasSize(1);
        assertThat(firstPage.path("items").get(0).path("timestamp").asText()).as("newest first")
                .isEqualTo("2026-03-05T06:00:00Z");
        assertThat(firstPage.path("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void countryTools_returnTheSeededTotals() throws Exception {
        JsonNode all = mcp.callToolJson(token, "listVisitedCountries", Map.of());
        JsonNode capped = mcp.callToolJson(token, "listVisitedCountries", Map.of("limit", 1));
        JsonNode home = mcp.callToolJson(token, "getCountryDetails", Map.of("name", homeCountry));

        assertThat(texts(all.path("items"), "countryName")).containsExactly(homeCountry, awayCountry);
        assertThat(longs(all.path("items"), "totalDuration")).containsExactly(12600L, 900L);
        assertThat(all.path("truncated").asBoolean()).isFalse();
        assertThat(texts(capped.path("items"), "countryName")).containsExactly(homeCountry);
        assertThat(capped.path("totalCount").asInt()).isEqualTo(2);
        assertThat(capped.path("truncated").asBoolean()).isTrue();
        assertThat(home.path("statistics").path("totalVisits").asLong()).isEqualTo(3);
    }

    @Test
    void getPeriodDigest_usesTheUsersTimezone() throws Exception {
        JsonNode february = mcp.callToolJson(token, "getPeriodDigest", Map.of("year", 2026, "month", 2));
        JsonNode march = mcp.callToolJson(token, "getPeriodDigest", Map.of("year", 2026, "month", 3));

        assertThat(february.path("metrics").path("stayCount").asInt())
                .as("the Hotel stay at Feb 28 22:30 UTC is March 1 locally").isZero();
        assertThat(march.path("metrics").path("stayCount").asInt()).isEqualTo(5);
        assertThat(march.path("metrics").path("tripCount").asInt()).isEqualTo(2);
        assertThat(march.path("metrics").path("totalDistance").asDouble()).isEqualTo(6000.0);
        assertThat(march.has("activityChart")).as("charts are left out").isFalse();
    }

    @Test
    void getJourneyInsights_returnsLifetimeDistance() throws Exception {
        JsonNode insights = mcp.callToolJson(token, "getJourneyInsights", Map.of());

        assertThat(insights.path("distanceTraveled").path("byCar").asInt()).isEqualTo(5);
        assertThat(insights.path("distanceTraveled").path("byWalk").asInt()).isEqualTo(1);
    }

    @Test
    void integrationsThatAreNotSetUp_areReportedAsErrors() throws Exception {
        JsonNode coverage = mcp.callTool(token, "getCoverageSummary", Map.of());
        JsonNode photos = mcp.callTool(token, "searchPhotos", Map.of("startDate", "2026-03-02", "endDate", "2026-03-02"));

        assertThat(coverage.path("isError").asBoolean()).isTrue();
        assertThat(McpTestClient.text(coverage)).startsWith("COVERAGE_DISABLED");
        assertThat(photos.path("isError").asBoolean()).isTrue();
        assertThat(McpTestClient.text(photos)).startsWith("IMMICH_NOT_CONFIGURED");
    }

    private static List<String> texts(JsonNode items, String field) {
        List<String> values = new ArrayList<>();
        items.forEach(item -> values.add(item.path(field).asText()));
        return values;
    }

    private static List<Double> doubles(JsonNode items, String field) {
        List<Double> values = new ArrayList<>();
        items.forEach(item -> values.add(item.path(field).asDouble()));
        return values;
    }

    private static List<Long> longs(JsonNode items, String field) {
        List<Long> values = new ArrayList<>();
        items.forEach(item -> values.add(item.path(field).asLong()));
        return values;
    }
}
