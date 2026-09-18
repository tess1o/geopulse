package org.github.tess1o.geopulse.streaming.rest;

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
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.CITY_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.COUNTRY_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_BOUNDING_BOX;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_DATE_RANGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_LOCATION_SEARCH;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_PAGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.LOCATION_VISITS_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/location-analytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = "User: Location Analytics", description = "Search and analyze visited cities, countries, places, and visits.")
public class LocationAnalyticsResource {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    LocationAnalyticsService analyticsService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/search")
    public List<LocationSearchResultDTO> search(
            @QueryParam("q") String query,
            @QueryParam("type") String type) {
        if (query == null || query.trim().length() < 2) {
            throw problem(INVALID_LOCATION_SEARCH, "Search query must be at least 2 characters");
        }
        return analyticsService.search(currentUserService.getCurrentUserId(), query.trim(), type);
    }

    @GET
    @Path("/cities")
    public List<CitySummaryDTO> getCities() {
        return analyticsService.getAllCities(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/countries")
    public List<CountrySummaryDTO> getCountries() {
        return analyticsService.getAllCountries(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/map/places")
    public List<LocationAnalyticsMapPlaceDTO> getMapPlaces(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("minLat") Double minLat,
            @QueryParam("maxLat") Double maxLat,
            @QueryParam("minLon") Double minLon,
            @QueryParam("maxLon") Double maxLon,
            @QueryParam("minVisits") @DefaultValue("1") Integer minVisits,
            @QueryParam("limit") @DefaultValue("3000") Integer limit) {
        try {
            Instant fromInstant = parseOptionalInstant(from);
            Instant toInstant = parseOptionalInstant(to);
            if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
                throw problem(INVALID_DATE_RANGE, "'from' must be before 'to'");
            }
            validateViewport(minLat, maxLat, minLon, maxLon);
            return analyticsService.getMapPlaces(
                    currentUserService.getCurrentUserId(), fromInstant, toInstant,
                    minLat, maxLat, minLon, maxLon, minVisits, limit);
        } catch (DateTimeParseException exception) {
            throw problem(INVALID_DATE_RANGE, "Dates must use ISO-8601 format");
        }
    }

    @GET
    @Path("/cities/{name}")
    public CityDetailsDTO getCityDetails(@PathParam("name") String cityName) {
        return analyticsService.getCityDetails(currentUserService.getCurrentUserId(), cityName)
                .orElseThrow(() -> problem(CITY_NOT_FOUND, "City not found or no visits recorded"));
    }

    @GET
    @Path("/countries/{name}")
    public CountryDetailsDTO getCountryDetails(@PathParam("name") String countryName) {
        return analyticsService.getCountryDetails(currentUserService.getCurrentUserId(), countryName)
                .orElseThrow(() -> problem(COUNTRY_NOT_FOUND, "Country not found or no visits recorded"));
    }

    @GET
    @Path("/cities/{name}/visits")
    public PageResponse<PlaceVisitDTO> getCityVisits(
            @PathParam("name") String cityName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {
        validatePage(page);
        return analyticsService.getCityVisits(
                currentUserService.getCurrentUserId(), cityName, page, size, sortBy, sortDirection);
    }

    @GET
    @Path("/countries/{name}/visits")
    public PageResponse<PlaceVisitDTO> getCountryVisits(
            @PathParam("name") String countryName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {
        validatePage(page);
        return analyticsService.getCountryVisits(
                currentUserService.getCurrentUserId(), countryName, page, size, sortBy, sortDirection);
    }

    @GET
    @Path("/cities/{name}/visits/export")
    @Produces("text/csv")
    @APIResponse(responseCode = "200", description = "City visits CSV export",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = SchemaType.STRING)))
    public Response exportCityVisits(
            @PathParam("name") String cityName,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {
        List<PlaceVisitDTO> visits = analyticsService.getAllCityVisits(
                currentUserService.getCurrentUserId(), cityName, sortBy, sortDirection);
        if (visits.isEmpty()) {
            throw problem(LOCATION_VISITS_NOT_FOUND, "No visits found for this city");
        }
        return csvResponse(visits, "city_" + sanitizeFilename(cityName));
    }

    @GET
    @Path("/countries/{name}/visits/export")
    @Produces("text/csv")
    @APIResponse(responseCode = "200", description = "Country visits CSV export",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = SchemaType.STRING)))
    public Response exportCountryVisits(
            @PathParam("name") String countryName,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {
        List<PlaceVisitDTO> visits = analyticsService.getAllCountryVisits(
                currentUserService.getCurrentUserId(), countryName, sortBy, sortDirection);
        if (visits.isEmpty()) {
            throw problem(LOCATION_VISITS_NOT_FOUND, "No visits found for this country");
        }
        return csvResponse(visits, "country_" + sanitizeFilename(countryName));
    }

    @GET
    @Path("/countries/{name}/cities")
    public List<CityInCountryDTO> getCitiesInCountry(@PathParam("name") String countryName) {
        return analyticsService.getCountryDetails(currentUserService.getCurrentUserId(), countryName)
                .map(CountryDetailsDTO::getCities)
                .orElseThrow(() -> problem(COUNTRY_NOT_FOUND, "Country not found or no visits recorded"));
    }

    private void validateViewport(Double minLat, Double maxLat, Double minLon, Double maxLon) {
        boolean any = minLat != null || maxLat != null || minLon != null || maxLon != null;
        boolean all = minLat != null && maxLat != null && minLon != null && maxLon != null;
        if (any && !all) {
            throw problem(INVALID_BOUNDING_BOX,
                    "Viewport filter requires minLat, maxLat, minLon and maxLon");
        }
        if (all && (!Double.isFinite(minLat) || !Double.isFinite(maxLat)
                || !Double.isFinite(minLon) || !Double.isFinite(maxLon))) {
            throw problem(INVALID_BOUNDING_BOX, "Viewport bounds must be finite numbers");
        }
        if (all && (minLat < -90 || maxLat > 90 || minLat > maxLat || minLon > maxLon)) {
            throw problem(INVALID_BOUNDING_BOX, "Invalid viewport bounds");
        }
    }

    private void validatePage(int page) {
        if (page < 0) throw problem(INVALID_PAGE, "Page number must be non-negative");
    }

    private Response csvResponse(List<PlaceVisitDTO> visits, String namePrefix) {
        StreamingOutput stream = output -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write("Location Name,Latitude,Longitude,Visit Date,Visit Time," +
                        "End Date,End Time,Duration (hours),Duration (formatted),Day of Week");
                writer.newLine();
                for (PlaceVisitDTO visit : visits) writeVisitCsvRow(writer, visit);
            }
        };
        String filename = namePrefix + "_visits_" +
                DATE_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault())) + ".csv";
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    private void writeVisitCsvRow(BufferedWriter writer, PlaceVisitDTO visit) throws IOException {
        ZoneId zoneId = ZoneId.systemDefault();
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
