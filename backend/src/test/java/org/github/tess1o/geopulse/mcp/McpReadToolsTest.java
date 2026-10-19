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
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the read-only MCP tools over HTTP with a {@code read}-scoped token and checks that each user only sees
 * their own data.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class McpReadToolsTest {

    private static final List<String> EXPECTED_TOOLS = List.of(
            "getTodayDate", "queryTimeline", "getVisitedLocations", "getTripMovements", "getStayStats",
            "getTripStats", "getRoutePatterns", "listAccessibleTimelineFriends", "listAccessibleLiveFriends",
            "getFriendLiveLocation",
            "getCurrentStatus", "searchVisitedPlaces", "getPlaceDetails", "getPlaceVisits", "listVisitedCities",
            "listVisitedCountries", "getCityDetails", "getCountryDetails", "listFavoritePlaces",
            "getPeriodDigest", "getStatistics", "getJourneyInsights", "getCoverageSummary",
            "listTrips", "getTripDetails", "listTimelineLabels", "searchNotes", "searchPhotos", "getWeather",
            "listGeofenceEvents", "explainTimelineEvent", "getGpsStatus");

    private static final Instant STAY_START = Instant.parse("2026-01-10T08:00:00Z");

    private final McpTestClient mcp = new McpTestClient();

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    TimelineSeeder seed;

    private String ownerToken;
    private String otherToken;
    private String ownerCity;
    private long ownerFavoriteId;
    private long otherFavoriteId;

    @BeforeEach
    @Transactional
    void setup() {
        ownerCity = "Mcpville " + TestIds.uniqueEmail("city").hashCode();
        UserEntity owner = seed.user("mcp-owner", "UTC");
        FavoritesEntity home = seed.favorite(owner, "Home", ownerCity, "Testland", 50.4501, 30.5234);
        seed.stay(owner, home, STAY_START, 3600);
        ownerFavoriteId = home.getId();

        UserEntity other = seed.user("mcp-other", "UTC");
        otherFavoriteId = seed.favorite(other, "Secret Base", "Elsewhere", "Testland", 48.8566, 2.3522).getId();

        ownerToken = readToken(owner);
        otherToken = readToken(other);
    }

    @Test
    void toolsList_containsEveryToolMarkedReadOnly() throws Exception {
        JsonNode tools = mcp.listTools(ownerToken);

        Map<String, JsonNode> byName = new HashMap<>();
        tools.forEach(tool -> byName.put(tool.path("name").asText(), tool));
        assertThat(byName.keySet()).containsAll(EXPECTED_TOOLS);
        for (String name : EXPECTED_TOOLS) {
            JsonNode annotations = byName.get(name).path("annotations");
            assertThat(annotations.path("readOnlyHint").asBoolean()).as(name + " readOnlyHint").isTrue();
            assertThat(annotations.path("destructiveHint").asBoolean(true)).as(name + " destructiveHint").isFalse();
        }
    }

    @Test
    void listFavoritePlaces_returnsOnlyTheCallersFavorites() throws Exception {
        JsonNode owner = mcp.callToolJson(ownerToken, "listFavoritePlaces", Map.of());
        JsonNode other = mcp.callToolJson(otherToken, "listFavoritePlaces", Map.of());

        assertThat(names(owner.path("points"))).containsExactly("Home");
        assertThat(names(other.path("points"))).containsExactly("Secret Base");
    }

    @Test
    void getPlaceDetails_ownFavoriteHasStatisticsAndOtherUsersFavoriteIsNotFound() throws Exception {
        JsonNode details = mcp.callToolJson(ownerToken, "getPlaceDetails",
                Map.of("type", "favorite", "id", ownerFavoriteId));
        assertThat(details.path("locationName").asText()).isEqualTo("Home");
        assertThat(details.path("statistics").path("totalVisits").asLong()).isEqualTo(1);

        JsonNode denied = mcp.callTool(ownerToken, "getPlaceDetails", Map.of("type", "favorite", "id", otherFavoriteId));
        assertThat(denied.path("isError").asBoolean()).isTrue();
        assertThat(McpTestClient.text(denied)).startsWith("PLACE_NOT_FOUND");
    }

    @Test
    void searchVisitedPlacesAndCityDetails_findTheCallersCity() throws Exception {
        List<JsonNode> results = mcp.callToolItems(ownerToken, "searchVisitedPlaces", Map.of("query", ownerCity, "type", "city"));
        assertThat(results).extracting(item -> item.path("name").asText()).contains(ownerCity);

        JsonNode city = mcp.callToolJson(ownerToken, "getCityDetails", Map.of("name", ownerCity));
        assertThat(city.path("cityName").asText()).isEqualTo(ownerCity);

        JsonNode otherCity = mcp.callTool(otherToken, "getCityDetails", Map.of("name", ownerCity));
        assertThat(otherCity.path("isError").asBoolean()).isTrue();
        assertThat(McpTestClient.text(otherCity)).startsWith("CITY_NOT_FOUND");
    }

    @Test
    void queryTimeline_includesStayIds() throws Exception {
        JsonNode timeline = mcp.callToolJson(ownerToken, "queryTimeline",
                Map.of("startDate", "2026-01-10", "endDate", "2026-01-10"));

        assertThat(timeline.path("stays")).hasSize(1);
        assertThat(timeline.path("stays").get(0).path("id").isIntegralNumber()).isTrue();
    }

    @Test
    void summaryAndStatusTools_answerForTheCaller() throws Exception {
        assertThat(mcp.callToolJson(ownerToken, "getCurrentStatus", Map.of()).path("state").asText()).isNotBlank();
        assertThat(mcp.callToolJson(ownerToken, "getPeriodDigest", Map.of("year", 2026, "month", 1))
                .path("period").isMissingNode()).isFalse();
        assertThat(mcp.callToolJson(ownerToken, "getStatistics", Map.of("startDate", "2026-01-01", "endDate", "2026-01-31"))
                .has("totalDistanceMeters")).isTrue();
        assertThat(mcp.callToolJson(ownerToken, "getGpsStatus", Map.of()).path("hasGpsData").asBoolean(true)).isFalse();
    }

    @Test
    void invalidArguments_areReportedAsToolErrors() throws Exception {
        JsonNode result = mcp.callTool(ownerToken, "getStatistics", Map.of("startDate", "2026-02-01", "endDate", "2026-01-01"));

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(McpTestClient.text(result)).startsWith("INVALID_ARGUMENT");
    }

    private static List<String> names(JsonNode items) {
        List<String> names = new ArrayList<>();
        items.forEach(item -> names.add(item.path("name").asText()));
        return names;
    }

    private String readToken(UserEntity user) {
        return apiTokenService.createToken(user.getId(), "MCP read", Instant.now().plusSeconds(3600),
                List.of(ApiTokenScope.READ.value()), "127.0.0.1").getToken();
    }
}
