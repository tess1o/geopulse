package org.github.tess1o.geopulse.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.client.dto.FunctionDefinition;
import org.github.tess1o.geopulse.ai.client.dto.FunctionSpec;
import org.github.tess1o.geopulse.ai.model.StayGroupBy;
import org.github.tess1o.geopulse.ai.model.TripGroupBy;
import org.github.tess1o.geopulse.ai.service.AIFriendLiveTools;
import org.github.tess1o.geopulse.ai.service.AITimelineTools;
import org.github.tess1o.geopulse.ai.service.AIToolException;
import org.github.tess1o.geopulse.ai.service.SimpleAITools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@ApplicationScoped
@Slf4j
public class ToolRegistry {

    private final AITimelineTools aiTimelineTools;
    private final AIFriendLiveTools aiFriendLiveTools;
    private final SimpleAITools simpleAITools;
    private final ObjectMapper objectMapper;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    @Inject
    public ToolRegistry(AITimelineTools aiTimelineTools,
                        AIFriendLiveTools aiFriendLiveTools,
                        SimpleAITools simpleAITools,
                        ObjectMapper objectMapper) {
        this.aiTimelineTools = aiTimelineTools;
        this.aiFriendLiveTools = aiFriendLiveTools;
        this.simpleAITools = simpleAITools;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void registerTools() {
        register("queryTimeline", this::invokeQueryTimeline, createQueryTimelineSpec());
        register("getVisitedLocations", this::invokeGetVisitedLocations, createGetVisitedLocationsSpec());
        register("getTripMovements", this::invokeTripMovements, createTripMovementsSpec());
        register("getStayStats", this::invokeGetStayStats, createGetStayStatsSpec());
        register("getTripStats", this::invokeGetTripStats, createGetTripStatsSpec());
        register("getRoutePatterns", this::invokeGetRoutePatterns, createRoutePatternsSpec());
        register("listAccessibleTimelineFriends", this::invokeListAccessibleTimelineFriends, createListAccessibleTimelineFriendsSpec());
        register("listAccessibleLiveFriends", this::invokeListAccessibleLiveFriends, createListAccessibleLiveFriendsSpec());
        register("getFriendLiveLocation", this::invokeGetFriendLiveLocation, createGetFriendLiveLocationSpec());
        register("getTodayDate", this::invokeGetTodayDate, createGetTodayDateSpec());

        log.info("Registered {} AI tools", tools.size());
    }

    private void register(String name, Function<String, String> invoker, FunctionDefinition definition) {
        tools.put(name, new RegisteredTool(definition, invoker));
    }

    public List<FunctionDefinition> getAllToolDefinitions() {
        return tools.values().stream()
                .map(RegisteredTool::definition)
                .toList();
    }

    public String invokeTool(String name, String argumentsJson) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool.invoker().apply(argumentsJson);
    }

