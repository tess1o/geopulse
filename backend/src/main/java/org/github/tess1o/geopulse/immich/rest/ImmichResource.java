package org.github.tess1o.geopulse.immich.rest;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.immich.model.ImmichConfigResponse;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoMapMarkersResponse;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchRequest;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchResponse;
import org.github.tess1o.geopulse.immich.model.TestImmichConnectionRequest;
import org.github.tess1o.geopulse.immich.model.TestImmichConnectionResponse;
import org.github.tess1o.geopulse.immich.model.UpdateImmichConfigRequest;
import org.github.tess1o.geopulse.immich.service.ImmichService;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.IMMICH_PHOTO_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_IMMICH_CONFIG;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_IMMICH_SEARCH;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_USER_ID;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Immich", description = "Manage Immich configuration and retrieve Immich photo data.")
public class ImmichResource {

    @Inject
    ImmichService immichService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/{userId}/immich-config")
    @Blocking
    @APIResponse(responseCode = "204", description = "Immich is not configured")
    public RestResponse<ImmichConfigResponse> getImmichConfig(@PathParam("userId") String userIdValue) {
        return getConfig(authorizedUserId(userIdValue));
    }

    @GET
    @Path("/me/immich-config")
    @Blocking
    @APIResponse(responseCode = "204", description = "Immich is not configured")
    public RestResponse<ImmichConfigResponse> getCurrentUserImmichConfig() {
        return getConfig(currentUserService.getCurrentUserId());
    }

    @PUT
    @Path("/{userId}/immich-config")
    @Blocking
    @APIResponse(responseCode = "204", description = "Immich configuration updated")
    public RestResponse<Void> updateImmichConfig(
            @PathParam("userId") String userIdValue,
            @NotNull @Valid UpdateImmichConfigRequest request) {
        return updateConfig(authorizedUserId(userIdValue), request);
    }

    @PUT
    @Path("/me/immich-config")
    @Blocking
    @APIResponse(responseCode = "204", description = "Immich configuration updated")
    public RestResponse<Void> updateCurrentUserImmichConfig(
            @NotNull @Valid UpdateImmichConfigRequest request) {
        return updateConfig(currentUserService.getCurrentUserId(), request);
    }

    @GET
    @Path("/{userId}/immich/photos/search")
    @Blocking
    public CompletableFuture<ImmichPhotoSearchResponse> searchPhotos(
            @PathParam("userId") String userIdValue,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters,
            @QueryParam("city") String city,
            @QueryParam("country") String country,
            @QueryParam("limit") Integer limit) {
        return searchPhotosForUser(authorizedUserId(userIdValue), startDate, endDate,
                latitude, longitude, radiusMeters, city, country, limit);
    }

    @GET
    @Path("/me/immich/photos/search")
    @Blocking
    public CompletableFuture<ImmichPhotoSearchResponse> searchCurrentUserPhotos(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters,
            @QueryParam("city") String city,
            @QueryParam("country") String country,
            @QueryParam("limit") Integer limit) {
        return searchPhotosForUser(currentUserService.getCurrentUserId(), startDate, endDate,
                latitude, longitude, radiusMeters, city, country, limit);
    }

    @GET
    @Path("/{userId}/immich/photos/map-markers")
    @Blocking
    public CompletableFuture<ImmichPhotoMapMarkersResponse> getPhotoMapMarkers(
            @PathParam("userId") String userIdValue,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters,
            @QueryParam("city") String city,
            @QueryParam("country") String country,
            @QueryParam("coordinatePrecision") Integer coordinatePrecision) {
        return photoMapMarkersForUser(authorizedUserId(userIdValue), startDate, endDate,
                latitude, longitude, radiusMeters, city, country, coordinatePrecision);
    }

    @GET
    @Path("/me/immich/photos/map-markers")
    @Blocking
    public CompletableFuture<ImmichPhotoMapMarkersResponse> getCurrentUserPhotoMapMarkers(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters,
            @QueryParam("city") String city,
            @QueryParam("country") String country,
            @QueryParam("coordinatePrecision") Integer coordinatePrecision) {
        return photoMapMarkersForUser(currentUserService.getCurrentUserId(), startDate, endDate,
                latitude, longitude, radiusMeters, city, country, coordinatePrecision);
    }

