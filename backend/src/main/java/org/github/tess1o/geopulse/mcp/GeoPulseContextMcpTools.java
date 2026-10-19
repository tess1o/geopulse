package org.github.tess1o.geopulse.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.ai.model.AITripDetailsDTO;
import org.github.tess1o.geopulse.ai.model.AIWeatherDTO;
import org.github.tess1o.geopulse.ai.service.AIContextTools;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventDto;
import org.github.tess1o.geopulse.gps.model.GpsStatusDTO;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchResponse;
import org.github.tess1o.geopulse.notes.model.NoteSearchResponse;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO;
import org.github.tess1o.geopulse.timelinelabels.model.dto.TimelineLabelDto;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;

import java.util.List;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
public class GeoPulseContextMcpTools {

    private final AIContextTools contextTools;

    public GeoPulseContextMcpTools(AIContextTools contextTools) {
        this.contextTools = contextTools;
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists the user's planned trips (owned or shared with them) with dates, status and notes.")
    List<TripDto> listTrips(
            @ToolArg(description = "Only UNPLANNED, UPCOMING, ACTIVE, COMPLETED or CANCELLED trips. Defaults to all.", required = false) String status) {
        return McpToolCalls.execute(() -> contextTools.listTrips(status));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets a planned trip with its totals (plan items visited, stays, distance, time moving) and its plan items in order.")
    AITripDetailsDTO getTripDetails(
            @ToolArg(description = "Trip id from listTrips.") Long tripId) {
        return McpToolCalls.execute(() -> contextTools.getTripDetails(tripId));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists the user's named timeline periods (for example 'Japan 2025') with start and end times. Use it to turn a named period into a date range.")
    List<TimelineLabelDto> listTimelineLabels(
            @ToolArg(description = "Only labels overlapping this start date (YYYY-MM-DD). Use with endDate.", required = false) String startDate,
            @ToolArg(description = "Only labels overlapping this end date (YYYY-MM-DD). Use with startDate.", required = false) String endDate) {
        return McpToolCalls.execute(() -> contextTools.listTimelineLabels(startDate, endDate));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true),
            description = "Searches the user's notes, from GeoPulse and the connected Memos server, by date range and optionally near a location.")
    NoteSearchResponse searchNotes(
            @ToolArg(description = "Start date in YYYY-MM-DD format. Defaults to the earliest data.", required = false) String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format, inclusive. Defaults to today.", required = false) String endDate,
            @ToolArg(description = "Latitude of a search center. Use with longitude and radiusMeters.", required = false) Double latitude,
            @ToolArg(description = "Longitude of a search center.", required = false) Double longitude,
            @ToolArg(description = "Search radius in meters.", required = false) Double radiusMeters,
            @ToolArg(description = "Maximum notes, up to 200. Defaults to 50.", required = false) Integer limit) {
        return McpToolCalls.execute(() -> contextTools.searchNotes(startDate, endDate, latitude, longitude, radiusMeters, limit));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true),
            description = "Searches the user's Immich photo library for photos taken in a date range, optionally near a location or in a city or country. Returns photo metadata only. Requires the Immich integration.")
    ImmichPhotoSearchResponse searchPhotos(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format, inclusive") String endDate,
            @ToolArg(description = "Latitude of a search center. Use with longitude and radiusMeters.", required = false) Double latitude,
            @ToolArg(description = "Longitude of a search center.", required = false) Double longitude,
            @ToolArg(description = "Search radius in meters.", required = false) Double radiusMeters,
            @ToolArg(description = "Only photos Immich places in this city.", required = false) String city,
            @ToolArg(description = "Only photos Immich places in this country.", required = false) String country,
            @ToolArg(description = "Maximum photos, up to 200. Defaults to 50.", required = false) Integer limit) {
        return McpToolCalls.execute(() -> contextTools.searchPhotos(startDate, endDate, latitude, longitude,
                radiusMeters, city, country, limit));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets the weather recorded along the user's GPS data in a date range, optionally near a location: temperature, precipitation, wind, cloud cover. Requires the weather integration.")
    AIWeatherDTO getWeather(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format, inclusive") String endDate,
            @ToolArg(description = "Latitude of a search center. Use with longitude and radiusKm.", required = false) Double latitude,
            @ToolArg(description = "Longitude of a search center.", required = false) Double longitude,
            @ToolArg(description = "Search radius in kilometers.", required = false) Double radiusKm,
            @ToolArg(description = "Maximum samples, up to 500, spread evenly over the range. Defaults to 100.", required = false) Integer limit) {
        return McpToolCalls.execute(() -> contextTools.getWeather(startDate, endDate, latitude, longitude, radiusKm, limit));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists geofence enter/leave events from the user's geofence rules, newest first.")
    PageResponse<GeofenceEventDto> listGeofenceEvents(
            @ToolArg(description = "Start date in YYYY-MM-DD format.", required = false) String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format, inclusive.", required = false) String endDate,
            @ToolArg(description = "Only ENTER or LEAVE events.", required = false) String eventType,
            @ToolArg(description = "Only events not marked as seen. Defaults to false.", required = false) Boolean unreadOnly,
            @ToolArg(description = "Maximum events, up to 200. Defaults to 25.", required = false) Integer limit) {
        return McpToolCalls.execute(() -> contextTools.listGeofenceEvents(startDate, endDate, eventType, unreadOnly, limit));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Explains why the timeline engine created a stay, trip or data gap as it is: recorded reasons, the deciding GPS points, points left out for accuracy, and relevant settings.")
    TimelineEventExplanationDetailsDTO explainTimelineEvent(
            @ToolArg(description = "STAY, TRIP or DATA_GAP.") String kind,
            @ToolArg(description = "Event id, as returned by queryTimeline, getVisitedLocations or getTripMovements.") Long id) {
        return McpToolCalls.execute(() -> contextTools.explainTimelineEvent(kind, id));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Checks whether GPS data is still arriving: when the latest point was recorded and received, its age, source and device, and the total number of points.")
    GpsStatusDTO getGpsStatus() {
        return McpToolCalls.execute(contextTools::getGpsStatus);
    }
}
