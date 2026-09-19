package org.github.tess1o.geopulse.immich.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.IMMICH_PHOTO_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_IMMICH_CONFIG;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_IMMICH_SEARCH;

@Path(ApiPaths.INTEGRATIONS + "/immich")
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
    @Blocking
    @APIResponse(responseCode = "204", description = "Immich is not configured")
    public RestResponse<ImmichConfigResponse> getImmichConfig() {
        return getConfig(currentUserService.getCurrentUserId());
    }

    @PUT
    @Blocking
    @APIResponse(responseCode = "204", description = "Immich configuration updated")
    public RestResponse<Void> updateImmichConfig(@NotNull @Valid UpdateImmichConfigRequest request) {
        return updateConfig(currentUserService.getCurrentUserId(), request);
    }

    @GET
    @Path("/photos/search")
    @Blocking
    public CompletableFuture<ImmichPhotoSearchResponse> searchPhotos(
            @QueryParam("from") String startDate,
            @QueryParam("to") String endDate,
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
    @Path("/photos/map-markers")
    @Blocking
    public CompletableFuture<ImmichPhotoMapMarkersResponse> getPhotoMapMarkers(
            @QueryParam("from") String startDate,
            @QueryParam("to") String endDate,
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
    @Path("/photos/by-marker")
    @Blocking
    public CompletableFuture<ImmichPhotoSearchResponse> getPhotosForMapMarker(
            @QueryParam("from") String startDate,
            @QueryParam("to") String endDate,
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
    @Path("/connection-tests")
    @Blocking
    public CompletableFuture<TestImmichConnectionResponse> testImmichConnection(
            @NotNull @Valid TestImmichConnectionRequest request) {
        return immichService.testImmichConnection(currentUserService.getCurrentUserId(), request);
    }

    @GET
    @Path("/photos/{photoId}/thumbnail")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Immich photo thumbnail",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> getPhotoThumbnail(@PathParam("photoId") String photoId) {
        return photoBytes(currentUserService.getCurrentUserId(), photoId, PhotoVariant.THUMBNAIL);
    }

    @GET
    @Path("/photos/{photoId}/preview")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Immich photo preview",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> getPhotoPreview(@PathParam("photoId") String photoId) {
        return photoBytes(currentUserService.getCurrentUserId(), photoId, PhotoVariant.PREVIEW);
    }

    @GET
    @Path("/photos/{photoId}/download")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Original Immich photo",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public CompletableFuture<Response> downloadPhoto(@PathParam("photoId") String photoId) {
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
            throw new GeoPulseException(INVALID_IMMICH_CONFIG, INVALID_IMMICH_CONFIG.title(), exception);
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
            throw new GeoPulseException(INVALID_IMMICH_SEARCH, "markerLatitude and markerLongitude are required",
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
            throw new GeoPulseException(IMMICH_PHOTO_NOT_FOUND, "Immich photo could not be retrieved",
                    Map.of("photoId", photoId));
        });
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
            throw new GeoPulseException(INVALID_IMMICH_SEARCH, "startDate and endDate must use ISO-8601 format", exception);
        }
    }

    private enum PhotoVariant {
        THUMBNAIL,
        PREVIEW,
        ORIGINAL
    }
}
