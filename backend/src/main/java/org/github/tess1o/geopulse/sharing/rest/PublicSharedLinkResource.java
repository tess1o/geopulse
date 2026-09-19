package org.github.tess1o.geopulse.sharing.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.notes.model.NoteSearchResponse;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.sharing.service.SharedLinkService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/public/share-links")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "User: Sharing", description = "Read public shared location data and verify shared-link passwords.")
public class PublicSharedLinkResource {

    @Inject
    SharedLinkService sharedLinkService;

    @GET
    @Path("/{linkId}")
    public SharedLocationInfo getSharedLocationInfo(@PathParam("linkId") UUID linkId) {
        try {
            return sharedLinkService.getSharedLocationInfo(linkId);
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found or expired", e);
        }
    }

    @POST
    @Path("/{linkId}/access-tokens")
    public AccessTokenResponse verifyPassword(@PathParam("linkId") UUID linkId, @Valid VerifyPasswordRequest request) {
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
    public LocationHistoryResponse getSharedLocation(@PathParam("linkId") UUID linkId,
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
    public MovementTimelineDTO getSharedTimeline(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("from") String startTime,
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
    public CompletionStage<NoteSearchResponse> getSharedNotes(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("from") String startTime,
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
    @Path("/{linkId}/path")
    public GpsPointPathDTO getSharedPath(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("from") String startTime,
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
    public LocationHistoryResponse.CurrentLocationData getSharedCurrentLocation(
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