    private String invokeQueryTimeline(String argumentsJson) {
        try {
            JsonNode args = parseArgs(argumentsJson);
            String startDate = getRequiredText(args, "startDate");
            String endDate = getRequiredText(args, "endDate");
            String targetScope = getOptionalText(args, "targetScope");
            String targetUser = getOptionalText(args, "targetUser");

            Object result = aiTimelineTools.queryTimeline(startDate, endDate, targetScope, targetUser);
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking queryTimeline", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeGetVisitedLocations(String argumentsJson) {
        try {
            JsonNode args = parseArgs(argumentsJson);
            String startDate = getRequiredText(args, "startDate");
            String endDate = getRequiredText(args, "endDate");
            String targetScope = getOptionalText(args, "targetScope");
            String targetUser = getOptionalText(args, "targetUser");

            Object result = aiTimelineTools.getVisitedLocations(startDate, endDate, targetScope, targetUser);
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking getVisitedLocations", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeTripMovements(String argumentsJson) {
        try {
            JsonNode args = parseArgs(argumentsJson);
            String startDate = getRequiredText(args, "startDate");
            String endDate = getRequiredText(args, "endDate");
            String targetScope = getOptionalText(args, "targetScope");
            String targetUser = getOptionalText(args, "targetUser");

            Object result = aiTimelineTools.getTripMovements(startDate, endDate, targetScope, targetUser);
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking getTripMovements", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeGetStayStats(String argumentsJson) {
        try {
            JsonNode args = parseArgs(argumentsJson);
            String startDate = getRequiredText(args, "startDate");
            String endDate = getRequiredText(args, "endDate");
            String groupByStr = getRequiredText(args, "groupBy");
            String targetScope = getOptionalText(args, "targetScope");
            String targetUser = getOptionalText(args, "targetUser");
            StayGroupBy groupBy = StayGroupBy.valueOf(groupByStr);

            Object result = aiTimelineTools.getStayStats(startDate, endDate, groupBy, targetScope, targetUser);
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking getStayStats", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeGetTripStats(String argumentsJson) {
        try {
            JsonNode args = parseArgs(argumentsJson);
            String startDate = getRequiredText(args, "startDate");
            String endDate = getRequiredText(args, "endDate");
            String groupByStr = getRequiredText(args, "groupBy");
            String targetScope = getOptionalText(args, "targetScope");
            String targetUser = getOptionalText(args, "targetUser");
            TripGroupBy groupBy = TripGroupBy.valueOf(groupByStr);

            Object result = aiTimelineTools.getTripStats(startDate, endDate, groupBy, targetScope, targetUser);
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking getTripStats", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeGetRoutePatterns(String argumentsJson) {
        try {
            JsonNode args = parseArgs(argumentsJson);
            String startDate = getRequiredText(args, "startDate");
            String endDate = getRequiredText(args, "endDate");
            String targetScope = getOptionalText(args, "targetScope");
            String targetUser = getOptionalText(args, "targetUser");

            Object result = aiTimelineTools.getRoutePatterns(startDate, endDate, targetScope, targetUser);
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking getRoutePatterns", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeListAccessibleTimelineFriends(String argumentsJson) {
        try {
            Object result = aiTimelineTools.listAccessibleTimelineFriends();
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking listAccessibleTimelineFriends", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeListAccessibleLiveFriends(String argumentsJson) {
        try {
            Object result = aiFriendLiveTools.listAccessibleLiveFriends();
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking listAccessibleLiveFriends", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeGetFriendLiveLocation(String argumentsJson) {
        try {
            JsonNode args = parseArgs(argumentsJson);
            String targetUser = getOptionalText(args, "targetUser");
            Object result = aiFriendLiveTools.getFriendLiveLocation(targetUser);
            return objectMapper.writeValueAsString(result);
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (Exception e) {
            log.error("Error invoking getFriendLiveLocation", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String invokeGetTodayDate(String argumentsJson) {
        try {
            String result = simpleAITools.getTodayDate();
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking getTodayDate", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private FunctionDefinition createQueryTimelineSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "queryTimeline",
                        "Gets complete timeline with all stays and trips in chronological order. Use when listing specific events or detailed activity.",
                        createParametersSchema(
                                withTargetProperties(Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)")
                                )),
                                List.of("startDate", "endDate")
                        )
                )
        );
    }

    private FunctionDefinition createGetVisitedLocationsSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "getVisitedLocations",
                        "Lists all places stayed at with timestamps. Use ONLY for listing specific places, NOT for counting. For counts use getStayStats.",
                        createParametersSchema(
                                withTargetProperties(Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)")
                                )),
                                List.of("startDate", "endDate")
                        )
                )
        );
    }

    private FunctionDefinition createTripMovementsSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "getTripMovements",
                        "Lists all individual trips with details. Use ONLY for listing specific trips, NOT for totals or distances. For aggregations use getTripStats.",
                        createParametersSchema(
                                withTargetProperties(Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)")
                                )),
                                List.of("startDate", "endDate")
                        )
                )
        );
    }

    private FunctionDefinition createGetStayStatsSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "getStayStats",
                        "Calculates aggregated stay statistics: total time, visit counts, number of unique cities/locations/countries. Use for counting cities, comparing time spent, and statistical analysis grouped by location, city, country, day, week, or month.",
                        createParametersSchema(
                                withTargetProperties(Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)"),
                                        "groupBy", enumPropertySpec("string",
                                                "Group by: LOCATION_NAME, CITY, COUNTRY, DAY, WEEK, or MONTH",
                                                List.of("LOCATION_NAME", "CITY", "COUNTRY", "DAY", "WEEK", "MONTH"))
                                )),
                                List.of("startDate", "endDate", "groupBy")
                        )
                )
        );
    }

    private FunctionDefinition createGetTripStatsSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "getTripStats",
                        "Calculates aggregated trip statistics: total distance, duration, trip counts by transportation mode. Use for comparing walking vs driving, analyzing travel patterns grouped by movement type, origin, destination, day, week, or month.",
                        createParametersSchema(
                                withTargetProperties(Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)"),
                                        "groupBy", enumPropertySpec("string",
                                                "Group by: MOVEMENT_TYPE, ORIGIN_LOCATION_NAME, DESTINATION_LOCATION_NAME, DAY, WEEK, or MONTH",
                                                List.of("MOVEMENT_TYPE", "ORIGIN_LOCATION_NAME", "DESTINATION_LOCATION_NAME", "DAY", "WEEK", "MONTH"))
                                )),
                                List.of("startDate", "endDate", "groupBy")
                        )
                )
        );
    }

    private FunctionDefinition createRoutePatternsSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "getRoutePatterns",
                        "Analyzes route patterns: most common routes, unique route count, average/longest trip. Use for route frequency and travel diversity. NOT for transport modes or location visits.",
                        createParametersSchema(
                                withTargetProperties(Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)")
                                )),
                                List.of("startDate", "endDate")
                        )
                )
        );
    }

    private FunctionDefinition createListAccessibleTimelineFriendsSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "listAccessibleTimelineFriends",
                        "Lists friends who granted timeline access. Use this before FRIEND-scoped timeline queries to pick a valid email/full name.",
                        createParametersSchema(Map.of(), List.of())
                )
        );
    }

    private FunctionDefinition createListAccessibleLiveFriendsSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "listAccessibleLiveFriends",
                        "Lists friends who granted live location access. Use this before asking where a friend is right now.",
                        createParametersSchema(Map.of(), List.of())
                )
        );
    }

    private FunctionDefinition createGetFriendLiveLocationSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "getFriendLiveLocation",
                        "Gets the latest known live location for one friend, including timestamp, recency fields (secondsAgo/liveNow/stale), and coordinates. If targetUser is omitted and exactly one friend shared live location, that friend is selected automatically.",
                        createParametersSchema(
                                Map.of(
                                        "targetUser", propertySpec("string", "Optional friend identifier (email or full name).")
                                ),
                                List.of()
                        )
                )
        );
    }

    private FunctionDefinition createGetTodayDateSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "getTodayDate",
                        "Returns today's date with year. Call this first when user mentions relative dates like 'this month', 'last week', 'yesterday', or 'this year'.",
                        createParametersSchema(
                                Map.of(),
                                List.of()
                        )
                )
        );
    }

    private Map<String, Object> createParametersSchema(Map<String, Map<String, Object>> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Map<String, Object>> withTargetProperties(Map<String, Map<String, Object>> baseProperties) {
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>(baseProperties);
        properties.put("targetScope", enumPropertySpec("string",
                "Optional target scope. SELF (default) queries your timeline. FRIEND queries a friend's timeline.",
                List.of("SELF", "FRIEND")));
        properties.put("targetUser", propertySpec("string",
                "Optional friend identifier (email or full name). Required for FRIEND scope unless exactly one friend has shared timeline access."));
        return properties;
    }

    private Map<String, Object> propertySpec(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private Map<String, Object> enumPropertySpec(String type, String description, List<String> enumValues) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        property.put("enum", enumValues);
        return property;
    }

    private JsonNode parseArgs(String argumentsJson) throws Exception {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(argumentsJson);
    }

    private String getRequiredText(JsonNode args, String fieldName) {
        String value = getOptionalText(args, fieldName);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + fieldName);
        }
        return value;
    }

    private String getOptionalText(JsonNode args, String fieldName) {
        if (args == null || args.isMissingNode()) {
            return null;
        }
        JsonNode value = args.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private String serializeToolError(AIToolException e) {
        try {
            return objectMapper.writeValueAsString(e.toErrorPayload());
        } catch (Exception serializationError) {
            log.error("Failed to serialize structured AI tool error", serializationError);
            return unsafeFallbackErrorJson(e);
        }
    }

    private String unsafeFallbackErrorJson(Exception e) {
        String message = e.getMessage() == null ? "Unknown tool error" : e.getMessage().replace("\"", "'");
        return "{\"error\": {\"code\": \"TOOL_EXECUTION_ERROR\", \"message\": \"" + message + "\"}}";
    }

    private record RegisteredTool(FunctionDefinition definition, Function<String, String> invoker) {
    }
}