    @GET
    @Path("/{userId}/immich/photos/map-marker/photos")
    @Blocking
    public CompletableFuture<ImmichPhotoSearchResponse> getPhotosForMapMarker(
            @PathParam("userId") String userIdValue,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters,
            @QueryParam("city") String city,
            @QueryParam("country") String country,
            @QueryParam("markerLatitude") Double markerLatitude,
            @QueryParam("markerLongitude") Double markerLongitude,
            @QueryParam("coordinatePrecision") Integer coordinatePrecision,
            @QueryParam("limit") Integer limit) {
        return photosForMapMarkerForUser(authorizedUserId(userIdValue), startDate, endDate,
                latitude, longitude, radiusMeters, city, country, markerLatitude, markerLongitude,
                coordinatePrecision, limit);
    }

    @GET
    @Path("/me/immich/photos/map-marker/photos")
    @Blocking
    public CompletableFuture<ImmichPhotoSearchResponse> getCurrentUserPhotosForMapMarker(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters,
            @QueryParam("city") String city,
            @QueryParam("country") String country,
            @QueryParam("markerLatitude") Double markerLatitude,
            @QueryParam("markerLongitude") Double markerLongitude,
            @QueryParam("coordinatePrecision") Integer coordinatePrecision,
            @QueryParam("limit") Integer limit) {
        return photosForMapMarkerForUser(currentUserService.getCurrentUserId(), startDate, endDate,
                latitude, longitude, radiusMeters, city, country, markerLatitude, markerLongitude,
                coordinatePrecision, limit);
    }

    @POST
    @Path("/{userId}/immich-config/test")
    @Blocking
    public CompletableFuture<TestImmichConnectionResponse> testImmichConnection(
            @PathParam("userId") String userIdValue,
            @NotNull @Valid TestImmichConnectionRequest request) {
        return immichService.testImmichConnection(authorizedUserId(userIdValue), request);
    }

    @POST
    @Path("/me/immich-config/test")
    @Blocking
    public CompletableFuture<TestImmichConnectionResponse> testCurrentUserImmichConnection(
            @NotNull @Valid TestImmichConnectionRequest request) {
        return immichService.testImmichConnection(currentUserService.getCurrentUserId(), request);
    }

