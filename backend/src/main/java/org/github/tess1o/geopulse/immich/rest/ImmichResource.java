package org.github.tess1o.geopulse.immich.rest;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.immich.model.*;
import org.github.tess1o.geopulse.immich.service.ImmichService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
public class ImmichResource {

    @Inject
    ImmichService immichService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/{userId}/immich-config")
    @RolesAllowed("USER")
    @Blocking
    public Response getImmichConfig(@PathParam("userId") String userIdStr) {
        UUID userId = parseUserId(userIdStr);
        validateUserAccess(userId);

        Optional<ImmichConfigResponse> config = immichService.getUserImmichConfig(userId);
        if (config.isEmpty()) {
            return Response.ok(ApiResponse.success(null)).build();
        }

        return Response.ok(ApiResponse.success(config.get())).build();
    }

    @PUT
    @Path("/{userId}/immich-config")
    @RolesAllowed("USER")
    @Blocking
    public Response updateImmichConfig(
            @PathParam("userId") String userIdStr,
            @Valid UpdateImmichConfigRequest request) {
        
        UUID userId = parseUserId(userIdStr);
        validateUserAccess(userId);

        try {
            immichService.updateUserImmichConfig(userId, request);
            return Response.ok(ApiResponse.success("Immich configuration updated successfully")).build();
        } catch (Exception e) {
            log.error("Failed to update Immich config for user {}: {}", userId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update Immich configuration"))
                    .build();
        }
    }

    @GET
    @Path("/{userId}/immich/photos/search")
    @RolesAllowed("USER")
    @Blocking
    public CompletableFuture<Response> searchPhotos(
            @PathParam("userId") String userIdStr,
            @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters) {
        
        UUID userId = parseUserId(userIdStr);
        validateUserAccess(userId);

        try {
            ImmichPhotoSearchRequest searchRequest = new ImmichPhotoSearchRequest();
            searchRequest.setStartDate(OffsetDateTime.parse(startDateStr));
            searchRequest.setEndDate(OffsetDateTime.parse(endDateStr));
            searchRequest.setLatitude(latitude);
            searchRequest.setLongitude(longitude);
            searchRequest.setRadiusMeters(radiusMeters);

            return immichService.searchPhotos(userId, searchRequest)
                    .thenApply(result -> Response.ok(ApiResponse.success(result)).build())
                    .exceptionally(throwable -> {
                        log.error("Failed to search photos for user {}: {}", userId, throwable.getMessage(), throwable);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity(ApiResponse.error("Failed to search photos"))
                                .build();
                    });
        } catch (Exception e) {
            log.error("Invalid search parameters for user {}: {}", userId, e.getMessage());
            return CompletableFuture.completedFuture(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(ApiResponse.error("Invalid search parameters: " + e.getMessage()))
                            .build()
            );
        }
    }

    @GET
    @Path("/{userId}/immich/photos/{photoId}/thumbnail")
    @Produces("image/jpeg")
    @RolesAllowed("USER")
    @Blocking
    public CompletableFuture<Response> getPhotoThumbnail(
            @PathParam("userId") String userIdStr,
            @PathParam("photoId") String photoId) {
        
        UUID userId = parseUserId(userIdStr);
        validateUserAccess(userId);

        return immichService.getPhotoThumbnail(userId, photoId)
                .thenApply(imageBytes -> 
                        Response.ok(imageBytes)
                                .header("Cache-Control", "max-age=3600")
                                .build())
                .exceptionally(throwable -> {
                    log.error("Failed to get thumbnail for photo {} and user {}: {}", 
                            photoId, userId, throwable.getMessage(), throwable);
                    return Response.status(Response.Status.NOT_FOUND).build();
                });
    }

    @GET
    @Path("/{userId}/immich/photos/{photoId}/download")
    @Produces("image/jpeg")
    @RolesAllowed("USER")
    @Blocking
    public CompletableFuture<Response> downloadPhoto(
            @PathParam("userId") String userIdStr,
            @PathParam("photoId") String photoId) {
        
        UUID userId = parseUserId(userIdStr);
        validateUserAccess(userId);

        return immichService.getPhotoOriginal(userId, photoId)
                .thenApply(imageBytes -> 
                        Response.ok(imageBytes)
                                .header("Content-Disposition", "attachment; filename=\"photo_" + photoId + ".jpg\"")
                                .build())
                .exceptionally(throwable -> {
                    log.error("Failed to download photo {} for user {}: {}", 
                            photoId, userId, throwable.getMessage(), throwable);
                    return Response.status(Response.Status.NOT_FOUND).build();
                });
    }

    @GET
    @Path("/me/immich-config")
    @RolesAllowed("USER")
    @Blocking
    public Response getCurrentUserImmichConfig() {
        UUID userId = currentUserService.getCurrentUserId();
        return getImmichConfig(userId.toString());
    }

    @PUT
    @Path("/me/immich-config")
    @RolesAllowed("USER")
    @Blocking
    public Response updateCurrentUserImmichConfig(@Valid UpdateImmichConfigRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        return updateImmichConfig(userId.toString(), request);
    }

    @GET
    @Path("/me/immich/photos/search")
    @RolesAllowed("USER")
    @Blocking
    public CompletableFuture<Response> searchCurrentUserPhotos(
            @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters) {
        
        UUID userId = currentUserService.getCurrentUserId();
        return searchPhotos(userId.toString(), startDateStr, endDateStr, latitude, longitude, radiusMeters);
    }

    @GET
    @Path("/me/immich/photos/{photoId}/thumbnail")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @RolesAllowed("USER")
    @Blocking
    public CompletableFuture<Response> getCurrentUserPhotoThumbnail(@PathParam("photoId") String photoId) {
        UUID userId = currentUserService.getCurrentUserId();
        return getPhotoThumbnail(userId.toString(), photoId);
    }

    @GET
    @Path("/me/immich/photos/{photoId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @RolesAllowed("USER")
    @Blocking
    public CompletableFuture<Response> downloadCurrentUserPhoto(@PathParam("photoId") String photoId) {
        UUID userId = currentUserService.getCurrentUserId();
        return downloadPhoto(userId.toString(), photoId);
    }

    private UUID parseUserId(String userIdStr) {
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException("Invalid user ID format", Response.Status.BAD_REQUEST);
        }
    }

    private void validateUserAccess(UUID userId) {
        UUID currentUserId = currentUserService.getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new WebApplicationException("Access denied", Response.Status.FORBIDDEN);
        }
    }
}