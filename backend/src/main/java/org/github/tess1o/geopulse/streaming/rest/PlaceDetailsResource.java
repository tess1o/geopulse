package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.streaming.model.dto.PagedPlaceVisitsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceVisitDTO;
import org.github.tess1o.geopulse.streaming.service.PlaceDetailsService;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API resource for place details and visit history.
 * Provides endpoints to view comprehensive information about specific locations.
 */
@Path("/api/place-details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequestScoped
public class PlaceDetailsResource {

    @Inject
    PlaceDetailsService placeDetailsService;

    @Inject
    CurrentUserService currentUserService;

    /**
     * Get comprehensive details for a specific place including statistics.
     *
     * @param type place type ("favorite" or "geocoding")
     * @param id   place ID
     * @return place details with statistics
     */
    @GET
    @Path("/{type}/{id}")
    @RolesAllowed("USER")
    public Response getPlaceDetails(@PathParam("type") String type, @PathParam("id") Long id) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Place details request from user {} for {}:{}", userId, type, id);

        try {
            Optional<PlaceDetailsDTO> placeDetails = placeDetailsService.getPlaceDetails(type, id, userId);

            if (placeDetails.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Place not found or access denied"))
                        .build();
            }

            return Response.ok(ApiResponse.success(placeDetails.get())).build();

        } catch (Exception e) {
            log.error("Failed to get place details for user {}, {}:{}", userId, type, id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get place details: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get paginated visit history for a specific place.
     *
     * @param type          place type ("favorite" or "geocoding")
     * @param id            place ID
     * @param page          zero-based page number (default: 0)
     * @param size          page size (default: 50, max: 200)
     * @param sortBy        field to sort by (default: "timestamp")
     * @param sortDirection sort direction "asc" or "desc" (default: "desc")
     * @return paginated list of visits
     */
    @GET
    @Path("/{type}/{id}/visits")
    @RolesAllowed("USER")
    public Response getPlaceVisits(
            @PathParam("type") String type,
            @PathParam("id") Long id,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Place visits request from user {} for {}:{} (page={}, size={}, sortBy={}, dir={})",
                userId, type, id, page, size, sortBy, sortDirection);

        try {
            // Validate page number
            if (page < 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Page number must be non-negative"))
                        .build();
            }

            PagedPlaceVisitsDTO visits = placeDetailsService.getPlaceVisits(
                    type, id, userId, page, size, sortBy, sortDirection);

            return Response.ok(ApiResponse.success(visits)).build();

        } catch (Exception e) {
            log.error("Failed to get place visits for user {}, {}:{}", userId, type, id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get place visits: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Update place name (only for favorite locations).
     *
     * @param type place type (must be "favorite")
     * @param id   place ID
     * @param request request body containing new name
     * @return success response
     */
    @PUT
    @Path("/{type}/{id}")
    @RolesAllowed("USER")
    public Response updatePlaceName(
            @PathParam("type") String type,
            @PathParam("id") Long id,
            UpdatePlaceNameRequest request) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Update place name request from user {} for {}:{}", userId, type, id);

        try {
            if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Place name cannot be empty"))
                        .build();
            }

            boolean updated = placeDetailsService.updatePlaceName(type, id, userId, request.getName());

            if (!updated) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Cannot update place name. Only favorite locations can be renamed."))
                        .build();
            }

            return Response.ok(ApiResponse.success("Place name updated successfully")).build();

        } catch (Exception e) {
            log.error("Failed to update place name for user {}, {}:{}", userId, type, id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update place name: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Export all visits for a place as CSV.
     * Streams data to avoid memory issues with large datasets.
     *
     * @param type          place type ("favorite" or "geocoding")
     * @param id            place ID
     * @param sortBy        field to sort by (default: "timestamp")
     * @param sortDirection sort direction "asc" or "desc" (default: "desc")
     * @return CSV file with all visits
     */
    @GET
    @Path("/{type}/{id}/visits/export")
    @Produces("text/csv")
    @RolesAllowed("USER")
    public Response exportPlaceVisits(
            @PathParam("type") String type,
            @PathParam("id") Long id,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Export visits request from user {} for {}:{}", userId, type, id);

        try {
            // Get place details for metadata
            Optional<PlaceDetailsDTO> placeDetailsOpt = placeDetailsService.getPlaceDetails(type, id, userId);
            if (placeDetailsOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Place not found or access denied")
                        .build();
            }

            PlaceDetailsDTO placeDetails = placeDetailsOpt.get();

            // Get all visits
            List<PlaceVisitDTO> visits = placeDetailsService.getAllPlaceVisits(
                    type, id, userId, sortBy, sortDirection);

            // Create streaming output for CSV
            StreamingOutput stream = output -> {
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(output, StandardCharsets.UTF_8))) {

                    // Write CSV header
                    writer.write("Location Name,Latitude,Longitude,Visit Date,Visit Time,End Date,End Time,Duration (hours),Duration (formatted),Day of Week");
                    writer.newLine();

                    // Date formatters
                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                            .withZone(ZoneId.systemDefault());
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                            .withZone(ZoneId.systemDefault());
                    DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEEE")
                            .withZone(ZoneId.systemDefault());

                    // Write data rows
                    for (PlaceVisitDTO visit : visits) {
                        Instant startTime = visit.getTimestamp();
                        Instant endTime = startTime.plusSeconds(visit.getStayDuration());

                        // Format duration
                        String formattedDuration = formatDuration(visit.getStayDuration());
                        double durationHours = visit.getStayDuration() / 3600.0;

                        // Write row with proper CSV escaping
                        writer.write(escapeCsv(visit.getLocationName()));
                        writer.write(",");
                        writer.write(String.format("%.6f", visit.getLatitude()));
                        writer.write(",");
                        writer.write(String.format("%.6f", visit.getLongitude()));
                        writer.write(",");
                        writer.write(dateFormatter.format(startTime));
                        writer.write(",");
                        writer.write(timeFormatter.format(startTime));
                        writer.write(",");
                        writer.write(dateFormatter.format(endTime));
                        writer.write(",");
                        writer.write(timeFormatter.format(endTime));
                        writer.write(",");
                        writer.write(String.format("%.2f", durationHours));
                        writer.write(",");
                        writer.write(escapeCsv(formattedDuration));
                        writer.write(",");
                        writer.write(dayFormatter.format(startTime));
                        writer.newLine();
                    }

                    writer.flush();
                }
            };

            // Generate filename
            String sanitizedName = placeDetails.getLocationName()
                    .replaceAll("[^a-zA-Z0-9]", "_")
                    .toLowerCase();
            String filename = String.format("%s_visits_%s.csv",
                    sanitizedName,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Instant.now().atZone(ZoneId.systemDefault())));

            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "text/csv; charset=utf-8")
                    .build();

        } catch (Exception e) {
            log.error("Failed to export visits for user {}, {}:{}", userId, type, id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to export visits: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Escape CSV field value.
     * Wraps in quotes if contains comma, quote, or newline.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Format duration in seconds to human-readable format.
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + "m";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return hours > 0 ? days + "d " + hours + "h" : days + "d";
        }
    }

    /**
     * Request DTO for updating place name.
     */
    public static class UpdatePlaceNameRequest {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
