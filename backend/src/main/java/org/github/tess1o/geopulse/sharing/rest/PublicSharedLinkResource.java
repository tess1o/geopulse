package org.github.tess1o.geopulse.sharing.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchResponse;
import org.github.tess1o.geopulse.notes.model.NoteSearchResponse;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.sharing.service.SharedLinkService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.github.tess1o.geopulse.shared.openapi.ApiSecuritySchemes;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/public/share-links")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = ApiTags.SHARED_LINK_VIEWER)
public class PublicSharedLinkResource {

    @Inject
    SharedLinkService sharedLinkService;

    @GET
    @Path("/{linkId}")
    @Operation(summary = "Get share link info",
            description = "Returns what a share link shows: its type, name, owner, shared period, display options, "
                    + "and whether a password is required. Does not need an access token.")
    public SharedLocationInfo getSharedLocationInfo(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId) {
        try {
            return sharedLinkService.getSharedLocationInfo(linkId);
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        }
    }

    @POST
    @Path("/{linkId}/access-tokens")
    @Operation(summary = "Get an access token",
            description = "Returns a short-lived access token for a share link, to send as `Authorization: Bearer "
                    + "<token>` on the other viewer endpoints. For password-protected links, send the password; "
                    + "otherwise the password can be empty.")
    public AccessTokenResponse verifyPassword(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId, @Valid VerifyPasswordRequest request) {
        try {
            return sharedLinkService.verifyPassword(linkId, request.getPassword());
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(SHARED_LINK_PASSWORD_INVALID, "Invalid password", e);
        }
    }

    @GET
    @Path("/{linkId}/location")
    @Operation(summary = "Get the shared live location",
            description = "Returns the current location of the link owner and, if the link includes history, their "
                    + "recent movement. Live-location links only. Requires the access token from `POST "
                    + "/api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public LocationHistoryResponse getSharedLocation(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader) {
        try {
            return sharedLinkService.getSharedLocation(linkId, bearerToken(authHeader));
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(SHARED_LINK_ACCESS_DENIED, "Access denied", e);
        }
    }

