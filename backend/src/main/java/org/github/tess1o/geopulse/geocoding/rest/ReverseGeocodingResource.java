package org.github.tess1o.geopulse.geocoding.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geocoding.dto.ApplyNormalizationRulesRequest;
import org.github.tess1o.geopulse.geocoding.dto.BulkUpdateGeocodingDto;
import org.github.tess1o.geopulse.geocoding.dto.BulkUpdateGeocodingResult;
import org.github.tess1o.geopulse.geocoding.dto.CreateNormalizationRuleRequest;
import org.github.tess1o.geopulse.geocoding.dto.DistinctValuesDto;
import org.github.tess1o.geopulse.geocoding.dto.GeocodingProviderDTO;
import org.github.tess1o.geopulse.geocoding.dto.NormalizationRuleDto;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingReconcileRequest;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingReconcileResult;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingUpdateDTO;
import org.github.tess1o.geopulse.geocoding.dto.UpdateNormalizationRuleRequest;
import org.github.tess1o.geopulse.geocoding.model.ReconciliationJobProgress;
import org.github.tess1o.geopulse.geocoding.service.ReconciliationJobProgressService;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.geocoding.service.UserLocationNormalizationService;
import org.github.tess1o.geopulse.shared.api.JobResponse;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GEOCODING_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GEOCODING_RESULT_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_GEOCODING_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_RECONCILIATION_JOB_ID;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NORMALIZATION_RULE_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NORMALIZATION_RULE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.RECONCILIATION_ALREADY_ACTIVE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.RECONCILIATION_JOB_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.RECONCILIATION_JOB_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/geocoding")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Geocoding", description = "Manage reverse geocoding results, normalization, providers, and reconciliation.")
public class ReverseGeocodingResource {

    private final ReverseGeocodingManagementService managementService;
    private final CurrentUserService currentUserService;
    private final ReconciliationJobProgressService reconciliationProgressService;
    private final UserLocationNormalizationService normalizationService;

    @Inject
    public ReverseGeocodingResource(
            ReverseGeocodingManagementService managementService,
            CurrentUserService currentUserService,
            ReconciliationJobProgressService reconciliationProgressService,
            UserLocationNormalizationService normalizationService) {
        this.managementService = managementService;
        this.currentUserService = currentUserService;
        this.reconciliationProgressService = reconciliationProgressService;
        this.normalizationService = normalizationService;
    }

