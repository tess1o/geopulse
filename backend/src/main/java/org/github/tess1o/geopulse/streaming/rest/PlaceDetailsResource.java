package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlacePhotoSearchWindowDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceVisitDTO;
import org.github.tess1o.geopulse.streaming.model.dto.UpdatePlaceNameRequest;
import org.github.tess1o.geopulse.streaming.service.PlaceDetailsService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.jboss.resteasy.reactive.RestResponse;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_PAGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_PLACE_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.PLACE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.PLACE_RENAME_NOT_ALLOWED;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

/**
 * REST API resource for place details and visit history.
 * Provides endpoints to view comprehensive information about specific locations.
 */
@Path("/places")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequestScoped
@Tag(name = "User: Places", description = "Read and update place details, visits, photos, and exports.")
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
    @RolesAllowed({"USER", "ADMIN"})
    public PlaceDetailsDTO getPlaceDetails(@PathParam("type") String type, @PathParam("id") Long id) {
        UserEntity user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        log.info("Place details request from user {} for {}:{}", userId, type, id);

        return placeDetailsService.getPlaceDetails(type, id, userId, user.getTimezone())
                .orElseThrow(() -> problem(PLACE_NOT_FOUND, "Place not found or access denied"));
    }

    /**
     * Get merged photo search window for stays near this place geometry.
     * Useful for finding Immich photos across nearby places (for example close streets).
     *
     * @param type place type ("favorite" or "geocoding")
     * @param id place ID
     * @param radiusMeters nearby radius in meters (default 100)
     * @return min/max visit window and number of matched stays
     */
    @GET
    @Path("/{type}/{id}/photo-search-window")
    @RolesAllowed({"USER", "ADMIN"})
    public PlacePhotoSearchWindowDTO getPlacePhotoSearchWindow(
            @PathParam("type") String type,
            @PathParam("id") Long id,
            @QueryParam("radiusMeters") @DefaultValue("100") double radiusMeters) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Place photo search window request from user {} for {}:{} (radius={}m)",
                userId, type, id, radiusMeters);

        if (radiusMeters <= 0 || radiusMeters > 5000) {
            throw problem(INVALID_PLACE_REQUEST, "radiusMeters must be between 0 and 5000");
        }

        return placeDetailsService.getPlacePhotoSearchWindow(type, id, userId, radiusMeters)
                .orElseThrow(() -> problem(PLACE_NOT_FOUND, "Place not found or access denied"));
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
    @RolesAllowed({"USER", "ADMIN"})
    public PageResponse<PlaceVisitDTO> getPlaceVisits(
            @PathParam("type") String type,
            @PathParam("id") Long id,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Place visits request from user {} for {}:{} (page={}, size={}, sortBy={}, dir={})",
                userId, type, id, page, size, sortBy, sortDirection);

        if (page < 0) {
            throw problem(INVALID_PAGE, "Page number must be non-negative");
        }
        return placeDetailsService.getPlaceVisits(type, id, userId, page, size, sortBy, sortDirection);
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
    @RolesAllowed({"USER", "ADMIN"})
    @APIResponse(responseCode = "204", description = "Place name updated")
    public RestResponse<Void> updatePlaceName(
            @PathParam("type") String type,
            @PathParam("id") Long id,
            @NotNull @Valid UpdatePlaceNameRequest request) {

        UUID userId = currentUserService.getCurrentUserId();
        log.info("Update place name request from user {} for {}:{}", userId, type, id);

        boolean updated = placeDetailsService.updatePlaceName(type, id, userId, request.name().trim());
        if (!updated) {
            throw problem(PLACE_RENAME_NOT_ALLOWED,
                    "Only favorite locations can be renamed");
        }
        return RestResponse.noContent();
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
    @RolesAllowed({"USER", "ADMIN"})
    @APIResponse(responseCode = "200", description = "Place visits CSV export",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = SchemaType.STRING)))
    public Response exportPlaceVisits(
            @PathParam("type") String type,
            @PathParam("id") Long id,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {

        UserEntity user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        log.info("Export visits request from user {} for {}:{}", userId, type, id);

        try {
            // Get place details for metadata
            Optional<PlaceDetailsDTO> placeDetailsOpt = placeDetailsService.getPlaceDetails(
                    type, id, userId, user.getTimezone());
            if (placeDetailsOpt.isEmpty()) {
                throw problem(PLACE_NOT_FOUND, "Place not found or access denied");
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

        } catch (WebApplicationException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to export visits for user {}, {}:{}", userId, type, id, e);
            throw e;
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

}
