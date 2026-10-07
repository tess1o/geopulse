package org.github.tess1o.geopulse.favorites.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.favorites.model.AddAreaToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.BulkAddFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.BulkAddFavoritesResult;
import org.github.tess1o.geopulse.favorites.model.BulkUpdateFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.BulkUpdateFavoritesResult;
import org.github.tess1o.geopulse.favorites.model.DistinctValuesDto;
import org.github.tess1o.geopulse.favorites.model.EditFavoriteDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteReconcileRequest;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.model.ReconciliationJobProgress;
import org.github.tess1o.geopulse.geocoding.service.ReconciliationJobProgressService;
import org.github.tess1o.geopulse.shared.api.JobResponse;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/favorites")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
@Tag(name = ApiTags.FAVORITES)
public class FavoritesResource {

    private final FavoriteLocationService service;
    private final CurrentUserService currentUserService;
    private final ReconciliationJobProgressService reconciliationProgressService;

    @Inject
    public FavoritesResource(FavoriteLocationService service,
                             CurrentUserService currentUserService,
                             ReconciliationJobProgressService reconciliationProgressService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.reconciliationProgressService = reconciliationProgressService;
    }

    @GET
    @Operation(summary = "List favorites",
            description = "Returns all favorite points and areas of the signed-in user with their names, cities, and "
                    + "countries.")
    public FavoriteLocationsDto getFavorites() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("User {} is retrieving favorites", userId);
        return service.getFavorites(userId);
    }

    @PUT
    @Path("/{favoriteId}")
    @APIResponse(responseCode = "200", description = "Favorite updated")
    @APIResponse(responseCode = "403", description = "Favorite is not owned by the current user")
    @Operation(summary = "Update a favorite",
            description = "Changes the name, city, country, or (for areas) the bounds of a favorite. When the bounds "
                    + "change, a background timeline regeneration starts and its job ID is returned; otherwise the "
                    + "job ID is empty.")
    public JobResponse updateFavorite(
            @Parameter(description = "Favorite ID.")
            @PathParam("favoriteId") long favoriteId,
            @NotNull @Valid EditFavoriteDto dto) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            boolean boundsChanged = service.updateFavorite(userId, favoriteId, dto);
            UUID jobId = boundsChanged ? service.createTimelineRegenerationJob(userId) : null;
            return new JobResponse(jobId);
        } catch (SecurityException exception) {
            throw new GeoPulseException(FAVORITE_ACCESS_DENIED, "Not authorized to update this favorite", exception);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_FAVORITE, INVALID_FAVORITE.title(), exception);
        }
    }

    @DELETE
    @Path("/{favoriteId}")
    @APIResponse(responseCode = "200", description = "Favorite deleted")
    @APIResponse(responseCode = "404", description = "Favorite not found")
    @Operation(summary = "Delete a favorite",
            description = "Deletes a favorite. Stays inside it fall back to their reverse-geocoded names. Starts a "
                    + "background timeline regeneration so existing stays pick up the change; the response contains "
                    + "the job ID to poll with `GET /api/v1/timeline/jobs/{jobId}`.")
    public JobResponse deleteFavorite(
            @Parameter(description = "Favorite ID.")
            @PathParam("favoriteId") long favoriteId) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            service.deleteFavorite(userId, favoriteId);
            return new JobResponse(service.createTimelineRegenerationJob(userId));
        } catch (SecurityException exception) {
            throw new GeoPulseException(FAVORITE_ACCESS_DENIED, "Not authorized to delete this favorite", exception);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(FAVORITE_NOT_FOUND, "Favorite not found", exception);
        }
    }

    @POST
    @Path("/points")
    @APIResponse(responseCode = "201", description = "Point favorite created")
    @Operation(summary = "Add a favorite point",
            description = "Creates a favorite at a single location. Stays near the point use the favorite's name. "
                    + "Starts a background timeline regeneration so existing stays pick up the change; the response "
                    + "contains the job ID to poll with `GET /api/v1/timeline/jobs/{jobId}`.")
    public RestResponse<JobResponse> addPointToFavorites(@NotNull @Valid AddPointToFavoritesDto dto) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            service.addFavorite(userId, dto);
            return RestResponse.status(Response.Status.CREATED,
                    new JobResponse(service.createTimelineRegenerationJob(userId)));
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_FAVORITE, INVALID_FAVORITE.title(), exception);
        }
    }

    @POST
    @Path("/areas")
    @APIResponse(responseCode = "201", description = "Area favorite created")
    @Operation(summary = "Add a favorite area",
            description = "Creates a favorite covering a rectangular area, such as a campus or a park. Stays inside "
                    + "the area use the favorite's name. Starts a background timeline regeneration so existing "
                    + "stays pick up the change; the response contains the job ID to poll with `GET "
                    + "/api/v1/timeline/jobs/{jobId}`.")
    public RestResponse<JobResponse> addAreaToFavorites(@NotNull @Valid AddAreaToFavoritesDto dto) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            service.addFavorite(userId, dto);
            return RestResponse.status(Response.Status.CREATED,
                    new JobResponse(service.createTimelineRegenerationJob(userId)));
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_FAVORITE, INVALID_FAVORITE.title(), exception);
        }
    }

    @POST
    @Path("/bulk")
    @APIResponse(responseCode = "201", description = "Favorites created")
    @Operation(summary = "Add several favorites",
            description = "Creates several favorite points and areas in one request. The response reports which ones "
                    + "succeeded. A single timeline regeneration job is started for all of them.")
    public RestResponse<BulkAddFavoritesResult> bulkAddFavorites(@NotNull @Valid BulkAddFavoritesDto request) {
        if (request.getPoints().isEmpty() && request.getAreas().isEmpty()) {
            throw new GeoPulseException(NO_FAVORITES_PROVIDED, "No favorites provided for bulk add");
        }

        UUID userId = currentUserService.getCurrentUserId();
        try {
            BulkAddFavoritesResult result = service.bulkAddFavorites(userId, request);
            if (result.getSuccessCount() == 0) {
                throw new GeoPulseException(FAVORITES_BULK_CREATE_FAILED, "Failed to add any favorites");
            }
            UUID jobId = service.createTimelineRegenerationJob(userId);
            if (jobId != null) {
                result.setJobId(jobId.toString());
            }
            return RestResponse.status(Response.Status.CREATED, result);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_FAVORITE, INVALID_FAVORITE.title(), exception);
        }
    }

    @PATCH
    @Path("/bulk-update")
    @Operation(summary = "Set city or country of several favorites",
            description = "Sets the city, the country, or both, on several favorites at once, for example to fix "
                    + "inconsistent names.")
    public BulkUpdateFavoritesResult bulkUpdateFavorites(@NotNull @Valid BulkUpdateFavoritesDto request) {
        try {
            BulkUpdateFavoritesResult result = service.bulkUpdateFavorites(
                    currentUserService.getCurrentUserId(), request);
            if (result.getSuccessCount() == 0) {
                throw new GeoPulseException(FAVORITES_BULK_UPDATE_FAILED, "Failed to update any favorites");
            }
            return result;
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_FAVORITE, INVALID_FAVORITE.title(), exception);
        }
    }

    @GET
    @Path("/distinct-values")
    @Operation(summary = "List favorite cities and countries",
            description = "Returns the distinct city and country names used by the signed-in user's favorites, for "
                    + "filters and autocomplete.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public DistinctValuesDto getDistinctValues() {
        return service.getDistinctValues(currentUserService.getCurrentUserId());
    }

    @POST
    @Path("/reconcile/bulk")
    @Operation(summary = "Re-resolve favorite addresses",
            description = "Starts a background job that looks up the city and country of favorites again with a "
                    + "chosen geocoding provider. Pass specific `favoriteIds`, or set `reconcileAll` with optional "
                    + "filters. Only one reconciliation job per user can run at a time. Poll `GET "
                    + "/api/v1/favorites/reconcile/jobs/{jobId}` for progress.")
    public JobResponse reconcileFavoritesBulk(@NotNull @Valid FavoriteReconcileRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        Optional<ReconciliationJobProgress> activeJob = reconciliationProgressService.getUserActiveJob(userId);
        if (activeJob.isPresent()) {
            throw new GeoPulseException(RECONCILIATION_ALREADY_ACTIVE,
                    "You already have an active reconciliation job",
                    Map.of("jobId", activeJob.get().getJobId().toString()));
        }
        return new JobResponse(service.reconcileWithProviderAsync(userId, request));
    }

    @GET
    @Path("/reconcile/jobs/{jobId}")
    @Operation(summary = "Get favorite reconciliation progress",
            description = "Returns the progress and result of a favorite reconciliation job.")
    public ReconciliationJobProgress getReconciliationJobProgress(
            @Parameter(description = "Reconciliation job ID.")
            @PathParam("jobId") String jobId) {
        UUID parsedJobId;
        try {
            parsedJobId = UUID.fromString(jobId);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_RECONCILIATION_JOB_ID, "Invalid job ID format", exception);
        }

        ReconciliationJobProgress progress = reconciliationProgressService.getJobProgress(parsedJobId)
                .orElseThrow(() -> new GeoPulseException(RECONCILIATION_JOB_NOT_FOUND, "Job not found"));
        if (!progress.getUserId().equals(currentUserService.getCurrentUserId())) {
            throw new GeoPulseException(RECONCILIATION_JOB_ACCESS_DENIED, "Access denied");
        }
        return progress;
    }
}
