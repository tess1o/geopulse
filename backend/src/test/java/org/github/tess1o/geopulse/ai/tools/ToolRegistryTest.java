package org.github.tess1o.geopulse.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.ai.model.AIFriendLiveCandidateDTO;
import org.github.tess1o.geopulse.ai.model.AIFriendLiveLocationDTO;
import org.github.tess1o.geopulse.ai.model.AIMovementTimelineDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineFriendCandidateDTO;
import org.github.tess1o.geopulse.ai.service.AIContextTools;
import org.github.tess1o.geopulse.ai.service.AIFriendLiveTools;
import org.github.tess1o.geopulse.ai.service.AIInsightsTools;
import org.github.tess1o.geopulse.ai.service.AIPlacesTools;
import org.github.tess1o.geopulse.ai.service.AITimelineTools;
import org.github.tess1o.geopulse.ai.service.AIToolException;
import org.github.tess1o.geopulse.ai.service.SimpleAITools;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class ToolRegistryTest {

    private ToolRegistry toolRegistry;
    private AIFriendLiveTools aiFriendLiveTools;
    private AITimelineTools aiTimelineTools;
    private SimpleAITools simpleAITools;
    private AIPlacesTools aiPlacesTools;
    private AIInsightsTools aiInsightsTools;
    private AIContextTools aiContextTools;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        aiFriendLiveTools = Mockito.mock(AIFriendLiveTools.class);
        aiTimelineTools = Mockito.mock(AITimelineTools.class);
        simpleAITools = Mockito.mock(SimpleAITools.class);
        aiPlacesTools = Mockito.mock(AIPlacesTools.class);
        aiInsightsTools = Mockito.mock(AIInsightsTools.class);
        aiContextTools = Mockito.mock(AIContextTools.class);
        objectMapper = new ObjectMapper();

        toolRegistry = new ToolRegistry(aiTimelineTools, aiFriendLiveTools, simpleAITools,
                aiPlacesTools, aiInsightsTools, aiContextTools, objectMapper);
        toolRegistry.registerTools();
    }

    @Test
    void queryTimeline_defaultsToSelfScopeWhenNoTargetProvided() {
        when(aiTimelineTools.queryTimeline("2026-01-01", "2026-01-02", null, null))
                .thenReturn(new AIMovementTimelineDTO(UUID.randomUUID()));

        toolRegistry.invokeTool("queryTimeline", "{\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-02\"}");

        verify(aiTimelineTools).queryTimeline(eq("2026-01-01"), eq("2026-01-02"), isNull(), isNull());
    }

    @Test
    void queryTimeline_passesFriendTargetArguments() {
        when(aiTimelineTools.queryTimeline("2026-01-01", "2026-01-02", "FRIEND", "alex@example.com"))
                .thenReturn(new AIMovementTimelineDTO(UUID.randomUUID()));

        toolRegistry.invokeTool(
                "queryTimeline",
                "{\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-02\",\"targetScope\":\"FRIEND\",\"targetUser\":\"alex@example.com\"}"
        );

        verify(aiTimelineTools).queryTimeline("2026-01-01", "2026-01-02", "FRIEND", "alex@example.com");
    }

    @Test
    void queryTimeline_structuredToolErrorIsSerialized() throws Exception {
        AIToolException toolException = new AIToolException(
                "FRIEND_SELECTION_REQUIRED",
                "Multiple friends shared timeline access. Specify one.",
                java.util.Map.of("candidates", List.of(
                        AITimelineFriendCandidateDTO.builder()
                                .userId(UUID.randomUUID())
                                .email("a@example.com")
                                .fullName("Alice")
                                .timelineAccessGranted(true)
                                .build()
                ))
        );

        when(aiTimelineTools.queryTimeline("2026-01-01", "2026-01-02", "FRIEND", null))
                .thenThrow(toolException);

        String response = toolRegistry.invokeTool(
                "queryTimeline",
                "{\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-02\",\"targetScope\":\"FRIEND\"}"
        );

        JsonNode json = objectMapper.readTree(response);
        assertEquals("FRIEND_SELECTION_REQUIRED", json.path("error").path("code").asText());
        assertTrue(json.path("error").path("candidates").isArray());
    }

    @Test
    void listAccessibleTimelineFriends_toolIsRegisteredAndInvoked() {
        when(aiTimelineTools.listAccessibleTimelineFriends()).thenReturn(List.of(
                AITimelineFriendCandidateDTO.builder()
                        .userId(UUID.randomUUID())
                        .email("friend@example.com")
                        .fullName("Friend User")
                        .timelineAccessGranted(true)
                        .build()
        ));

        String response = toolRegistry.invokeTool("listAccessibleTimelineFriends", "{}");

        verify(aiTimelineTools).listAccessibleTimelineFriends();
        assertTrue(response.contains("friend@example.com"));
    }

    @Test
    void toolDefinitions_includeNewFriendHelperTool() {
        List<String> toolNames = toolRegistry.getAllToolDefinitions().stream()
                .map(def -> def.function().name())
                .collect(Collectors.toList());

        assertTrue(toolNames.contains("listAccessibleTimelineFriends"));
        assertTrue(toolNames.contains("listAccessibleLiveFriends"));
        assertTrue(toolNames.contains("getFriendLiveLocation"));
    }

    @Test
    void listAccessibleLiveFriends_toolIsRegisteredAndInvoked() {
        when(aiFriendLiveTools.listAccessibleLiveFriends()).thenReturn(List.of(
                AIFriendLiveCandidateDTO.builder()
                        .friendId(UUID.randomUUID())
                        .email("live@example.com")
                        .fullName("Live Friend")
                        .liveLocationAccessGranted(true)
                        .build()
        ));

        String response = toolRegistry.invokeTool("listAccessibleLiveFriends", "{}");

        verify(aiFriendLiveTools).listAccessibleLiveFriends();
        assertTrue(response.contains("live@example.com"));
    }

    @Test
    void getFriendLiveLocation_passesTargetUserArgument() {
        when(aiFriendLiveTools.getFriendLiveLocation("alex@example.com")).thenReturn(
                AIFriendLiveLocationDTO.builder()
                        .friendId(UUID.randomUUID())
                        .email("alex@example.com")
                        .latitude(10.0)
                        .longitude(20.0)
                        .build()
        );

        toolRegistry.invokeTool(
                "getFriendLiveLocation",
                "{\"targetUser\":\"alex@example.com\"}"
        );

        verify(aiFriendLiveTools).getFriendLiveLocation("alex@example.com");
    }

    @Test
    void toolDefinitions_includeReadToolsSharedWithMcp() {
        List<String> toolNames = toolRegistry.getAllToolDefinitions().stream()
                .map(def -> def.function().name())
                .toList();

        assertTrue(toolNames.containsAll(List.of(
                "getCurrentStatus", "searchVisitedPlaces", "getPlaceDetails", "getPlaceVisits",
                "listVisitedCities", "listVisitedCountries", "getCityDetails", "getCountryDetails",
                "listFavoritePlaces", "getPeriodDigest", "getStatistics", "getJourneyInsights",
                "getCoverageSummary", "listTrips", "getTripDetails", "listTimelineLabels", "searchNotes",
                "searchPhotos", "getWeather", "listGeofenceEvents", "explainTimelineEvent", "getGpsStatus")));
        assertEquals(toolNames.size(), toolNames.stream().distinct().count());
    }

    @Test
    void getPlaceVisits_parsesNumericArguments() {
        toolRegistry.invokeTool("getPlaceVisits",
                "{\"type\":\"favorite\",\"id\":42,\"page\":\"1\",\"size\":10,\"sortBy\":\"STAYDURATION\"}");

        verify(aiPlacesTools).getPlaceVisits("favorite", 42L, 1, 10, "STAYDURATION", null);
    }

    @Test
    void searchNotes_parsesDecimalArguments() {
        toolRegistry.invokeTool("searchNotes",
                "{\"latitude\":50.45,\"longitude\":\"30.52\",\"radiusMeters\":500}");

        verify(aiContextTools).searchNotes(null, null, 50.45, 30.52, 500.0, null);
    }

    @Test
    void invalidNumber_isReportedAsInvalidArgument() throws Exception {
        String response = toolRegistry.invokeTool("getTripDetails", "{\"tripId\":\"abc\"}");

        JsonNode json = objectMapper.readTree(response);
        assertEquals("INVALID_ARGUMENT", json.path("error").path("code").asText());
        assertEquals("tripId", json.path("error").path("argument").asText());
    }

    @Test
    void domainError_isSerializedWithItsCode() throws Exception {
        when(aiInsightsTools.getCoverageSummary(null))
                .thenThrow(new GeoPulseException(ApiErrorCode.COVERAGE_DISABLED, "Coverage is not enabled"));

        String response = toolRegistry.invokeTool("getCoverageSummary", "{}");

        JsonNode json = objectMapper.readTree(response);
        assertEquals("COVERAGE_DISABLED", json.path("error").path("code").asText());
        assertEquals("Coverage is not enabled", json.path("error").path("message").asText());
    }
}
