package org.github.tess1o.geopulse.geocoding.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GEOCODING_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GEOCODING_RESULT_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_GEOCODING_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_RECONCILIATION_JOB_ID;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NORMALIZATION_RULE_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NORMALIZATION_RULE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.RECONCILIATION_ALREADY_ACTIVE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.RECONCILIATION_JOB_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.RECONCILIATION_JOB_NOT_FOUND;

@Path("/geocoding")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.GEOCODING)
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
    @Operation(summary = "List geocoding results",
            description = "Returns the reverse-geocoding results used by the signed-in user's stays, one page at a "
                    + "time, with optional provider and text filters.")
    public PageResponse<ReverseGeocodingDTO> getGeocodingResults(
            @Parameter(description = "Only results from this geocoding provider.", example = "Nominatim")
            @QueryParam("providerName") String providerName,
            @Parameter(description = "Text to search for in the name, city, or country.")
            @QueryParam("searchText") String searchText,
            @Parameter(description = "Page number, starting at 1.")
            @QueryParam("page") @DefaultValue("1") int page,
            @Parameter(description = "Page size. Defaults to 50.")
            @QueryParam("limit") @DefaultValue("50") int limit,
            @Parameter(description = "Sort field: `displayName`, `city`, `country`, `providerName`, `createdAt`, or "
                    + "`lastAccessedAt` (default).")
            @QueryParam("sortField") @DefaultValue("lastAccessedAt") String sortField,
            @Parameter(description = "Sort direction: `asc` or `desc`. Defaults to `desc`.")
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDirection) {
        if (page < 1 || limit < 1) {
            throw new GeoPulseException(INVALID_GEOCODING_REQUEST, "page and limit must be positive");
        }
        UUID userId = currentUserService.getCurrentUserId();
        List<ReverseGeocodingDTO> results = managementService.getGeocodingResults(
                userId, providerName, searchText, page, limit, sortField, sortDirection);
        long total = managementService.countGeocodingResults(userId, providerName, searchText);
        return new PageResponse<>(results, page, limit, total, (int) Math.ceil((double) total / limit));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a geocoding result",
            description = "Returns one reverse-geocoding result: name, address, city, country, coordinates, and "
                    + "provider.")
    public ReverseGeocodingDTO getGeocodingResult(
            @Parameter(description = "Geocoding result ID.")
            @PathParam("id") Long id) {
        try {
            return managementService.getGeocodingResult(currentUserService.getCurrentUserId(), id);
        } catch (NotFoundException e) {
            throw new GeoPulseException(GEOCODING_RESULT_NOT_FOUND, "Geocoding result not found", Map.of("id", id), e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(GEOCODING_ACCESS_DENIED, "Access denied", e);
        }
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a geocoding result",
            description = "Corrects the name, city, or country of a reverse-geocoding result. Shared provider "
                    + "results are not changed: the first edit creates a private copy for the signed-in user, and "
                    + "the user's stays are updated to use it.")
    public ReverseGeocodingDTO updateGeocodingResult(
            @Parameter(description = "Geocoding result ID.")
            @PathParam("id") Long id, @Valid ReverseGeocodingUpdateDTO update) {
        try {
            return managementService.updateGeocodingResult(currentUserService.getCurrentUserId(), id, update);
        } catch (NotFoundException e) {
            throw new GeoPulseException(GEOCODING_RESULT_NOT_FOUND, "Geocoding result not found", Map.of("id", id), e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(GEOCODING_ACCESS_DENIED, "Access denied", e);
        }
    }

    @PATCH
    @Path("/bulk-update")
    @Operation(summary = "Set city or country of several results",
            description = "Sets the city, the country, or both, on several geocoding results at once. Shared "
                    + "provider results are not changed: the first edit creates a private copy for the signed-in "
                    + "user, and the user's stays are updated to use it.")
    public BulkUpdateGeocodingResult bulkUpdateGeocoding(@Valid BulkUpdateGeocodingDto request) {
        try {
            BulkUpdateGeocodingResult result = managementService.bulkUpdateGeocoding(
                    currentUserService.getCurrentUserId(), request);
            if (result.getSuccessCount() == 0) {
                throw new GeoPulseException(INVALID_GEOCODING_REQUEST, "Failed to update any geocoding results");
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_GEOCODING_REQUEST, "Invalid update data", e);
        }
    }

    @GET
    @Path("/distinct-values")
    @Operation(summary = "List geocoded cities and countries",
            description = "Returns the distinct city and country names in the signed-in user's geocoding results, "
                    + "for filters and autocomplete.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public DistinctValuesDto getDistinctValues() {
        return managementService.getDistinctValues(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/normalization-rules")
    @Operation(summary = "List normalization rules",
            description = "Returns the signed-in user's rules for renaming cities or countries, for example \"Kiev\" "
                    + "to \"Kyiv\".")
    public List<NormalizationRuleDto> listNormalizationRules() {
        return normalizationService.listRules(currentUserService.getCurrentUserId());
    }

    @POST
    @Path("/normalization-rules")
    @Operation(summary = "Create a normalization rule",
            description = "Creates a rule that renames a country (`ruleType: COUNTRY`) or a city within a country "
                    + "(`ruleType: CITY`). New favorites and re-resolved locations use the rule automatically; "
                    + "apply it to existing data with the apply endpoints.")
    public RestResponse<NormalizationRuleDto> createNormalizationRule(
            @Valid CreateNormalizationRuleRequest request) {
        try {
            return RestResponse.status(Response.Status.CREATED,
                    normalizationService.createRule(currentUserService.getCurrentUserId(), request));
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_GEOCODING_REQUEST, "Invalid normalization rule", e);
        }
    }

    @PUT
    @Path("/normalization-rules/{id}")
    @Operation(summary = "Update a normalization rule",
            description = "Changes the source or target names of a normalization rule. Existing data is not changed "
                    + "until the rule is applied.")
    public NormalizationRuleDto updateNormalizationRule(
            @Parameter(description = "Normalization rule ID.")
            @PathParam("id") Long id, @Valid UpdateNormalizationRuleRequest request) {
        try {
            return normalizationService.updateRule(currentUserService.getCurrentUserId(), id, request);
        } catch (NotFoundException e) {
            throw normalizationNotFound(e, id);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(NORMALIZATION_RULE_ACCESS_DENIED, "Access denied", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_GEOCODING_REQUEST, "Invalid normalization rule", e);
        }
    }

    @DELETE
    @Path("/normalization-rules/{id}")
    @Operation(summary = "Delete a normalization rule",
            description = "Deletes a normalization rule. Names it already changed stay as they are.")
    public RestResponse<Void> deleteNormalizationRule(
            @Parameter(description = "Normalization rule ID.")
            @PathParam("id") Long id) {
        try {
            normalizationService.deleteRule(currentUserService.getCurrentUserId(), id);
            return RestResponse.noContent();
        } catch (NotFoundException e) {
            throw normalizationNotFound(e, id);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(NORMALIZATION_RULE_ACCESS_DENIED, "Access denied", e);
        }
    }

    @POST
    @Path("/normalization-rules/apply")
    @Operation(summary = "Apply all normalization rules",
            description = "Starts a background job that applies all of the user's normalization rules to existing "
                    + "geocoding results, favorites, or both. Poll `GET /api/v1/geocoding/reconcile/jobs/{jobId}` "
                    + "for progress. Only one such job per user can run at a time.")
    public JobResponse applyNormalizationRules(@Valid ApplyNormalizationRulesRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        rejectActiveJob(userId);
        return new JobResponse(managementService.applyNormalizationRulesAsync(userId, request));
    }

    @POST
    @Path("/normalization-rules/{id}/apply")
    @Operation(summary = "Apply one normalization rule",
            description = "Starts a background job that applies a single normalization rule to existing geocoding "
                    + "results, favorites, or both. Poll `GET /api/v1/geocoding/reconcile/jobs/{jobId}` for "
                    + "progress. Only one such job per user can run at a time.")
    public JobResponse applyNormalizationRule(
            @Parameter(description = "Normalization rule ID.")
            @PathParam("id") Long id, @Valid ApplyNormalizationRulesRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        rejectActiveJob(userId);
        try {
            return new JobResponse(managementService.applyNormalizationRuleAsync(userId, id, request));
        } catch (NotFoundException e) {
            throw normalizationNotFound(e, id);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(NORMALIZATION_RULE_ACCESS_DENIED, "Access denied", e);
        }
    }

    @POST
    @Path("/reconcile/bulk")
    @Operation(summary = "Re-resolve geocoding results",
            description = "Starts a background job that looks up locations again with a chosen geocoding provider "
                    + "and updates the results that changed. Pass specific `geocodingIds`, or set `reconcileAll` "
                    + "with an optional provider filter. Poll `GET /api/v1/geocoding/reconcile/jobs/{jobId}` for "
                    + "progress. Only one such job per user can run at a time.")
    public JobResponse reconcileWithProviderBulk(@Valid ReverseGeocodingReconcileRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        rejectActiveJob(userId);
        return new JobResponse(managementService.reconcileWithProviderAsync(userId, request));
    }

    @GET
    @Path("/reconcile/jobs/{jobId}")
    @Operation(summary = "Get geocoding job progress",
            description = "Returns the progress and result of a geocoding reconciliation or normalization job.")
    public ReconciliationJobProgress getReconciliationJobProgress(
            @Parameter(description = "Job ID.")
            @PathParam("jobId") String jobId) {
        UUID id;
        try {
            id = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_RECONCILIATION_JOB_ID, "Invalid job ID format", Map.of("jobId", jobId), e);
        }
        ReconciliationJobProgress progress = reconciliationProgressService.getJobProgress(id)
                .orElseThrow(() -> new GeoPulseException(RECONCILIATION_JOB_NOT_FOUND, "Reconciliation job not found",
                        Map.of("jobId", jobId)));
        if (!progress.getUserId().equals(currentUserService.getCurrentUserId())) {
            throw new GeoPulseException(RECONCILIATION_JOB_ACCESS_DENIED, "Access denied");
        }
        return progress;
    }

    @POST
    @Path("/reconcile/single")
    @Operation(summary = "Re-resolve one geocoding result",
            description = "Looks up the given locations again with a chosen geocoding provider and returns the "
                    + "result immediately, without a background job.")
    public ReverseGeocodingReconcileResult reconcileSingle(@Valid ReverseGeocodingReconcileRequest request) {
        return managementService.reconcileWithProvider(currentUserService.getCurrentUserId(), request);
    }

    @GET
    @Path("/providers")
    @Operation(summary = "List geocoding providers",
            description = "Returns the geocoding providers enabled on this server, which can be used for "
                    + "re-resolving locations.")
    public List<GeocodingProviderDTO> getEnabledProviders() {
        return managementService.getEnabledProviders();
    }

    @GET
    @Path("/providers/available")
    @Operation(summary = "List providers used by your data",
            description = "Returns the names of the providers that produced the signed-in user's geocoding results, "
                    + "for filtering.")
    public List<String> getProvidersWithData() {
        return managementService.getProvidersWithData();
    }

    private void rejectActiveJob(UUID userId) {
        reconciliationProgressService.getUserActiveJob(userId).ifPresent(active -> {
            throw new GeoPulseException(RECONCILIATION_ALREADY_ACTIVE, "A reconciliation job is already active",
                    Map.of("jobId", active.getJobId().toString()));
        });
    }

    private static GeoPulseException normalizationNotFound(
            NotFoundException exception, Long id) {
        return new GeoPulseException(NORMALIZATION_RULE_NOT_FOUND, "Normalization rule not found",
                Map.of("id", id), exception);
    }
}
