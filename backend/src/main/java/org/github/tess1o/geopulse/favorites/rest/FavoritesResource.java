package org.github.tess1o.geopulse.favorites.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.favorites.model.AddAreaToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.EditFavoriteDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteReconcileRequest;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.geocoding.model.ReconciliationJobProgress;
import org.github.tess1o.geopulse.geocoding.service.ReconciliationJobProgressService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/favorites")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
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
    @Path("")
    public Response getFavorites() {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} is retrieving favorites", authenticatedUserId);
            FavoriteLocationsDto favorites = service.getFavorites(authenticatedUserId);
            return Response.ok(ApiResponse.success(favorites)).build();
        } catch (Exception e) {
            log.error("Failed to retrieve favorites", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve favorites: " + e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{favoriteId}")
    public Response updateFavorite(@PathParam("favoriteId") long favoriteId, @Valid EditFavoriteDto dto) {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} updating favorite {}: name='{}', city='{}', country='{}'",
                    authenticatedUserId, favoriteId, dto.getName(), dto.getCity(), dto.getCountry());
            service.updateFavorite(authenticatedUserId, favoriteId, dto.getName(), dto.getCity(), dto.getCountry());
            return Response.ok(ApiResponse.success("Favorite updated successfully")).build();
        } catch (SecurityException e) {
            log.warn("User {} attempted to update favorite {} without authorization",
                    currentUserService.getCurrentUserId(), favoriteId);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Not authorized to update this favorite"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request to update favorite {}: {}", favoriteId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Favorite not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update favorite {}", favoriteId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update favorite: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{favoriteId}")
    public Response deleteFavorite(@PathParam("favoriteId") long favoriteId) {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} deleting favorite {}", authenticatedUserId, favoriteId);

            // Delete favorite within transaction
            service.deleteFavorite(authenticatedUserId, favoriteId);

            // Create async job AFTER transaction commits
            UUID jobId = service.createTimelineRegenerationJob(authenticatedUserId);
            if (jobId != null) {
                return Response.ok(ApiResponse.success(java.util.Map.of(
                    "message", "Favorite deleted successfully",
                    "jobId", jobId.toString()
                ))).build();
            }

            return Response.ok(ApiResponse.success("Favorite deleted successfully")).build();
        } catch (SecurityException e) {
            log.warn("User {} attempted to delete favorite {} without authorization",
                    currentUserService.getCurrentUserId(), favoriteId);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Not authorized to delete this favorite"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request to delete favorite {}: {}", favoriteId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Favorite not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete favorite {}", favoriteId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete favorite: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/point")
    public Response addPointToFavorites(@Valid AddPointToFavoritesDto dto) {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} adding point favorite: {} at [{}, {}]",
                    authenticatedUserId, dto.getName(), dto.getLat(), dto.getLon());

            // Add favorite within transaction (service method is @Transactional)
            service.addFavorite(authenticatedUserId, dto);

            // Create async job AFTER transaction commits (no @Transactional here)
            UUID jobId = service.createTimelineRegenerationJob(authenticatedUserId);
            if (jobId != null) {
                return Response.status(Response.Status.CREATED)
                        .entity(ApiResponse.success(java.util.Map.of(
                            "message", "Point favorite added successfully",
                            "jobId", jobId.toString()
                        )))
                        .build();
            }

            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success("Point favorite added successfully"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid point favorite data: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid favorite data: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to add point favorite", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to add favorite: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/area")
    public Response addAreaToFavorites(@Valid AddAreaToFavoritesDto dto) {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} adding area favorite: {} with bounds NE[{}, {}] SW[{}, {}]",
                    authenticatedUserId, dto.getName(),
                    dto.getNorthEastLat(), dto.getNorthEastLon(),
                    dto.getSouthWestLat(), dto.getSouthWestLon());

            // Add favorite within transaction (service method is @Transactional)
            service.addFavorite(authenticatedUserId, dto);

            // Create async job AFTER transaction commits (no @Transactional here)
            UUID jobId = service.createTimelineRegenerationJob(authenticatedUserId);
            if (jobId != null) {
                return Response.status(Response.Status.CREATED)
                        .entity(ApiResponse.success(java.util.Map.of(
                            "message", "Area favorite added successfully",
                            "jobId", jobId.toString()
                        )))
                        .build();
            }

            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success("Area favorite added successfully"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid area favorite data: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid favorite data: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to add area favorite", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to add favorite: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Start bulk reconciliation job (async).
     * Returns job ID immediately for progress tracking.
     *
     * @param request Reconciliation request
     * @return Job ID
     */
    @POST
    @Path("/reconcile/bulk")
    public Response reconcileFavoritesBulk(@Valid FavoriteReconcileRequest request) {
        UUID currentUserId = currentUserService.getCurrentUserId();
        log.info("User {} starting bulk favorite reconciliation with provider: {}",
                currentUserId, request.getProviderName());

        try {
            // Check if user already has active job
            Optional<ReconciliationJobProgress> activeJob =
                    reconciliationProgressService.getUserActiveJob(currentUserId);
            if (activeJob.isPresent()) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(ApiResponse.error("You already have an active reconciliation job. Job ID: " +
                                activeJob.get().getJobId().toString()))
                        .build();
            }

            UUID jobId = service.reconcileWithProviderAsync(currentUserId, request);
            return Response.ok(ApiResponse.success(Map.of("jobId", jobId.toString()))).build();

        } catch (Exception e) {
            log.error("Error starting favorite reconciliation for user {}: {}",
                    currentUserId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to start reconciliation: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get progress of a reconciliation job.
     * Reuses the same endpoint pattern as geocoding.
     *
     * @param jobId Job ID
     * @return Job progress
     */
    @GET
    @Path("/reconcile/jobs/{jobId}")
    public Response getReconciliationJobProgress(@PathParam("jobId") String jobId) {
        UUID currentUserId = currentUserService.getCurrentUserId();

        try {
            UUID jobUuid = UUID.fromString(jobId);
            Optional<ReconciliationJobProgress> jobProgress =
                    reconciliationProgressService.getJobProgress(jobUuid);

            if (jobProgress.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Job not found"))
                        .build();
            }

            // Verify job belongs to current user
            if (!jobProgress.get().getUserId().equals(currentUserId)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(ApiResponse.error("Access denied"))
                        .build();
            }

            return Response.ok(ApiResponse.success(jobProgress.get())).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid job ID format"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get job progress for job {}", jobId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get job progress: " + e.getMessage()))
                    .build();
        }
    }
}