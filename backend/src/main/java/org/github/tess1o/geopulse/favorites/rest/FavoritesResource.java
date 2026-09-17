package org.github.tess1o.geopulse.favorites.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/favorites")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
@Tag(name = "User: Favorites", description = "Manage favorite places, areas, distinct values, and reconciliation.")
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
    public FavoriteLocationsDto getFavorites() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("User {} is retrieving favorites", userId);
        return service.getFavorites(userId);
    }

    @PUT
    @Path("/{favoriteId}")
    @APIResponse(responseCode = "200", description = "Favorite updated")
    @APIResponse(responseCode = "403", description = "Favorite is not owned by the current user")
    public JobResponse updateFavorite(@PathParam("favoriteId") long favoriteId,
                                      @NotNull @Valid EditFavoriteDto dto) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            boolean boundsChanged = service.updateFavorite(userId, favoriteId, dto);
            UUID jobId = boundsChanged ? service.createTimelineRegenerationJob(userId) : null;
            return new JobResponse(jobId);
        } catch (SecurityException exception) {
            throw problem(FAVORITE_ACCESS_DENIED, "Not authorized to update this favorite");
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_FAVORITE, exception.getMessage());
        }
    }

    @DELETE
    @Path("/{favoriteId}")
    @APIResponse(responseCode = "200", description = "Favorite deleted")
    @APIResponse(responseCode = "404", description = "Favorite not found")
    public JobResponse deleteFavorite(@PathParam("favoriteId") long favoriteId) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            service.deleteFavorite(userId, favoriteId);
            return new JobResponse(service.createTimelineRegenerationJob(userId));
        } catch (SecurityException exception) {
            throw problem(FAVORITE_ACCESS_DENIED, "Not authorized to delete this favorite");
        } catch (IllegalArgumentException exception) {
            throw problem(FAVORITE_NOT_FOUND, "Favorite not found");
        }
    }

    @POST
    @Path("/point")
    @APIResponse(responseCode = "201", description = "Point favorite created")
    public RestResponse<JobResponse> addPointToFavorites(@NotNull @Valid AddPointToFavoritesDto dto) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            service.addFavorite(userId, dto);
            return RestResponse.status(Response.Status.CREATED,
                    new JobResponse(service.createTimelineRegenerationJob(userId)));
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_FAVORITE, exception.getMessage());
        }
    }

    @POST
    @Path("/area")
    @APIResponse(responseCode = "201", description = "Area favorite created")
    public RestResponse<JobResponse> addAreaToFavorites(@NotNull @Valid AddAreaToFavoritesDto dto) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            service.addFavorite(userId, dto);
            return RestResponse.status(Response.Status.CREATED,
                    new JobResponse(service.createTimelineRegenerationJob(userId)));
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_FAVORITE, exception.getMessage());
        }
    }

    @POST
    @Path("/bulk")
    @APIResponse(responseCode = "201", description = "Favorites created")
    public RestResponse<BulkAddFavoritesResult> bulkAddFavorites(@NotNull @Valid BulkAddFavoritesDto request) {
        if (request.getPoints().isEmpty() && request.getAreas().isEmpty()) {
            throw problem(NO_FAVORITES_PROVIDED, "No favorites provided for bulk add");
        }

        UUID userId = currentUserService.getCurrentUserId();
        try {
            BulkAddFavoritesResult result = service.bulkAddFavorites(userId, request);
            if (result.getSuccessCount() == 0) {
                throw problem(FAVORITES_BULK_CREATE_FAILED, "Failed to add any favorites");
            }
            UUID jobId = service.createTimelineRegenerationJob(userId);
            if (jobId != null) {
                result.setJobId(jobId.toString());
            }
            return RestResponse.status(Response.Status.CREATED, result);
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_FAVORITE, exception.getMessage());
        }
    }

    @PUT
    @Path("/bulk-update")
    public BulkUpdateFavoritesResult bulkUpdateFavorites(@NotNull @Valid BulkUpdateFavoritesDto request) {
        try {
            BulkUpdateFavoritesResult result = service.bulkUpdateFavorites(
                    currentUserService.getCurrentUserId(), request);
            if (result.getSuccessCount() == 0) {
                throw problem(FAVORITES_BULK_UPDATE_FAILED, "Failed to update any favorites");
            }
            return result;
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_FAVORITE, exception.getMessage());
        }
    }

    @GET
    @Path("/distinct-values")
    public DistinctValuesDto getDistinctValues() {
        return service.getDistinctValues(currentUserService.getCurrentUserId());
    }

    @POST
    @Path("/reconcile/bulk")
    public JobResponse reconcileFavoritesBulk(@NotNull @Valid FavoriteReconcileRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        Optional<ReconciliationJobProgress> activeJob = reconciliationProgressService.getUserActiveJob(userId);
        if (activeJob.isPresent()) {
            throw problem(RECONCILIATION_ALREADY_ACTIVE,
                    "You already have an active reconciliation job",
                    Map.of("jobId", activeJob.get().getJobId().toString()));
        }
        return new JobResponse(service.reconcileWithProviderAsync(userId, request));
    }

    @GET
    @Path("/reconcile/jobs/{jobId}")
    public ReconciliationJobProgress getReconciliationJobProgress(@PathParam("jobId") String jobId) {
        UUID parsedJobId;
        try {
            parsedJobId = UUID.fromString(jobId);
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_RECONCILIATION_JOB_ID, "Invalid job ID format");
        }

        ReconciliationJobProgress progress = reconciliationProgressService.getJobProgress(parsedJobId)
                .orElseThrow(() -> problem(RECONCILIATION_JOB_NOT_FOUND, "Job not found"));
        if (!progress.getUserId().equals(currentUserService.getCurrentUserId())) {
            throw problem(RECONCILIATION_JOB_ACCESS_DENIED, "Access denied");
        }
        return progress;
    }
}
