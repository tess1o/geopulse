package org.github.tess1o.geopulse.streaming.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geocoding.service.LocationTimezoneService;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.streaming.model.dto.CityDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CityInCountryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CitySummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CountryDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CountrySummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationAnalyticsMapPlaceDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationSearchResultDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceVisitDTO;
import org.github.tess1o.geopulse.streaming.service.LocationAnalyticsService;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.CITY_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.COUNTRY_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_BOUNDING_BOX;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_DATE_RANGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_LOCATION_SEARCH;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_PAGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.LOCATION_VISITS_NOT_FOUND;

@Path("/location-analytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = ApiTags.LOCATION_ANALYTICS)
public class LocationAnalyticsResource {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    LocationAnalyticsService analyticsService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    LocationTimezoneService locationTimezoneService;

    @GET
    @Path("/search")
    @Operation(summary = "Search visited locations",
            description = "Searches the places, cities, and countries the signed-in user has visited by name.")
    public List<LocationSearchResultDTO> search(
            @Parameter(description = "Search text, at least 2 characters.", example = "Lisb")
            @QueryParam("q") String query,
            @Parameter(description = "Limit results to `place`, `city`, or `country`. Defaults to all.")
            @QueryParam("type") String type) {
        if (query == null || query.trim().length() < 2) {
            throw new GeoPulseException(INVALID_LOCATION_SEARCH, "Search query must be at least 2 characters");
        }
        return analyticsService.search(currentUserService.getCurrentUserId(), query.trim(), type);
    }

    @GET
    @Path("/cities")
    @Operation(summary = "List visited cities",
            description = "Returns every city the signed-in user has stayed in, with visit counts and time spent.")
    public List<CitySummaryDTO> getCities() {
        return analyticsService.getAllCities(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/countries")
    @Operation(summary = "List visited countries",
            description = "Returns every country the signed-in user has stayed in, with visit counts, time spent, "
                    + "and number of cities.")
    public List<CountrySummaryDTO> getCountries() {
        return analyticsService.getAllCountries(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/map/places")
    @Operation(summary = "Get visited places for the map",
            description = "Returns visited places with their visit counts for drawing on a map, optionally limited "
                    + "to a time range and a map viewport. The viewport needs all four bounds.")
    public List<LocationAnalyticsMapPlaceDTO> getMapPlaces(
            @Parameter(description = "Only visits after this ISO-8601 instant.", example = "2025-01-01T00:00:00Z")
            @QueryParam("from") String from,
            @Parameter(description = "Only visits before this ISO-8601 instant.", example = "2025-12-31T23:59:59Z")
            @QueryParam("to") String to,
            @Parameter(description = "Southern edge of the viewport, in degrees.")
            @QueryParam("minLat") Double minLat,
            @Parameter(description = "Northern edge of the viewport, in degrees.")
            @QueryParam("maxLat") Double maxLat,
            @Parameter(description = "Western edge of the viewport, in degrees.")
            @QueryParam("minLon") Double minLon,
            @Parameter(description = "Eastern edge of the viewport, in degrees.")
            @QueryParam("maxLon") Double maxLon,
            @Parameter(description = "Only places with at least this many visits. Defaults to 1.")
            @QueryParam("minVisits") @DefaultValue("1") Integer minVisits,
            @Parameter(description = "Maximum number of places. Defaults to 3000.")
            @QueryParam("limit") @DefaultValue("3000") Integer limit) {
        try {
            Instant fromInstant = parseOptionalInstant(from);
            Instant toInstant = parseOptionalInstant(to);
            if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
                throw new GeoPulseException(INVALID_DATE_RANGE, "'from' must be before 'to'");
            }
            validateViewport(minLat, maxLat, minLon, maxLon);
            return analyticsService.getMapPlaces(
                    currentUserService.getCurrentUserId(), fromInstant, toInstant,
                    minLat, maxLat, minLon, maxLon, minVisits, limit);
        } catch (DateTimeParseException exception) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "Dates must use ISO-8601 format", exception);
        }
    }

    @GET
    @Path("/cities/{name}")
    @Operation(summary = "Get city details",
            description = "Returns statistics for a city: visits, time spent, first and last visit, and the most "
                    + "visited places in it.")
    public CityDetailsDTO getCityDetails(
            @Parameter(description = "City name, URL-encoded, exactly as returned by `GET "
                    + "/api/v1/location-analytics/cities`.", example = "Lisbon")
            @PathParam("name") String cityName) {
        return analyticsService.getCityDetails(currentUserService.getCurrentUserId(), cityName)
                .orElseThrow(() -> new GeoPulseException(CITY_NOT_FOUND, "City not found or no visits recorded"));
    }

    @GET
    @Path("/countries/{name}")
    @Operation(summary = "Get country details",
            description = "Returns statistics for a country: visits, time spent, first and last visit, and the "
                    + "cities visited in it.")
    public CountryDetailsDTO getCountryDetails(
            @Parameter(description = "Country name, URL-encoded, exactly as returned by `GET "
                    + "/api/v1/location-analytics/countries`.", example = "Portugal")
            @PathParam("name") String countryName) {
        return analyticsService.getCountryDetails(currentUserService.getCurrentUserId(), countryName)
                .orElseThrow(() -> new GeoPulseException(COUNTRY_NOT_FOUND, "Country not found or no visits recorded"));
    }

    @GET
    @Path("/cities/{name}/visits")
    @Operation(summary = "List visits in a city",
            description = "Returns the stays in a city one page at a time.")
    public PageResponse<PlaceVisitDTO> getCityVisits(
            @Parameter(description = "City name, URL-encoded, exactly as returned by `GET "
                    + "/api/v1/location-analytics/cities`.")
            @PathParam("name") String cityName,
            @Parameter(description = "Page number, starting at 0.")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size. Defaults to 50.")
            @QueryParam("size") @DefaultValue("50") int size,
            @Parameter(description = "Sort field: `timestamp` or `stayDuration`. Defaults to `timestamp`.")
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @Parameter(description = "Sort direction: `asc` or `desc`. Defaults to `desc`.")
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection,
            @Parameter(description = "Add the local timezone of each visit (`locationTimezone`), resolved from the "
                    + "nearest GeoNames city. Defaults to `false`.")
            @QueryParam("includeLocationTimezones") @DefaultValue("false") boolean includeLocationTimezones) {
        validatePage(page);
        return withLocationTimezones(analyticsService.getCityVisits(
                currentUserService.getCurrentUserId(), cityName, page, size, sortBy, sortDirection),
                includeLocationTimezones);
    }

    @GET
    @Path("/countries/{name}/visits")
    @Operation(summary = "List visits in a country",
            description = "Returns the stays in a country one page at a time.")
    public PageResponse<PlaceVisitDTO> getCountryVisits(
            @Parameter(description = "Country name, URL-encoded, exactly as returned by `GET "
                    + "/api/v1/location-analytics/countries`.")
            @PathParam("name") String countryName,
            @Parameter(description = "Page number, starting at 0.")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size. Defaults to 50.")
            @QueryParam("size") @DefaultValue("50") int size,
            @Parameter(description = "Sort field: `timestamp` or `stayDuration`. Defaults to `timestamp`.")
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @Parameter(description = "Sort direction: `asc` or `desc`. Defaults to `desc`.")
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection,
            @Parameter(description = "Add the local timezone of each visit (`locationTimezone`), resolved from the "
                    + "nearest GeoNames city. Defaults to `false`.")
            @QueryParam("includeLocationTimezones") @DefaultValue("false") boolean includeLocationTimezones) {
        validatePage(page);
        return withLocationTimezones(analyticsService.getCountryVisits(
                currentUserService.getCurrentUserId(), countryName, page, size, sortBy, sortDirection),
                includeLocationTimezones);
    }

    @GET
    @Path("/cities/{name}/visits/export")
    @Produces("text/csv")
    @APIResponse(responseCode = "200", description = "City visits CSV export",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = SchemaType.STRING)))
    @Operation(summary = "Export visits in a city as CSV",
            description = "Downloads all stays in a city as a CSV file with location, start and end time, duration, "
                    + "and day of week.")
    public Response exportCityVisits(
            @Parameter(description = "City name, URL-encoded, exactly as returned by `GET "
                    + "/api/v1/location-analytics/cities`.")
            @PathParam("name") String cityName,
            @Parameter(description = "Sort field: `timestamp` or `stayDuration`. Defaults to `timestamp`.")
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @Parameter(description = "Sort direction: `asc` or `desc`. Defaults to `desc`.")
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {
        List<PlaceVisitDTO> visits = analyticsService.getAllCityVisits(
                currentUserService.getCurrentUserId(), cityName, sortBy, sortDirection);
        if (visits.isEmpty()) {
            throw new GeoPulseException(LOCATION_VISITS_NOT_FOUND, "No visits found for this city");
        }
        return csvResponse(visits, "city_" + sanitizeFilename(cityName));
    }

    @GET
    @Path("/countries/{name}/visits/export")
    @Produces("text/csv")
    @APIResponse(responseCode = "200", description = "Country visits CSV export",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = SchemaType.STRING)))
    @Operation(summary = "Export visits in a country as CSV",
            description = "Downloads all stays in a country as a CSV file with location, start and end time, "
                    + "duration, and day of week.")
    public Response exportCountryVisits(
            @Parameter(description = "Country name, URL-encoded, exactly as returned by `GET "
                    + "/api/v1/location-analytics/countries`.")
            @PathParam("name") String countryName,
            @Parameter(description = "Sort field: `timestamp` or `stayDuration`. Defaults to `timestamp`.")
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @Parameter(description = "Sort direction: `asc` or `desc`. Defaults to `desc`.")
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {
        List<PlaceVisitDTO> visits = analyticsService.getAllCountryVisits(
                currentUserService.getCurrentUserId(), countryName, sortBy, sortDirection);
        if (visits.isEmpty()) {
            throw new GeoPulseException(LOCATION_VISITS_NOT_FOUND, "No visits found for this country");
        }
        return csvResponse(visits, "country_" + sanitizeFilename(countryName));
    }

    @GET
    @Path("/countries/{name}/cities")
    @Operation(summary = "List cities in a country",
            description = "Returns the cities the signed-in user has visited in a country, with visit counts and "
                    + "time spent.")
    public List<CityInCountryDTO> getCitiesInCountry(
            @Parameter(description = "Country name, URL-encoded, exactly as returned by `GET "
                    + "/api/v1/location-analytics/countries`.", example = "Portugal")
            @PathParam("name") String countryName) {
        return analyticsService.getCountryDetails(currentUserService.getCurrentUserId(), countryName)
                .map(CountryDetailsDTO::getCities)
                .orElseThrow(() -> new GeoPulseException(COUNTRY_NOT_FOUND, "Country not found or no visits recorded"));
    }

    private void validateViewport(Double minLat, Double maxLat, Double minLon, Double maxLon) {
        boolean any = minLat != null || maxLat != null || minLon != null || maxLon != null;
        boolean all = minLat != null && maxLat != null && minLon != null && maxLon != null;
        if (any && !all) {
            throw new GeoPulseException(INVALID_BOUNDING_BOX,
                    "Viewport filter requires minLat, maxLat, minLon and maxLon");
        }
        if (all && (!Double.isFinite(minLat) || !Double.isFinite(maxLat)
                || !Double.isFinite(minLon) || !Double.isFinite(maxLon))) {
            throw new GeoPulseException(INVALID_BOUNDING_BOX, "Viewport bounds must be finite numbers");
        }
        if (all && (minLat < -90 || maxLat > 90 || minLat > maxLat || minLon > maxLon)) {
            throw new GeoPulseException(INVALID_BOUNDING_BOX, "Invalid viewport bounds");
        }
    }

    private PageResponse<PlaceVisitDTO> withLocationTimezones(PageResponse<PlaceVisitDTO> visits, boolean include) {
        if (include) {
            locationTimezoneService.assign(visits.items(), PlaceVisitDTO::getLatitude, PlaceVisitDTO::getLongitude,
                    PlaceVisitDTO::setLocationTimezone, "location_analytics_visits");
        }
        return visits;
    }

    private void validatePage(int page) {
        if (page < 0) throw new GeoPulseException(INVALID_PAGE, "Page number must be non-negative");
    }

    private Response csvResponse(List<PlaceVisitDTO> visits, String namePrefix) {
        // The user's profile timezone, like the rest of the app (not the server's).
        ZoneId zoneId = ZoneId.of(currentUserService.getCurrentUser().getTimezone());
        StreamingOutput stream = output -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write("Location Name,Latitude,Longitude,Visit Date,Visit Time," +
                        "End Date,End Time,Duration (hours),Duration (formatted),Day of Week");
                writer.newLine();
                for (PlaceVisitDTO visit : visits) writeVisitCsvRow(writer, visit, zoneId);
            }
        };
        String filename = namePrefix + "_visits_" +
                DATE_FORMATTER.format(Instant.now().atZone(zoneId)) + ".csv";
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    private void writeVisitCsvRow(BufferedWriter writer, PlaceVisitDTO visit, ZoneId zoneId) throws IOException {
        var start = visit.getTimestamp().atZone(zoneId);
        var end = visit.getTimestamp().plusSeconds(visit.getStayDuration()).atZone(zoneId);
        long hours = visit.getStayDuration() / 3600;
        long minutes = (visit.getStayDuration() % 3600) / 60;
        long seconds = visit.getStayDuration() % 60;
        writer.write(String.format("%s,%.6f,%.6f,%s,%s,%s,%s,%.2f,%02d:%02d:%02d,%s",
                escapeCsv(visit.getLocationName()), visit.getLatitude(), visit.getLongitude(),
                DATE_FORMATTER.format(start), TIME_FORMATTER.format(start),
                DATE_FORMATTER.format(end), TIME_FORMATTER.format(end),
                visit.getStayDuration() / 3600.0, hours, minutes, seconds,
                start.getDayOfWeek()));
        writer.newLine();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Instant parseOptionalInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value.trim());
    }
}
