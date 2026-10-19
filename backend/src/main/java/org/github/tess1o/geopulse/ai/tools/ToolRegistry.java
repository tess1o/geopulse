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
import org.github.tess1o.geopulse.ai.service.AIContextTools;
import org.github.tess1o.geopulse.ai.service.AIFriendLiveTools;
import org.github.tess1o.geopulse.ai.service.AIInsightsTools;
import org.github.tess1o.geopulse.ai.service.AIPlacesTools;
import org.github.tess1o.geopulse.ai.service.AITimelineTools;
import org.github.tess1o.geopulse.ai.service.AIToolException;
import org.github.tess1o.geopulse.ai.service.SimpleAITools;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
    private final AIPlacesTools aiPlacesTools;
    private final AIInsightsTools aiInsightsTools;
    private final AIContextTools aiContextTools;
    private final ObjectMapper objectMapper;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    @Inject
    public ToolRegistry(AITimelineTools aiTimelineTools,
                        AIFriendLiveTools aiFriendLiveTools,
                        SimpleAITools simpleAITools,
                        AIPlacesTools aiPlacesTools,
                        AIInsightsTools aiInsightsTools,
                        AIContextTools aiContextTools,
                        ObjectMapper objectMapper) {
        this.aiTimelineTools = aiTimelineTools;
        this.aiFriendLiveTools = aiFriendLiveTools;
        this.simpleAITools = simpleAITools;
        this.aiPlacesTools = aiPlacesTools;
        this.aiInsightsTools = aiInsightsTools;
        this.aiContextTools = aiContextTools;
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
        register("listAccessibleTimelineFriends", args -> invokeListAccessibleTimelineFriends(), createListAccessibleTimelineFriendsSpec());
        register("listAccessibleLiveFriends", args -> invokeListAccessibleLiveFriends(), createListAccessibleLiveFriendsSpec());
        register("getFriendLiveLocation", this::invokeGetFriendLiveLocation, createGetFriendLiveLocationSpec());
        register("getTodayDate", args -> invokeGetTodayDate(), createGetTodayDateSpec());
        registerPlacesTools();
        registerInsightsTools();
        registerContextTools();

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

    private String invokeListAccessibleTimelineFriends() {
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

    private String invokeListAccessibleLiveFriends() {
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

    private String invokeGetTodayDate() {
        try {
            String result = simpleAITools.getTodayDate();
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error invoking getTodayDate", e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private void registerPlacesTools() {
        registerTool("getCurrentStatus",
                "Gets what the user is doing right now (STAY, TRIP, DATA_GAP, UNKNOWN or NO_DATA) with the current place or trip, the latest GPS point and today's totals.",
                properties(), List.of(),
                args -> aiPlacesTools.getCurrentStatus());
        registerTool("searchVisitedPlaces",
                "Searches visited places, cities and countries by name. Use it to resolve a name to the type and id that getPlaceDetails and getPlaceVisits need, or to the exact city/country name.",
                properties(
                        "query", propertySpec("string", "Name to search for, at least 2 characters."),
                        "type", enumPropertySpec("string", "Only PLACE, CITY or COUNTRY results. Defaults to all.",
                                List.of("PLACE", "CITY", "COUNTRY"))),
                List.of("query"),
                args -> aiPlacesTools.searchVisitedPlaces(getOptionalText(args, "query"), getOptionalText(args, "type")));
        registerTool("getPlaceDetails",
                "Gets a place with its visit statistics: total visits, visits this week/month/year, total and average time spent, first and last visit.",
                properties(
                        "type", enumPropertySpec("string", "Place category from searchVisitedPlaces.", List.of("favorite", "geocoding")),
                        "id", propertySpec("integer", "Place id from searchVisitedPlaces.")),
                List.of("type", "id"),
                args -> aiPlacesTools.getPlaceDetails(getOptionalText(args, "type"), getOptionalLong(args, "id")));
        registerTool("getPlaceVisits",
                "Lists the individual stays at a place, one page at a time, newest first by default.",
                properties(
                        "type", enumPropertySpec("string", "Place category from searchVisitedPlaces.", List.of("favorite", "geocoding")),
                        "id", propertySpec("integer", "Place id from searchVisitedPlaces."),
                        "page", propertySpec("integer", "Page number starting at 0. Defaults to 0."),
                        "size", propertySpec("integer", "Page size, up to 100. Defaults to 20."),
                        "sortBy", enumPropertySpec("string", "Defaults to TIMESTAMP.", List.of("TIMESTAMP", "STAYDURATION")),
                        "sortDirection", enumPropertySpec("string", "Defaults to DESC.", List.of("ASC", "DESC"))),
                List.of("type", "id"),
                args -> aiPlacesTools.getPlaceVisits(getOptionalText(args, "type"), getOptionalLong(args, "id"),
                        getOptionalInteger(args, "page"), getOptionalInteger(args, "size"),
                        getOptionalText(args, "sortBy"), getOptionalText(args, "sortDirection")));
        registerTool("listVisitedCities",
                "Lists every city the user has stayed in with visit counts and time spent, most time first. Covers all history.",
                properties("limit", propertySpec("integer", "Maximum cities to return, up to 500. Defaults to 100.")),
                List.of(),
                args -> aiPlacesTools.listVisitedCities(getOptionalInteger(args, "limit")));
        registerTool("listVisitedCountries",
                "Lists every country the user has stayed in with visit counts, time spent and number of cities, most time first. Covers all history.",
                properties("limit", propertySpec("integer", "Maximum countries to return, up to 500. Defaults to 100.")),
                List.of(),
                args -> aiPlacesTools.listVisitedCountries(getOptionalInteger(args, "limit")));
        registerTool("getCityDetails",
                "Gets statistics for a city across all history: visits, time spent, first and last visit, and its most visited places.",
                properties("name", propertySpec("string", "Exact city name as returned by listVisitedCities or searchVisitedPlaces.")),
                List.of("name"),
                args -> aiPlacesTools.getCityDetails(getOptionalText(args, "name")));
        registerTool("getCountryDetails",
                "Gets statistics for a country across all history: visits, time spent, first and last visit, its cities and most visited places.",
                properties("name", propertySpec("string", "Exact country name as returned by listVisitedCountries or searchVisitedPlaces.")),
                List.of("name"),
                args -> aiPlacesTools.getCountryDetails(getOptionalText(args, "name")));
        registerTool("listFavoritePlaces",
                "Lists the user's favorite places (points) and areas, such as Home or Work, with their ids, coordinates, city and country.",
                properties(), List.of(),
                args -> aiPlacesTools.listFavoritePlaces());
    }

    private void registerInsightsTools() {
        registerTool("getPeriodDigest",
                "Gets the digest of a calendar month or year in the user's timezone: key metrics, comparison with the previous period, highlights, top places and milestones. Prefer it for month or year summaries.",
                properties(
                        "year", propertySpec("integer", "Year, 2000 to 2100."),
                        "month", propertySpec("integer", "Month 1 to 12. Omit for the whole year.")),
                List.of("year"),
                args -> aiInsightsTools.getPeriodDigest(getOptionalInteger(args, "year"), getOptionalInteger(args, "month")));
        registerTool("getStatistics",
                "Gets movement statistics for any date range: total distance, time moving, daily average distance, average speed, unique places, most active day, top places and frequent routes.",
                properties(
                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                        "endDate", propertySpec("string", "End date (YYYY-MM-DD), inclusive")),
                List.of("startDate", "endDate"),
                args -> aiInsightsTools.getStatistics(getOptionalText(args, "startDate"), getOptionalText(args, "endDate")));
        registerTool("getJourneyInsights",
                "Gets lifetime insights from all of the user's data: countries and cities, time patterns, distance traveled, weather and achievements.",
                properties(), List.of(),
                args -> aiInsightsTools.getJourneyInsights());
        registerTool("getCoverageSummary",
                "Gets how much area the user has explored: number of grid cells visited and covered area in km². Only works when coverage is enabled for the user.",
                properties("gridMeters", propertySpec("integer", "Grid cell size in meters: 20, 50, 250, 1000, 5000, 20000 or 40000. Defaults to 50.")),
                List.of(),
                args -> aiInsightsTools.getCoverageSummary(getOptionalInteger(args, "gridMeters")));
    }

    private void registerContextTools() {
        registerTool("listTrips",
                "Lists the user's planned trips (owned or shared with them) with dates, status and notes.",
                properties("status", enumPropertySpec("string", "Only trips with this status. Defaults to all.",
                        List.of("UNPLANNED", "UPCOMING", "ACTIVE", "COMPLETED", "CANCELLED"))),
                List.of(),
                args -> aiContextTools.listTrips(getOptionalText(args, "status")));
        registerTool("getTripDetails",
                "Gets a planned trip with its totals (plan items visited, stays, distance, time moving) and its plan items in order.",
                properties("tripId", propertySpec("integer", "Trip id from listTrips.")),
                List.of("tripId"),
                args -> aiContextTools.getTripDetails(getOptionalLong(args, "tripId")));
        registerTool("listTimelineLabels",
                "Lists the user's named timeline periods (for example 'Japan 2025') with start and end times. Use it to turn a named period into a date range.",
                properties(
                        "startDate", propertySpec("string", "Only labels overlapping this start date (YYYY-MM-DD). Use with endDate."),
                        "endDate", propertySpec("string", "Only labels overlapping this end date (YYYY-MM-DD). Use with startDate.")),
                List.of(),
                args -> aiContextTools.listTimelineLabels(getOptionalText(args, "startDate"), getOptionalText(args, "endDate")));
        registerTool("searchNotes",
                "Searches the user's notes, from GeoPulse and the connected Memos server, by date range and optionally near a location.",
                properties(
                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD). Defaults to the earliest data."),
                        "endDate", propertySpec("string", "End date (YYYY-MM-DD), inclusive. Defaults to today."),
                        "latitude", propertySpec("number", "Latitude of a search center. Use with longitude and radiusMeters."),
                        "longitude", propertySpec("number", "Longitude of a search center."),
                        "radiusMeters", propertySpec("number", "Search radius in meters."),
                        "limit", propertySpec("integer", "Maximum notes, up to 200. Defaults to 50.")),
                List.of(),
                args -> aiContextTools.searchNotes(getOptionalText(args, "startDate"), getOptionalText(args, "endDate"),
                        getOptionalDouble(args, "latitude"), getOptionalDouble(args, "longitude"),
                        getOptionalDouble(args, "radiusMeters"), getOptionalInteger(args, "limit")));
        registerTool("searchPhotos",
                "Searches the user's Immich photo library for photos taken in a date range, optionally near a location or in a city or country. Returns photo metadata only. Requires the Immich integration.",
                properties(
                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                        "endDate", propertySpec("string", "End date (YYYY-MM-DD), inclusive"),
                        "latitude", propertySpec("number", "Latitude of a search center. Use with longitude and radiusMeters."),
                        "longitude", propertySpec("number", "Longitude of a search center."),
                        "radiusMeters", propertySpec("number", "Search radius in meters."),
                        "city", propertySpec("string", "Only photos Immich places in this city."),
                        "country", propertySpec("string", "Only photos Immich places in this country."),
                        "limit", propertySpec("integer", "Maximum photos, up to 200. Defaults to 50.")),
                List.of("startDate", "endDate"),
                args -> aiContextTools.searchPhotos(getOptionalText(args, "startDate"), getOptionalText(args, "endDate"),
                        getOptionalDouble(args, "latitude"), getOptionalDouble(args, "longitude"),
                        getOptionalDouble(args, "radiusMeters"), getOptionalText(args, "city"),
                        getOptionalText(args, "country"), getOptionalInteger(args, "limit")));
        registerTool("getWeather",
                "Gets the weather recorded along the user's GPS data in a date range, optionally near a location: temperature, precipitation, wind, cloud cover. Requires the weather integration.",
                properties(
                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)"),
                        "endDate", propertySpec("string", "End date (YYYY-MM-DD), inclusive"),
                        "latitude", propertySpec("number", "Latitude of a search center. Use with longitude and radiusKm."),
                        "longitude", propertySpec("number", "Longitude of a search center."),
                        "radiusKm", propertySpec("number", "Search radius in kilometers."),
                        "limit", propertySpec("integer", "Maximum samples, up to 500, spread evenly over the range. Defaults to 100.")),
                List.of("startDate", "endDate"),
                args -> aiContextTools.getWeather(getOptionalText(args, "startDate"), getOptionalText(args, "endDate"),
                        getOptionalDouble(args, "latitude"), getOptionalDouble(args, "longitude"),
                        getOptionalDouble(args, "radiusKm"), getOptionalInteger(args, "limit")));
        registerTool("listGeofenceEvents",
                "Lists geofence enter/leave events from the user's geofence rules, newest first.",
                properties(
                        "startDate", propertySpec("string", "Start date (YYYY-MM-DD)."),
                        "endDate", propertySpec("string", "End date (YYYY-MM-DD), inclusive."),
                        "eventType", enumPropertySpec("string", "Only ENTER or LEAVE events.", List.of("ENTER", "LEAVE")),
                        "unreadOnly", propertySpec("boolean", "Only events not marked as seen. Defaults to false."),
                        "limit", propertySpec("integer", "Maximum events, up to 200. Defaults to 25.")),
                List.of(),
                args -> aiContextTools.listGeofenceEvents(getOptionalText(args, "startDate"), getOptionalText(args, "endDate"),
                        getOptionalText(args, "eventType"), getOptionalBoolean(args, "unreadOnly"),
                        getOptionalInteger(args, "limit")));
        registerTool("explainTimelineEvent",
                "Explains why the timeline engine created a stay, trip or data gap as it is: recorded reasons, the deciding GPS points, points left out for accuracy, and relevant settings.",
                properties(
                        "kind", enumPropertySpec("string", "Event kind.", List.of("STAY", "TRIP", "DATA_GAP")),
                        "id", propertySpec("integer", "Event id, as returned by queryTimeline, getVisitedLocations or getTripMovements.")),
                List.of("kind", "id"),
                args -> aiContextTools.explainTimelineEvent(getOptionalText(args, "kind"), getOptionalLong(args, "id")));
        registerTool("getGpsStatus",
                "Checks whether GPS data is still arriving: when the latest point was recorded and received, its age, source and device, and the total number of points.",
                properties(), List.of(),
                args -> aiContextTools.getGpsStatus());
    }

    private void registerTool(String name, String description, Map<String, Map<String, Object>> properties,
                              List<String> required, ToolInvocation invocation) {
        register(name,
                argumentsJson -> invokeAndSerialize(name, argumentsJson, invocation),
                new FunctionDefinition("function",
                        new FunctionSpec(name, description, createParametersSchema(properties, required))));
    }

    private String invokeAndSerialize(String name, String argumentsJson, ToolInvocation invocation) {
        try {
            return objectMapper.writeValueAsString(invocation.invoke(parseArgs(argumentsJson)));
        } catch (AIToolException e) {
            return serializeToolError(e);
        } catch (GeoPulseException e) {
            return serializeToolError(AIToolException.from(e));
        } catch (Exception e) {
            log.error("Error invoking {}", name, e);
            return unsafeFallbackErrorJson(e);
        }
    }

    private FunctionDefinition createQueryTimelineSpec() {
        return new FunctionDefinition(
                "function",
                new FunctionSpec(
                        "queryTimeline",
                        "Gets complete timeline with all stays and trips in chronological order. Use when listing specific events or detailed activity. Stay and trip ids can be passed to explainTimelineEvent.",
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

    /** Property specs in the given order: name, spec, name, spec, ... */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> properties(Object... namesAndSpecs) {
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        for (int i = 0; i < namesAndSpecs.length; i += 2) {
            properties.put((String) namesAndSpecs[i], (Map<String, Object>) namesAndSpecs[i + 1]);
        }
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

    private Integer getOptionalInteger(JsonNode args, String fieldName) {
        Long value = getOptionalLong(args, fieldName);
        if (value == null) {
            return null;
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new AIToolException("INVALID_ARGUMENT", fieldName + " is out of range", Map.of("argument", fieldName));
        }
        return value.intValue();
    }

    private Long getOptionalLong(JsonNode args, String fieldName) {
        JsonNode value = optionalNode(args, fieldName);
        if (value == null) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText().trim());
        } catch (NumberFormatException e) {
            throw new AIToolException("INVALID_ARGUMENT", fieldName + " must be a whole number", Map.of("argument", fieldName), e);
        }
    }

    private Double getOptionalDouble(JsonNode args, String fieldName) {
        JsonNode value = optionalNode(args, fieldName);
        if (value == null) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(value.asText().trim());
        } catch (NumberFormatException e) {
            throw new AIToolException("INVALID_ARGUMENT", fieldName + " must be a number", Map.of("argument", fieldName), e);
        }
    }

    private Boolean getOptionalBoolean(JsonNode args, String fieldName) {
        JsonNode value = optionalNode(args, fieldName);
        if (value == null) {
            return null;
        }
        return value.isBoolean() ? value.asBoolean() : Boolean.parseBoolean(value.asText().trim());
    }

    /** The argument's node, or {@code null} when it is absent, null or a blank string. */
    private JsonNode optionalNode(JsonNode args, String fieldName) {
        if (args == null || args.isMissingNode()) {
            return null;
        }
        JsonNode value = args.get(fieldName);
        if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) {
            return null;
        }
        return value;
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

    @FunctionalInterface
    private interface ToolInvocation {
        Object invoke(JsonNode args) throws Exception;
    }
}
