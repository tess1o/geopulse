package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.service.LocationAnalyticsService;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API resource for location analytics.
 * Provides city and country level aggregations and search capabilities.
 */
@Path("/api/location-analytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequestScoped
public class LocationAnalyticsResource {

    @Inject
    LocationAnalyticsService analyticsService;

    @Inject
    CurrentUserService currentUserService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Search across places, cities, and countries.
     *
     * @param query search term (minimum 2 characters)
     * @param type  filter by type: "place", "city", "country" (optional)
     * @return search results
     */
    @GET
    @Path("/search")
    @RolesAllowed({"USER", "ADMIN"})
    public Response search(
            @QueryParam("q") String query,
            @QueryParam("type") String type) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Location search request from user {} with query: '{}', type: {}", userId, query, type);

        try {
            // Validate query
            if (query == null || query.trim().length() < 2) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Search query must be at least 2 characters"))
                        .build();
            }

            List<LocationSearchResultDTO> results = analyticsService.search(
                    userId, query.trim(), type);

            return Response.ok(ApiResponse.success(results)).build();

        } catch (Exception e) {
            log.error("Search failed for user {}, query: {}", userId, query, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Search failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get list of all cities visited by user.
     *
     * @return list of city summaries
     */
    @GET
    @Path("/cities")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getCities() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Get cities request from user {}", userId);

        try {
            List<CitySummaryDTO> cities = analyticsService.getAllCities(userId);
            return Response.ok(ApiResponse.success(cities)).build();

        } catch (Exception e) {
            log.error("Failed to get cities for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get cities"))
                    .build();
        }
    }

    /**
     * Get list of all countries visited by user.
     *
     * @return list of country summaries
     */
    @GET
    @Path("/countries")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getCountries() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Get countries request from user {}", userId);

        try {
            List<CountrySummaryDTO> countries = analyticsService.getAllCountries(userId);
            return Response.ok(ApiResponse.success(countries)).build();

        } catch (Exception e) {
            log.error("Failed to get countries for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get countries"))
                    .build();
        }
    }

    /**
     * Get detailed statistics for a specific city.
     *
     * @param cityName city name (URL encoded)
     * @return city details
     */
    @GET
    @Path("/city/{name}")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getCityDetails(@PathParam("name") String cityName) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("City details request from user {} for city: {}", userId, cityName);

        try {
            Optional<CityDetailsDTO> details = analyticsService.getCityDetails(userId, cityName);

            if (details.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("City not found or no visits recorded"))
                        .build();
            }

            return Response.ok(ApiResponse.success(details.get())).build();

        } catch (Exception e) {
            log.error("Failed to get city details for user {}, city: {}", userId, cityName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get city details"))
                    .build();
        }
    }

    /**
     * Get detailed statistics for a specific country.
     *
     * @param countryName country name (URL encoded)
     * @return country details
     */
    @GET
    @Path("/country/{name}")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getCountryDetails(@PathParam("name") String countryName) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Country details request from user {} for country: {}", userId, countryName);

        try {
            Optional<CountryDetailsDTO> details = analyticsService.getCountryDetails(userId, countryName);

            if (details.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Country not found or no visits recorded"))
                        .build();
            }

            return Response.ok(ApiResponse.success(details.get())).build();

        } catch (Exception e) {
            log.error("Failed to get country details for user {}, country: {}", userId, countryName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get country details"))
                    .build();
        }
    }

    /**
     * Get paginated visits for a city.
     *
     * @param cityName      city name (URL encoded)
     * @param page          zero-based page number (default: 0)
     * @param size          page size (default: 50, max: 200)
     * @param sortBy        field to sort by (default: "timestamp")
     * @param sortDirection sort direction "asc" or "desc" (default: "desc")
     * @return paginated visits
     */
    @GET
    @Path("/city/{name}/visits")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getCityVisits(
            @PathParam("name") String cityName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("City visits request from user {} for city: {} (page={}, size={})",
                userId, cityName, page, size);

        try {
            if (page < 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Page number must be non-negative"))
                        .build();
            }

            PagedPlaceVisitsDTO visits = analyticsService.getCityVisits(
                    userId, cityName, page, size, sortBy, sortDirection);

            return Response.ok(ApiResponse.success(visits)).build();

        } catch (Exception e) {
            log.error("Failed to get city visits for user {}, city: {}", userId, cityName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get city visits"))
                    .build();
        }
    }

    /**
     * Get paginated visits for a country.
     *
     * @param countryName   country name (URL encoded)
     * @param page          zero-based page number (default: 0)
     * @param size          page size (default: 50, max: 200)
     * @param sortBy        field to sort by (default: "timestamp")
     * @param sortDirection sort direction "asc" or "desc" (default: "desc")
     * @return paginated visits
     */
    @GET
    @Path("/country/{name}/visits")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getCountryVisits(
            @PathParam("name") String countryName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Country visits request from user {} for country: {} (page={}, size={})",
                userId, countryName, page, size);

        try {
            if (page < 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Page number must be non-negative"))
                        .build();
            }

            PagedPlaceVisitsDTO visits = analyticsService.getCountryVisits(
                    userId, countryName, page, size, sortBy, sortDirection);

            return Response.ok(ApiResponse.success(visits)).build();

        } catch (Exception e) {
            log.error("Failed to get country visits for user {}, country: {}", userId, countryName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get country visits"))
                    .build();
        }
    }

    /**
     * Export all visits for a city as CSV.
     *
     * @param cityName      city name (URL encoded)
     * @param sortBy        field to sort by (default: "timestamp")
     * @param sortDirection sort direction "asc" or "desc" (default: "desc")
     * @return CSV file
     */
    @GET
    @Path("/city/{name}/visits/export")
    @Produces("text/csv")
    @RolesAllowed({"USER", "ADMIN"})
    public Response exportCityVisits(
            @PathParam("name") String cityName,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Export city visits request from user {} for city: {}", userId, cityName);

        try {
            List<PlaceVisitDTO> visits = analyticsService.getAllCityVisits(
                    userId, cityName, sortBy, sortDirection);

            if (visits.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("No visits found for this city")
                        .build();
            }

            StreamingOutput stream = output -> {
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(output, StandardCharsets.UTF_8))) {

                    // Write CSV header
                    writer.write("Location Name,Latitude,Longitude,Visit Date,Visit Time," +
                            "End Date,End Time,Duration (hours),Duration (formatted),Day of Week");
                    writer.newLine();

                    // Write data rows
                    for (PlaceVisitDTO visit : visits) {
                        writeVisitCsvRow(writer, visit);
                    }

                    writer.flush();
                }
            };

            String filename = String.format("city_%s_visits_%s.csv",
                    sanitizeFilename(cityName),
                    DATE_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault())));

            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();

        } catch (Exception e) {
            log.error("Failed to export city visits for user {}, city: {}", userId, cityName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to export visits")
                    .build();
        }
    }

    /**
     * Export all visits for a country as CSV.
     *
     * @param countryName   country name (URL encoded)
     * @param sortBy        field to sort by (default: "timestamp")
     * @param sortDirection sort direction "asc" or "desc" (default: "desc")
     * @return CSV file
     */
    @GET
    @Path("/country/{name}/visits/export")
    @Produces("text/csv")
    @RolesAllowed({"USER", "ADMIN"})
    public Response exportCountryVisits(
            @PathParam("name") String countryName,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Export country visits request from user {} for country: {}", userId, countryName);

        try {
            List<PlaceVisitDTO> visits = analyticsService.getAllCountryVisits(
                    userId, countryName, sortBy, sortDirection);

            if (visits.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("No visits found for this country")
                        .build();
            }

            StreamingOutput stream = output -> {
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(output, StandardCharsets.UTF_8))) {

                    // Write CSV header
                    writer.write("Location Name,Latitude,Longitude,Visit Date,Visit Time," +
                            "End Date,End Time,Duration (hours),Duration (formatted),Day of Week");
                    writer.newLine();

                    // Write data rows
                    for (PlaceVisitDTO visit : visits) {
                        writeVisitCsvRow(writer, visit);
                    }

                    writer.flush();
                }
            };

            String filename = String.format("country_%s_visits_%s.csv",
                    sanitizeFilename(countryName),
                    DATE_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault())));

            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();

        } catch (Exception e) {
            log.error("Failed to export country visits for user {}, country: {}", userId, countryName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to export visits")
                    .build();
        }
    }

    /**
     * Get all cities in a specific country.
     *
     * @param countryName country name (URL encoded)
     * @return list of cities
     */
    @GET
    @Path("/country/{name}/cities")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getCitiesInCountry(@PathParam("name") String countryName) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Get cities in country request from user {} for country: {}", userId, countryName);

        try {
            Optional<CountryDetailsDTO> details = analyticsService.getCountryDetails(userId, countryName);

            if (details.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Country not found or no visits recorded"))
                        .build();
            }

            return Response.ok(ApiResponse.success(details.get().getCities())).build();

        } catch (Exception e) {
            log.error("Failed to get cities in country for user {}, country: {}", userId, countryName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get cities"))
                    .build();
        }
    }

    // Helper methods

    /**
     * Write a visit record as a CSV row.
     */
    @SneakyThrows({IOException.class})
    private void writeVisitCsvRow(BufferedWriter writer, PlaceVisitDTO visit) {
        ZoneId zoneId = ZoneId.systemDefault();
        var startDateTime = visit.getTimestamp().atZone(zoneId);
        var endDateTime = visit.getTimestamp().plusSeconds(visit.getStayDuration()).atZone(zoneId);

        // Duration in hours
        double durationHours = visit.getStayDuration() / 3600.0;

        // Formatted duration (HH:MM:SS)
        long hours = visit.getStayDuration() / 3600;
        long minutes = (visit.getStayDuration() % 3600) / 60;
        long seconds = visit.getStayDuration() % 60;
        String durationFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        // Day of week
        String dayOfWeek = startDateTime.getDayOfWeek().toString();

        // Escape CSV values
        String locationName = escapeCsv(visit.getLocationName());

        writer.write(String.format("%s,%.6f,%.6f,%s,%s,%s,%s,%.2f,%s,%s",
                locationName,
                visit.getLatitude(),
                visit.getLongitude(),
                DATE_FORMATTER.format(startDateTime),
                TIME_FORMATTER.format(startDateTime),
                DATE_FORMATTER.format(endDateTime),
                TIME_FORMATTER.format(endDateTime),
                durationHours,
                durationFormatted,
                dayOfWeek
        ));
        writer.newLine();
    }

    /**
     * Escape CSV value (handle quotes and commas).
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Sanitize filename by removing unsafe characters.
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
