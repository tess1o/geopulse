package org.github.tess1o.geopulse.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.github.tess1o.geopulse.model.dto.ApiResponse;
import org.github.tess1o.geopulse.model.dto.LocationPathDTO;
import org.github.tess1o.geopulse.service.LocationService;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * REST resource for location data.
 */
@Path("/api/locations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LocationResource {

    private final LocationService locationService;

    @Inject
    public LocationResource(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Get a location path for a user within a specified time period.
     * This endpoint requires authentication.
     *
     * @param userId The ID of the user whose path to retrieve
     * @param startTime The start of the time period (ISO-8601 format)
     * @param endTime The end of the time period (ISO-8601 format)
     * @param requestContext The container request context for authentication
     * @return The location path
     */
    @GET
    @Path("/path")
    public Response getLocationPath(
            @QueryParam("userId") String userId,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @Context ContainerRequestContext requestContext) {

        // Get the authenticated user ID from the request context
        String authenticatedUserId = (String) requestContext.getProperty("userId");

        // If userId is not provided, use the authenticated user's ID
        if (userId == null || userId.isEmpty()) {
            userId = authenticatedUserId;
        }

        // Only allow users to access their own data unless they're an admin (future enhancement)
        if (!userId.equals(authenticatedUserId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("You can only access your own location data"))
                    .build();
        }

        try {
            // Parse the time parameters
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();

            // Get the location path using PostGIS features
            LocationPathDTO path = locationService.getLocationPathUsingPostGIS(userId, start, end);

            return Response.ok(ApiResponse.success(path)).build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve location path: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Export location path data as CSV.
     * This endpoint requires authentication.
     *
     * @param userId The ID of the user whose path to export
     * @param startTime The start of the time period (ISO-8601 format)
     * @param endTime The end of the time period (ISO-8601 format)
     * @param requestContext The container request context for authentication
     * @return CSV file as a response
     */
    @GET
    @Path("/export/csv")
    @Produces("text/csv")
    public Response exportLocationPathAsCsv(
            @QueryParam("userId") String userId,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @Context ContainerRequestContext requestContext) {

        // Get the authenticated user ID from the request context
        String authenticatedUserId = (String) requestContext.getProperty("userId");

        // If userId is not provided, use the authenticated user's ID
        if (userId == null || userId.isEmpty()) {
            userId = authenticatedUserId;
        }

        // Only allow users to access their own data unless they're an admin
        if (!userId.equals(authenticatedUserId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("You can only access your own location data"))
                    .build();
        }

        try {
            // Parse the time parameters
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();

            // Set the filename for download
            String filename = "location-data-" + start.toString().substring(0, 10) + "-to-" + 
                             end.toString().substring(0, 10) + ".csv";

            // Return a response with the CSV data
            // Note: The actual CSV generation logic is not implemented as per requirements
            return Response.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .entity("This is a placeholder for CSV data")
                    .build();

        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to export location path: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Export location path data as JSON.
     * This endpoint requires authentication.
     *
     * @param userId The ID of the user whose path to export
     * @param startTime The start of the time period (ISO-8601 format)
     * @param endTime The end of the time period (ISO-8601 format)
     * @param requestContext The container request context for authentication
     * @return JSON file as a response
     */
    @GET
    @Path("/export/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportLocationPathAsJson(
            @QueryParam("userId") String userId,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @Context ContainerRequestContext requestContext) {

        // Get the authenticated user ID from the request context
        String authenticatedUserId = (String) requestContext.getProperty("userId");

        // If userId is not provided, use the authenticated user's ID
        if (userId == null || userId.isEmpty()) {
            userId = authenticatedUserId;
        }

        // Only allow users to access their own data unless they're an admin
        if (!userId.equals(authenticatedUserId)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("You can only access your own location data"))
                    .build();
        }

        try {
            // Parse the time parameters
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();

            // Set the filename for download
            String filename = "location-data-" + start.toString().substring(0, 10) + "-to-" + 
                             end.toString().substring(0, 10) + ".json";

            // Return a response with the JSON data
            // Note: The actual JSON generation logic is not implemented as per requirements
            return Response.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .entity("{ \"message\": \"This is a placeholder for JSON data\" }")
                    .build();

        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to export location path: " + e.getMessage()))
                    .build();
        }
    }
}