    @GET
    @Path("/{userId}/immich/photos/{photoId}/thumbnail")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Immich photo thumbnail",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> getPhotoThumbnail(
            @PathParam("userId") String userIdValue,
            @PathParam("photoId") String photoId) {
        return photoBytes(authorizedUserId(userIdValue), photoId, PhotoVariant.THUMBNAIL);
    }

    @GET
    @Path("/me/immich/photos/{photoId}/thumbnail")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Immich photo thumbnail",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> getCurrentUserPhotoThumbnail(@PathParam("photoId") String photoId) {
        return photoBytes(currentUserService.getCurrentUserId(), photoId, PhotoVariant.THUMBNAIL);
    }

    @GET
    @Path("/{userId}/immich/photos/{photoId}/preview")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Immich photo preview",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> getPhotoPreview(
            @PathParam("userId") String userIdValue,
            @PathParam("photoId") String photoId) {
        return photoBytes(authorizedUserId(userIdValue), photoId, PhotoVariant.PREVIEW);
    }

    @GET
    @Path("/me/immich/photos/{photoId}/preview")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Immich photo preview",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> getCurrentUserPhotoPreview(@PathParam("photoId") String photoId) {
        return photoBytes(currentUserService.getCurrentUserId(), photoId, PhotoVariant.PREVIEW);
    }

    @GET
    @Path("/{userId}/immich/photos/{photoId}/download")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Original Immich photo",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> downloadPhoto(
            @PathParam("userId") String userIdValue,
            @PathParam("photoId") String photoId) {
        return photoBytes(authorizedUserId(userIdValue), photoId, PhotoVariant.ORIGINAL);
    }

    @GET
    @Path("/me/immich/photos/{photoId}/download")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Original Immich photo",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> downloadCurrentUserPhoto(@PathParam("photoId") String photoId) {
        return photoBytes(currentUserService.getCurrentUserId(), photoId, PhotoVariant.ORIGINAL);
    }

    private RestResponse<ImmichConfigResponse> getConfig(UUID userId) {
        return immichService.getUserImmichConfig(userId)
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    private RestResponse<Void> updateConfig(UUID userId, UpdateImmichConfigRequest request) {
        try {
            immichService.updateUserImmichConfig(userId, request);
            return RestResponse.noContent();
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_IMMICH_CONFIG, exception.getMessage());
        }
    }

    private CompletableFuture<ImmichPhotoSearchResponse> searchPhotosForUser(
            UUID userId, String startDate, String endDate, Double latitude, Double longitude,
            Double radiusMeters, String city, String country, Integer limit) {
        ImmichPhotoSearchRequest request = buildSearchRequest(
                startDate, endDate, latitude, longitude, radiusMeters, city, country, limit);
        return immichService.searchPhotos(userId, request);
    }

    private CompletableFuture<ImmichPhotoMapMarkersResponse> photoMapMarkersForUser(
            UUID userId, String startDate, String endDate, Double latitude, Double longitude,
            Double radiusMeters, String city, String country, Integer coordinatePrecision) {
        ImmichPhotoSearchRequest request = buildSearchRequest(
                startDate, endDate, latitude, longitude, radiusMeters, city, country, null);
        return immichService.getPhotoMapMarkers(userId, request, coordinatePrecision);
    }

    private CompletableFuture<ImmichPhotoSearchResponse> photosForMapMarkerForUser(
            UUID userId, String startDate, String endDate, Double latitude, Double longitude,
            Double radiusMeters, String city, String country, Double markerLatitude,
            Double markerLongitude, Integer coordinatePrecision, Integer limit) {
        if (markerLatitude == null || markerLongitude == null) {
            throw problem(INVALID_IMMICH_SEARCH, "markerLatitude and markerLongitude are required",
                    Map.of("fields", "markerLatitude,markerLongitude"));
        }
        ImmichPhotoSearchRequest request = buildSearchRequest(
                startDate, endDate, latitude, longitude, radiusMeters, city, country, null);
        return immichService.getPhotosForMapMarker(
                userId, request, markerLatitude, markerLongitude, coordinatePrecision, limit);
    }

    private CompletableFuture<Response> photoBytes(UUID userId, String photoId, PhotoVariant variant) {
        CompletableFuture<byte[]> bytes = switch (variant) {
            case THUMBNAIL -> immichService.getPhotoThumbnail(userId, photoId);
            case PREVIEW -> immichService.getPhotoPreview(userId, photoId);
            case ORIGINAL -> immichService.getPhotoOriginal(userId, photoId);
        };

        return bytes.thenApply(image -> {
            Response.ResponseBuilder response = Response.ok(image).header("Cache-Control", "max-age=3600");
            if (variant == PhotoVariant.ORIGINAL) {
                response.header("Content-Disposition", "attachment; filename=\"photo_" + photoId + ".jpg\"");
            }
            return response.build();
        }).exceptionally(throwable -> {
            throw problem(IMMICH_PHOTO_NOT_FOUND, "Immich photo could not be retrieved",
                    Map.of("photoId", photoId));
        });
    }

    private UUID authorizedUserId(String userIdValue) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdValue);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw problem(INVALID_USER_ID, "User ID must be a UUID");
        }
        if (!currentUserService.getCurrentUserId().equals(userId)) {
            throw problem(ACCESS_DENIED, "Access denied");
        }
        return userId;
    }

    private ImmichPhotoSearchRequest buildSearchRequest(
            String startDate, String endDate, Double latitude, Double longitude,
            Double radiusMeters, String city, String country, Integer limit) {
        try {
            ImmichPhotoSearchRequest request = new ImmichPhotoSearchRequest();
            request.setStartDate(OffsetDateTime.parse(startDate));
            request.setEndDate(OffsetDateTime.parse(endDate));
            request.setLatitude(latitude);
            request.setLongitude(longitude);
            request.setRadiusMeters(radiusMeters);
            request.setCity(city);
            request.setCountry(country);
            request.setLimit(limit);
            return request;
        } catch (DateTimeParseException | NullPointerException exception) {
            throw problem(INVALID_IMMICH_SEARCH, "startDate and endDate must use ISO-8601 format");
        }
    }

    private enum PhotoVariant {
        THUMBNAIL,
        PREVIEW,
        ORIGINAL
    }
}