    @GET
    public PageResponse<ReverseGeocodingDTO> getGeocodingResults(
            @QueryParam("providerName") String providerName,
            @QueryParam("searchText") String searchText,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("sortField") @DefaultValue("lastAccessedAt") String sortField,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {
        if (page < 1 || limit < 1) {
            throw problem(INVALID_GEOCODING_REQUEST, "page and limit must be positive");
        }
        UUID userId = currentUserService.getCurrentUserId();
        List<ReverseGeocodingDTO> results = managementService.getGeocodingResults(
                userId, providerName, searchText, page, limit, sortField, sortDirection);
        long total = managementService.countGeocodingResults(userId, providerName, searchText);
        return new PageResponse<>(results, page, limit, total, (int) Math.ceil((double) total / limit));
    }

    @GET
    @Path("/{id}")
    public ReverseGeocodingDTO getGeocodingResult(@PathParam("id") Long id) {
        try {
            return managementService.getGeocodingResult(currentUserService.getCurrentUserId(), id);
        } catch (NotFoundException e) {
            throw problem(GEOCODING_RESULT_NOT_FOUND, detail(e, "Geocoding result not found"), Map.of("id", id));
        } catch (ForbiddenException e) {
            throw problem(GEOCODING_ACCESS_DENIED, detail(e, "Access denied"));
        }
    }

    @PUT
    @Path("/{id}")
    public ReverseGeocodingDTO updateGeocodingResult(
            @PathParam("id") Long id, @Valid ReverseGeocodingUpdateDTO update) {
        try {
            return managementService.updateGeocodingResult(currentUserService.getCurrentUserId(), id, update);
        } catch (NotFoundException e) {
            throw problem(GEOCODING_RESULT_NOT_FOUND, detail(e, "Geocoding result not found"), Map.of("id", id));
        } catch (ForbiddenException e) {
            throw problem(GEOCODING_ACCESS_DENIED, detail(e, "Access denied"));
        }
    }

    @PATCH
    @Path("/bulk-update")
    public BulkUpdateGeocodingResult bulkUpdateGeocoding(@Valid BulkUpdateGeocodingDto request) {
        try {
            BulkUpdateGeocodingResult result = managementService.bulkUpdateGeocoding(
                    currentUserService.getCurrentUserId(), request);
            if (result.getSuccessCount() == 0) {
                throw problem(INVALID_GEOCODING_REQUEST, "Failed to update any geocoding results");
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_GEOCODING_REQUEST, detail(e, "Invalid update data"));
        }
    }

    @GET
    @Path("/distinct-values")
    public DistinctValuesDto getDistinctValues() {
        return managementService.getDistinctValues(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/normalization-rules")
    public List<NormalizationRuleDto> listNormalizationRules() {
        return normalizationService.listRules(currentUserService.getCurrentUserId());
    }

    @POST
    @Path("/normalization-rules")
    public RestResponse<NormalizationRuleDto> createNormalizationRule(
            @Valid CreateNormalizationRuleRequest request) {
        try {
            return RestResponse.status(Response.Status.CREATED,
                    normalizationService.createRule(currentUserService.getCurrentUserId(), request));
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_GEOCODING_REQUEST, detail(e, "Invalid normalization rule"));
        }
    }

    @PUT
    @Path("/normalization-rules/{id}")
    public NormalizationRuleDto updateNormalizationRule(
            @PathParam("id") Long id, @Valid UpdateNormalizationRuleRequest request) {
        try {
            return normalizationService.updateRule(currentUserService.getCurrentUserId(), id, request);
        } catch (NotFoundException e) {
            throw normalizationNotFound(e, id);
        } catch (ForbiddenException e) {
            throw problem(NORMALIZATION_RULE_ACCESS_DENIED, detail(e, "Access denied"));
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_GEOCODING_REQUEST, detail(e, "Invalid normalization rule"));
        }
    }

    @DELETE
    @Path("/normalization-rules/{id}")
    public RestResponse<Void> deleteNormalizationRule(@PathParam("id") Long id) {
        try {
            normalizationService.deleteRule(currentUserService.getCurrentUserId(), id);
            return RestResponse.noContent();
        } catch (NotFoundException e) {
            throw normalizationNotFound(e, id);
        } catch (ForbiddenException e) {
            throw problem(NORMALIZATION_RULE_ACCESS_DENIED, detail(e, "Access denied"));
        }
    }

    @POST
    @Path("/normalization-rules/apply")
    public JobResponse applyNormalizationRules(@Valid ApplyNormalizationRulesRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        rejectActiveJob(userId);
        return new JobResponse(managementService.applyNormalizationRulesAsync(userId, request));
    }

    @POST
    @Path("/normalization-rules/{id}/apply")
    public JobResponse applyNormalizationRule(
            @PathParam("id") Long id, @Valid ApplyNormalizationRulesRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        rejectActiveJob(userId);
        try {
            return new JobResponse(managementService.applyNormalizationRuleAsync(userId, id, request));
        } catch (NotFoundException e) {
            throw normalizationNotFound(e, id);
        } catch (ForbiddenException e) {
            throw problem(NORMALIZATION_RULE_ACCESS_DENIED, detail(e, "Access denied"));
        }
    }

    @POST
    @Path("/reconcile/bulk")
    public JobResponse reconcileWithProviderBulk(@Valid ReverseGeocodingReconcileRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        rejectActiveJob(userId);
        return new JobResponse(managementService.reconcileWithProviderAsync(userId, request));
    }

    @GET
    @Path("/reconcile/jobs/{jobId}")
    public ReconciliationJobProgress getReconciliationJobProgress(@PathParam("jobId") String jobId) {
        UUID id;
        try {
            id = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_RECONCILIATION_JOB_ID, "Invalid job ID format", Map.of("jobId", jobId));
        }
        ReconciliationJobProgress progress = reconciliationProgressService.getJobProgress(id)
                .orElseThrow(() -> problem(RECONCILIATION_JOB_NOT_FOUND, "Reconciliation job not found",
                        Map.of("jobId", jobId)));
        if (!progress.getUserId().equals(currentUserService.getCurrentUserId())) {
            throw problem(RECONCILIATION_JOB_ACCESS_DENIED, "Access denied");
        }
        return progress;
    }

    @POST
    @Path("/reconcile/single")
    public ReverseGeocodingReconcileResult reconcileSingle(@Valid ReverseGeocodingReconcileRequest request) {
        return managementService.reconcileWithProvider(currentUserService.getCurrentUserId(), request);
    }

    @GET
    @Path("/providers")
    public List<GeocodingProviderDTO> getEnabledProviders() {
        return managementService.getEnabledProviders();
    }

    @GET
    @Path("/providers/available")
    public List<String> getProvidersWithData() {
        return managementService.getProvidersWithData();
    }

    private void rejectActiveJob(UUID userId) {
        reconciliationProgressService.getUserActiveJob(userId).ifPresent(active -> {
            throw problem(RECONCILIATION_ALREADY_ACTIVE, "A reconciliation job is already active",
                    Map.of("jobId", active.getJobId().toString()));
        });
    }

    private static io.quarkiverse.httpproblem.HttpProblem normalizationNotFound(
            NotFoundException exception, Long id) {
        return problem(NORMALIZATION_RULE_NOT_FOUND, detail(exception, "Normalization rule not found"),
                Map.of("id", id));
    }

    private static String detail(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback
                : exception.getMessage();
    }
}
