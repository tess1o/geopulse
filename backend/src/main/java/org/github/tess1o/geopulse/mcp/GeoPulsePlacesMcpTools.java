package org.github.tess1o.geopulse.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.ai.model.AILimitedListDTO;
import org.github.tess1o.geopulse.ai.service.AIPlacesTools;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.streaming.model.dto.CityDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CitySummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CountryDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CountrySummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CurrentStatusDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationSearchResultDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceVisitDTO;

import java.util.List;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
public class GeoPulsePlacesMcpTools {

    private final AIPlacesTools placesTools;

    public GeoPulsePlacesMcpTools(AIPlacesTools placesTools) {
        this.placesTools = placesTools;
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets what the user is doing right now (STAY, TRIP, DATA_GAP, UNKNOWN or NO_DATA) with the current place or trip, the latest GPS point and today's totals.")
    CurrentStatusDTO getCurrentStatus() {
        return McpToolCalls.execute(placesTools::getCurrentStatus);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Searches visited places, cities and countries by name. Use it to resolve a name to the type and id that getPlaceDetails and getPlaceVisits need, or to the exact city/country name.")
    List<LocationSearchResultDTO> searchVisitedPlaces(
            @ToolArg(description = "Name to search for, at least 2 characters.") String query,
            @ToolArg(description = "Only PLACE, CITY or COUNTRY results. Defaults to all.", required = false) String type) {
        return McpToolCalls.execute(() -> placesTools.searchVisitedPlaces(query, type));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets a place with its visit statistics: total visits, visits this week/month/year, total and average time spent, first and last visit.")
    PlaceDetailsDTO getPlaceDetails(
            @ToolArg(description = "Place category from searchVisitedPlaces: favorite or geocoding.") String type,
            @ToolArg(description = "Place id from searchVisitedPlaces.") Long id) {
        return McpToolCalls.execute(() -> placesTools.getPlaceDetails(type, id));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists the individual stays at a place, one page at a time, newest first by default.")
    PageResponse<PlaceVisitDTO> getPlaceVisits(
            @ToolArg(description = "Place category from searchVisitedPlaces: favorite or geocoding.") String type,
            @ToolArg(description = "Place id from searchVisitedPlaces.") Long id,
            @ToolArg(description = "Page number starting at 0. Defaults to 0.", required = false) Integer page,
            @ToolArg(description = "Page size, up to 100. Defaults to 20.", required = false) Integer size,
            @ToolArg(description = "TIMESTAMP or STAYDURATION. Defaults to TIMESTAMP.", required = false) String sortBy,
            @ToolArg(description = "ASC or DESC. Defaults to DESC.", required = false) String sortDirection) {
        return McpToolCalls.execute(() -> placesTools.getPlaceVisits(type, id, page, size, sortBy, sortDirection));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists every city the user has stayed in with visit counts and time spent, most time first. Covers all history.")
    AILimitedListDTO<CitySummaryDTO> listVisitedCities(
            @ToolArg(description = "Maximum cities to return, up to 500. Defaults to 100.", required = false) Integer limit) {
        return McpToolCalls.execute(() -> placesTools.listVisitedCities(limit));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists every country the user has stayed in with visit counts, time spent and number of cities, most time first. Covers all history.")
    AILimitedListDTO<CountrySummaryDTO> listVisitedCountries(
            @ToolArg(description = "Maximum countries to return, up to 500. Defaults to 100.", required = false) Integer limit) {
        return McpToolCalls.execute(() -> placesTools.listVisitedCountries(limit));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets statistics for a city across all history: visits, time spent, first and last visit, and its most visited places.")
    CityDetailsDTO getCityDetails(
            @ToolArg(description = "Exact city name as returned by listVisitedCities or searchVisitedPlaces.") String name) {
        return McpToolCalls.execute(() -> placesTools.getCityDetails(name));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets statistics for a country across all history: visits, time spent, first and last visit, its cities and most visited places.")
    CountryDetailsDTO getCountryDetails(
            @ToolArg(description = "Exact country name as returned by listVisitedCountries or searchVisitedPlaces.") String name) {
        return McpToolCalls.execute(() -> placesTools.getCountryDetails(name));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists the user's favorite places (points) and areas, such as Home or Work, with their ids, coordinates, city and country.")
    FavoriteLocationsDto listFavoritePlaces() {
        return McpToolCalls.execute(placesTools::listFavoritePlaces);
    }
}
