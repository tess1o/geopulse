package org.github.tess1o.geopulse.sharing.rest;

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
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/shared")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "User: Sharing", description = "Read public shared location data and verify shared-link passwords.")
public class PublicSharedLinkResource {

    @Inject
    SharedLinkService sharedLinkService;

    @GET
    @Path("/{linkId}/info")
    public SharedLocationInfo getSharedLocationInfo(@PathParam("linkId") UUID linkId) {
        try {
            return sharedLinkService.getSharedLocationInfo(linkId);
        } catch (NotFoundException e) {
            throw problem(SHARED_LINK_NOT_FOUND, "Link not found or expired");
        }
    }

    @POST
    @Path("/{linkId}/verify")
    public AccessTokenResponse verifyPassword(@PathParam("linkId") UUID linkId, @Valid VerifyPasswordRequest request) {
        try {
            return sharedLinkService.verifyPassword(linkId, request.getPassword());
        } catch (NotFoundException e) {
            throw problem(SHARED_LINK_NOT_FOUND, "Link not found or expired");
        } catch (ForbiddenException e) {
            throw problem(SHARED_LINK_PASSWORD_INVALID, "Invalid password");
        }
    }

    @GET
    @Path("/{linkId}/location")
    public LocationHistoryResponse getSharedLocation(@PathParam("linkId") UUID linkId,
                                                     @HeaderParam("Authorization") String authHeader) {
        try {
            return sharedLinkService.getSharedLocation(linkId, bearerToken(authHeader));
        } catch (NotFoundException e) {
            throw problem(SHARED_LINK_NOT_FOUND, "Link not found or expired");
        } catch (ForbiddenException e) {
            throw problem(SHARED_LINK_ACCESS_DENIED, "Access denied");
        }
    }

    @GET
    @Path("/{linkId}/timeline")
    public MovementTimelineDTO getSharedTimeline(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        try {
            Instant startInstant = parseOptionalInstant(startTime, "startTime");
            Instant endInstant = parseOptionalInstant(endTime, "endTime");
            validateRange(startInstant, endInstant);
            return sharedLinkService.getSharedTimeline(
                    linkId, bearerToken(authHeader), startInstant, endInstant);
        } catch (NotFoundException e) {
            throw problem(SHARED_LINK_NOT_FOUND, "Link not found or expired");
        } catch (ForbiddenException e) {
            throw problem(SHARED_LINK_ACCESS_DENIED, "Access denied");
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_SHARED_TIME_RANGE, e.getMessage());
        }
    }

    @GET
    @Path("/{linkId}/notes")
    @Blocking
    public CompletionStage<NoteSearchResponse> getSharedNotes(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        try {
            Instant startInstant = parseOptionalInstant(startTime, "startTime");
            Instant endInstant = parseOptionalInstant(endTime, "endTime");
            validateRange(startInstant, endInstant);
            return sharedLinkService.getSharedNotes(
                    linkId, bearerToken(authHeader), startInstant, endInstant);
        } catch (NotFoundException e) {
            throw problem(SHARED_LINK_NOT_FOUND, "Link not found or expired");
        } catch (ForbiddenException e) {
            throw problem(SHARED_LINK_ACCESS_DENIED, "Access denied");
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_SHARED_TIME_RANGE, e.getMessage());
        }
    }

    @GET
    @Path("/{linkId}/path")
    public GpsPointPathDTO getSharedPath(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        try {
            Instant startInstant = parseOptionalInstant(startTime, "startTime");
            Instant endInstant = parseOptionalInstant(endTime, "endTime");
            validateRange(startInstant, endInstant);
            return sharedLinkService.getSharedPath(
                    linkId, bearerToken(authHeader), startInstant, endInstant);
        } catch (NotFoundException e) {
            throw problem(SHARED_LINK_NOT_FOUND, "Link not found or expired");
        } catch (ForbiddenException e) {
            throw problem(SHARED_LINK_ACCESS_DENIED, "Access denied");
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_SHARED_TIME_RANGE, e.getMessage());
        }
    }

    @GET
    @Path("/{linkId}/current")
    public LocationHistoryResponse.CurrentLocationData getSharedCurrentLocation(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader) {
        try {
            Optional<LocationHistoryResponse.CurrentLocationData> result =
                    sharedLinkService.getSharedCurrentLocation(linkId, bearerToken(authHeader));
            return result.orElseThrow(() -> problem(
                    SHARED_LOCATION_NOT_FOUND, "Current location not available"));
        } catch (NotFoundException e) {
            throw problem(SHARED_LOCATION_NOT_FOUND, "Current location not available");
        } catch (ForbiddenException e) {
            throw problem(SHARED_LINK_ACCESS_DENIED, "Access denied");
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_SHARE_LINK, e.getMessage());
        }
    }

    private String bearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw problem(SHARED_LINK_TOKEN_REQUIRED, "Authorization token required");
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
            throw problem(INVALID_SHARED_TIME_RANGE,
                    "Invalid " + fieldName + " format. Expected ISO-8601",
                    Map.of("field", fieldName));
        }
    }

    private void validateRange(Instant start, Instant end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw problem(INVALID_SHARED_TIME_RANGE, "startTime must be before endTime");
        }
    }
}