    @GET
    @Path("/{linkId}/timeline")
    @Operation(summary = "Get the shared timeline",
            description = "Returns the stays, trips, and data gaps of the shared period. Timeline links only. "
                    + "Requires the access token from `POST /api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public MovementTimelineDTO getSharedTimeline(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @Parameter(description = "Start of the range, as an ISO-8601 instant. Defaults to the start of the "
                    + "shared period; the result is always limited to the shared period.")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the range, as an ISO-8601 instant. Defaults to the end of the shared "
                    + "period.")
            @QueryParam("to") String endTime) {
        try {
            Instant startInstant = parseOptionalInstant(startTime, "startTime");
            Instant endInstant = parseOptionalInstant(endTime, "endTime");
            validateRange(startInstant, endInstant);
            return sharedLinkService.getSharedTimeline(
                    linkId, bearerToken(authHeader), startInstant, endInstant);
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(SHARED_LINK_ACCESS_DENIED, "Access denied", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_SHARED_TIME_RANGE, INVALID_SHARED_TIME_RANGE.title(), e);
        }
    }

    @GET
    @Path("/{linkId}/notes")
    @Blocking
    @Operation(summary = "Get shared notes",
            description = "Returns the owner's notes in the shared period, when the link includes notes. Timeline "
                    + "links only. Requires the access token from `POST "
                    + "/api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public CompletionStage<NoteSearchResponse> getSharedNotes(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @Parameter(description = "Start of the range, as an ISO-8601 instant. Defaults to the start of the "
                    + "shared period; the result is always limited to the shared period.")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the range, as an ISO-8601 instant. Defaults to the end of the shared "
                    + "period.")
            @QueryParam("to") String endTime) {
        try {
            Instant startInstant = parseOptionalInstant(startTime, "startTime");
            Instant endInstant = parseOptionalInstant(endTime, "endTime");
            validateRange(startInstant, endInstant);
            return sharedLinkService.getSharedNotes(
                    linkId, bearerToken(authHeader), startInstant, endInstant);
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(SHARED_LINK_ACCESS_DENIED, "Access denied", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_SHARED_TIME_RANGE, INVALID_SHARED_TIME_RANGE.title(), e);
        }
    }

    @GET
    @Path("/{linkId}/photos")
    @Blocking
    @Operation(summary = "List shared photos",
            description = "Returns the owner's Immich photos taken in the shared period (or from the selected "
                    + "album), when the link includes photos. Timeline links only. Requires the access token from "
                    + "`POST /api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public CompletableFuture<ImmichPhotoSearchResponse> getSharedPhotos(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @Parameter(description = "Start of the range, as an ISO-8601 instant. Defaults to the start of the "
                    + "shared period; the result is always limited to the shared period.")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the range, as an ISO-8601 instant. Defaults to the end of the shared "
                    + "period.")
            @QueryParam("to") String endTime,
            @Parameter(description = "Maximum number of photos.")
            @QueryParam("limit") Integer limit) {
        try {
            Instant startInstant = parseOptionalInstant(startTime, "startTime");
            Instant endInstant = parseOptionalInstant(endTime, "endTime");
            validateRange(startInstant, endInstant);
            return sharedLinkService.getSharedPhotos(
                    linkId, bearerToken(authHeader), startInstant, endInstant, limit);
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(SHARED_LINK_ACCESS_DENIED, "Access denied", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_SHARED_TIME_RANGE, INVALID_SHARED_TIME_RANGE.title(), e);
        }
    }

    @GET
    @Path("/{linkId}/photos/{photoId}/thumbnail")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Shared Immich photo thumbnail",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @Operation(summary = "Get a shared photo thumbnail",
            description = "Returns a small JPEG thumbnail of a shared photo. Timeline links only. Requires the "
                    + "access token from `POST /api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public CompletableFuture<Response> getSharedPhotoThumbnail(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @Parameter(description = "Immich photo ID, as returned by the photos endpoint.")
            @PathParam("photoId") String photoId,
            @HeaderParam("Authorization") String authHeader) {
        return sharedPhotoBytes(linkId, photoId, authHeader, SharedLinkService.SharedPhotoVariant.THUMBNAIL);
    }

    @GET
    @Path("/{linkId}/photos/{photoId}/preview")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Shared Immich photo preview",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @Operation(summary = "Get a shared photo preview",
            description = "Returns a larger JPEG preview of a shared photo. Timeline links only. Requires the access "
                    + "token from `POST /api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public CompletableFuture<Response> getSharedPhotoPreview(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @Parameter(description = "Immich photo ID, as returned by the photos endpoint.")
            @PathParam("photoId") String photoId,
            @HeaderParam("Authorization") String authHeader) {
        return sharedPhotoBytes(linkId, photoId, authHeader, SharedLinkService.SharedPhotoVariant.PREVIEW);
    }

    @GET
    @Path("/{linkId}/photos/{photoId}/download")
    @Produces("image/jpeg")
    @Blocking
    @APIResponse(responseCode = "200", description = "Original shared Immich photo",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @Operation(summary = "Download a shared photo",
            description = "Downloads the original file of a shared photo. Timeline links only. Requires the access "
                    + "token from `POST /api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public CompletableFuture<Response> downloadSharedPhoto(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @Parameter(description = "Immich photo ID, as returned by the photos endpoint.")
            @PathParam("photoId") String photoId,
            @HeaderParam("Authorization") String authHeader) {
        return sharedPhotoBytes(linkId, photoId, authHeader, SharedLinkService.SharedPhotoVariant.ORIGINAL);
    }

    private CompletableFuture<Response> sharedPhotoBytes(
            UUID linkId, String photoId, String authHeader, SharedLinkService.SharedPhotoVariant variant) {
        CompletableFuture<byte[]> bytes;
        try {
            bytes = sharedLinkService.getSharedPhotoBytes(linkId, bearerToken(authHeader), photoId, variant);
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(SHARED_LINK_ACCESS_DENIED, "Access denied", e);
        }

        return bytes.thenApply(image -> {
            Response.ResponseBuilder response = Response.ok(image).header("Cache-Control", "max-age=3600");
            if (variant == SharedLinkService.SharedPhotoVariant.ORIGINAL) {
                response.header("Content-Disposition", "attachment; filename=\"photo_" + photoId + ".jpg\"");
            }
            return response.build();
        }).exceptionally(throwable -> {
            throw new GeoPulseException(IMMICH_PHOTO_NOT_FOUND, "Immich photo could not be retrieved",
                    Map.of("photoId", photoId));
        });
    }

    @GET
    @Path("/{linkId}/path")
    @Operation(summary = "Get the shared GPS path",
            description = "Returns the owner's GPS path in the shared period for drawing on a map. Timeline links "
                    + "only. Requires the access token from `POST "
                    + "/api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public GpsPointPathDTO getSharedPath(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @Parameter(description = "Start of the range, as an ISO-8601 instant. Defaults to the start of the "
                    + "shared period; the result is always limited to the shared period.")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the range, as an ISO-8601 instant. Defaults to the end of the shared "
                    + "period.")
            @QueryParam("to") String endTime) {
        try {
            Instant startInstant = parseOptionalInstant(startTime, "startTime");
            Instant endInstant = parseOptionalInstant(endTime, "endTime");
            validateRange(startInstant, endInstant);
            return sharedLinkService.getSharedPath(
                    linkId, bearerToken(authHeader), startInstant, endInstant);
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(SHARED_LINK_ACCESS_DENIED, "Access denied", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_SHARED_TIME_RANGE, INVALID_SHARED_TIME_RANGE.title(), e);
        }
    }

    @GET
    @Path("/{linkId}/current-location")
    @Operation(summary = "Get the owner's current location",
            description = "Returns the owner's latest location, when the timeline link is set to show the current "
                    + "location. Timeline links only. Requires the access token from `POST "
                    + "/api/v1/public/share-links/{linkId}/access-tokens`.")
    @SecurityRequirement(name = ApiSecuritySchemes.SHARE_LINK_TOKEN)
    public LocationHistoryResponse.CurrentLocationData getSharedCurrentLocation(
            @Parameter(description = "Share link ID from the shared URL.")
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader) {
        try {
            Optional<LocationHistoryResponse.CurrentLocationData> result =
                    sharedLinkService.getSharedCurrentLocation(linkId, bearerToken(authHeader));
            return result.orElseThrow(() -> new GeoPulseException(
                    SHARED_LOCATION_NOT_FOUND, "Current location not available"));
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LOCATION_NOT_FOUND, "Current location not available", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(SHARED_LINK_ACCESS_DENIED, "Access denied", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_SHARE_LINK, INVALID_SHARE_LINK.title(), e);
        }
    }

    private String bearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new GeoPulseException(SHARED_LINK_TOKEN_REQUIRED, "Authorization token required");
        }
        return authHeader.substring("Bearer ".length());
    }

    private Instant parseOptionalInstant(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_SHARED_TIME_RANGE,
                    "Invalid " + fieldName + " format. Expected ISO-8601",
                    Map.of("field", fieldName), e);
        }
    }

    private void validateRange(Instant start, Instant end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new GeoPulseException(INVALID_SHARED_TIME_RANGE, "startTime must be before endTime");
        }
    }
}
