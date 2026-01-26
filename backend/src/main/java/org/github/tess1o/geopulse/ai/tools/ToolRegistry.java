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
import org.github.tess1o.geopulse.ai.service.AITimelineTools;
import org.github.tess1o.geopulse.ai.service.SimpleAITools;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.statistics.service.RoutesAnalysisService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;

import java.util.*;
import java.util.function.Function;

@ApplicationScoped
@Slf4j
public class ToolRegistry {

    @Inject
    StreamingTimelineAggregator streamingTimelineAggregator;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    RoutesAnalysisService routesAnalysisService;

    @Inject
    ObjectMapper objectMapper;

    private AITimelineTools aiTimelineTools;
    private SimpleAITools simpleAITools;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    @PostConstruct
    public void registerTools() {
        aiTimelineTools = new AITimelineTools(streamingTimelineAggregator, currentUserService, routesAnalysisService);
        simpleAITools = new SimpleAITools();

        register("queryTimeline", this::invokeQueryTimeline, createQueryTimelineSpec());
        register("getVisitedLocations", this::invokeGetVisitedLocations, createGetVisitedLocationsSpec());
        register("getTripMovements", this::invokeTripMovements, createTripMovementsSpec());
        register("getStayStats", this::invokeGetStayStats, createGetStayStatsSpec());
        register("getTripStats", this::invokeGetTripStats, createGetTripStatsSpec());
        register("getRoutePatterns", this::invokeGetRoutePatterns, createRoutePatternsSpec());
        register("getTodayDate", this::invokeGetTodayDate, createGetTodayDateSpec());

        log.info("Registered " + tools.size() + " AI tools");
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

    // Tool invocation methods

    private String invokeQueryTimeline(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String startDate = args.get("startDate").asText();
            String endDate = args.get("endDate").asText();

            Object result = aiTimelineTools.queryTimeline(startDate, endDate);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking queryTimeline", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String invokeGetVisitedLocations(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String startDate = args.get("startDate").asText();
            String endDate = args.get("endDate").asText();

            Object result = aiTimelineTools.getVisitedLocations(startDate, endDate);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking getVisitedLocations", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String invokeTripMovements(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String startDate = args.get("startDate").asText();
            String endDate = args.get("endDate").asText();

            Object result = aiTimelineTools.getTripMovements(startDate, endDate);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking getTripMovements", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String invokeGetStayStats(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String startDate = args.get("startDate").asText();
            String endDate = args.get("endDate").asText();
            String groupByStr = args.get("groupBy").asText();
            StayGroupBy groupBy = StayGroupBy.valueOf(groupByStr);

            Object result = aiTimelineTools.getStayStats(startDate, endDate, groupBy);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking getStayStats", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String invokeGetTripStats(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String startDate = args.get("startDate").asText();
            String endDate = args.get("endDate").asText();
            String groupByStr = args.get("groupBy").asText();
            TripGroupBy groupBy = TripGroupBy.valueOf(groupByStr);

            Object result = aiTimelineTools.getTripStats(startDate, endDate, groupBy);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking getTripStats", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String invokeGetRoutePatterns(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String startDate = args.get("startDate").asText();
            String endDate = args.get("endDate").asText();

            Object result = aiTimelineTools.getRoutePatterns(startDate, endDate);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking getRoutePatterns", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String invokeGetTodayDate(String argumentsJson) {
        try {
            String result = simpleAITools.getTodayDate();
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking getTodayDate", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    // Function schema creation methods

    private FunctionDefinition createQueryTimelineSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "queryTimeline",
                        "Gets complete timeline with all stays and trips in chronological order. Use when listing specific events or detailed activity.",
                        createParametersSchema(
                                Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)")
                                ),
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
                                Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)")
                                ),
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
                                Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)")
                                ),
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
                                Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)"),
                                        "groupBy", enumPropertySpec("string",
                                                "Group by: LOCATION_NAME, CITY, COUNTRY, DAY, WEEK, or MONTH",
                                                List.of("LOCATION_NAME", "CITY", "COUNTRY", "DAY", "WEEK", "MONTH"))
                                ),
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
                                Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)"),
                                        "groupBy", enumPropertySpec("string",
                                                "Group by: MOVEMENT_TYPE, ORIGIN_LOCATION_NAME, DESTINATION_LOCATION_NAME, DAY, WEEK, or MONTH",
                                                List.of("MOVEMENT_TYPE", "ORIGIN_LOCATION_NAME", "DESTINATION_LOCATION_NAME", "DAY", "WEEK", "MONTH"))
                                ),
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
                                Map.of(
                                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                                        "endDate", propertySpec("string", "End date (YYYY-MM-DD)")
                                ),
                                List.of("startDate", "endDate")
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

    // Helper methods for schema creation

    private Map<String, Object> createParametersSchema(Map<String, Map<String, Object>> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
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

    // Inner record for registered tools
    private record RegisteredTool(FunctionDefinition definition, Function<String, String> invoker) {
    }
}
