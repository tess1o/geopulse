package org.github.tess1o.geopulse.geocoding.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geocoding.dto.*;
import org.github.tess1o.geopulse.geocoding.model.ReconciliationJobProgress;
import org.github.tess1o.geopulse.geocoding.service.ReconciliationJobProgressService;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API for managing reverse geocoding results.
 */
@Path("/api/geocoding")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class ReverseGeocodingResource {

    private final ReverseGeocodingManagementService managementService;
    private final CurrentUserService currentUserService;
    private final ReconciliationJobProgressService reconciliationProgressService;

    @Inject
    public ReverseGeocodingResource(
            ReverseGeocodingManagementService managementService,
            CurrentUserService currentUserService,
            ReconciliationJobProgressService reconciliationProgressService) {
        this.managementService = managementService;
        this.currentUserService = currentUserService;
        this.reconciliationProgressService = reconciliationProgressService;
    }

    /**
     * Extract current user ID from security context.
     */
    private UUID getCurrentUserId() {
       return currentUserService.getCurrentUserId();
    }

    /**
     * Get paginated list of geocoding results with optional filters.
     * Shows only entities relevant to current user (user-specific + originals they reference).
     *
     * @param providerName Filter by provider (optional)
     * @param searchText Search text for displayName, city, or country (optional)
     * @param page Page number (1-based, default 1)
     * @param limit Page size (default 50)
     * @param sortField Field to sort by (default lastAccessedAt)
     * @param sortOrder Sort order (asc or desc, default desc)
     * @return Paginated geocoding results
     */
    @GET
    public Response getGeocodingResults(
            @QueryParam("providerName") String providerName,
            @QueryParam("searchText") String searchText,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("sortField") @DefaultValue("lastAccessedAt") String sortField,
            @QueryParam("sortOrder") @DefaultValue("desc") String sortOrder) {

        UUID currentUserId = getCurrentUserId();

        log.debug("Fetching geocoding results for user {}: page={}, limit={}, provider={}, search={}",
                currentUserId, page, limit, providerName, searchText);

        List<ReverseGeocodingDTO> results = managementService.getGeocodingResults(
                currentUserId, providerName, searchText, page, limit, sortField, sortOrder);

        long totalRecords = managementService.countGeocodingResults(currentUserId, providerName, searchText);

        Map<String, Object> response = new HashMap<>();
        response.put("data", results);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", totalRecords);
        pagination.put("totalPages", (int) Math.ceil((double) totalRecords / limit));
        response.put("pagination", pagination);

        return Response.ok(response).build();
    }

    /**
     * Get a single geocoding result by ID.
     * User can only access originals or their own copies.
     *
     * @param id Geocoding result ID
     * @return Geocoding result details
     */
    @GET
    @Path("/{id}")
    public Response getGeocodingResult(@PathParam("id") Long id) {
        UUID currentUserId = getCurrentUserId();
        log.debug("Fetching geocoding result {} for user {}", id, currentUserId);

        try {
            ReverseGeocodingDTO result = managementService.getGeocodingResult(currentUserId, id);
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Update a geocoding result with copy-on-write semantics.
     * - Modifying original (user_id=NULL): Creates user-specific copy
     * - Modifying own copy: Updates in-place
     * - Modifying other's copy: Returns 403 Forbidden
     *
     * @param id Geocoding result ID
     * @param updateDTO Update data
     * @return Updated geocoding result (or newly created user copy)
     */
    @PUT
    @Path("/{id}")
    public Response updateGeocodingResult(
            @PathParam("id") Long id,
            @Valid ReverseGeocodingUpdateDTO updateDTO) {

        UUID currentUserId = getCurrentUserId();
        log.info("User {} updating geocoding result: {}", currentUserId, id);

        try {
            ReverseGeocodingDTO updated = managementService.updateGeocodingResult(currentUserId, id, updateDTO);
            return Response.ok(updated).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error updating geocoding result {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to update geocoding result"))
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
    public Response reconcileWithProviderBulk(@Valid ReverseGeocodingReconcileRequest request) {
        UUID currentUserId = getCurrentUserId();
        log.info("User {} starting bulk reconciliation with provider: {}", currentUserId, request.getProviderName());

        try {
            // Check if user already has active job
            Optional<ReconciliationJobProgress> activeJob = reconciliationProgressService.getUserActiveJob(currentUserId);
            if (activeJob.isPresent()) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of(
                                "error", "You already have an active reconciliation job",
                                "jobId", activeJob.get().getJobId().toString()
                        ))
                        .build();
            }

            UUID jobId = managementService.reconcileWithProviderAsync(currentUserId, request);
            return Response.ok(Map.of("jobId", jobId.toString())).build();

        } catch (Exception e) {
            log.error("Error starting reconciliation for user {}: {}", currentUserId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to start reconciliation: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get progress of a reconciliation job.
     *
     * @param jobId Job ID
     * @return Job progress
     */
    @GET
    @Path("/reconcile/jobs/{jobId}")
    public Response getReconciliationJobProgress(@PathParam("jobId") String jobId) {
        UUID currentUserId = getCurrentUserId();

        try {
            UUID jobUuid = UUID.fromString(jobId);
            Optional<ReconciliationJobProgress> jobProgress = reconciliationProgressService.getJobProgress(jobUuid);

            if (jobProgress.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Job not found"))
                        .build();
            }

            // Verify job belongs to current user
            if (!jobProgress.get().getUserId().equals(currentUserId)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(Map.of("error", "Access denied"))
                        .build();
            }

            return Response.ok(jobProgress.get()).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid job ID format"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get job progress for job {}", jobId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get job progress: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Reconcile geocoding results with a specific provider (single item, synchronous).
     * Kept for backward compatibility.
     * Applies copy-on-write if data changed for originals.
     *
     * @param request Reconciliation request
     * @return Reconciliation results
     */
    @POST
    @Path("/reconcile/single")
    public Response reconcileSingle(@Valid ReverseGeocodingReconcileRequest request) {
        UUID currentUserId = getCurrentUserId();
        log.info("User {} reconciling single result with provider: {}", currentUserId, request.getProviderName());

        try {
            ReverseGeocodingReconcileResult result = managementService.reconcileWithProvider(currentUserId, request);
            return Response.ok(result).build();
        } catch (Exception e) {
            log.error("Error reconciling geocoding results for user {}: {}", currentUserId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Reconciliation failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get list of enabled geocoding providers (for reconciliation).
     *
     * @return List of enabled providers
     */
    @GET
    @Path("/providers")
    public Response getEnabledProviders() {
        log.debug("Fetching enabled geocoding providers");

        List<GeocodingProviderDTO> providers = managementService.getEnabledProviders();
        return Response.ok(providers).build();
    }

    /**
     * Get list of providers that have data in the database (for filtering).
     *
     * @return List of provider names
     */
    @GET
    @Path("/providers/available")
    public Response getProvidersWithData() {
        log.debug("Fetching providers with data");

        List<String> providers = managementService.getProvidersWithData();
        return Response.ok(providers).build();
    }
}
