package org.github.tess1o.geopulse.coverage.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.coverage.model.CoverageSettingsRequest;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/coverage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Coverage", description = "Read and manage coverage grid status, cells, and recalculation jobs.")
public class CoverageResource {

    private final CoverageService coverageService;
    private final CoverageProcessingService processingService;
    private final CurrentUserService currentUserService;
    private final ImportJobService importJobService;

    @Inject
    public CoverageResource(CoverageService coverageService,
                            CoverageProcessingService processingService,
                            CurrentUserService currentUserService,
                            ImportJobService importJobService) {
        this.coverageService = coverageService;
        this.processingService = processingService;
        this.currentUserService = currentUserService;
        this.importJobService = importJobService;
    }

    @GET
    @Path("/status")
    public CoverageStatus getCoverageStatus() {
        UUID userId = currentUserService.getCurrentUserId();
        return coverageService.getCoverageStatus(userId);
    }

    @PUT
    @Path("/settings")
    @APIResponse(responseCode = "200", description = "Coverage settings updated")
    @APIResponse(responseCode = "400", description = "Invalid coverage settings")
    public CoverageStatus updateCoverageSettings(CoverageSettingsRequest request) {
        if (request == null || request.enabled() == null) {
            throw problem(COVERAGE_ENABLED_REQUIRED, "enabled is required",
                    Map.of("field", "enabled"));
        }

        UUID userId = currentUserService.getCurrentUserId();
        boolean enable = request.enabled();

        coverageService.setUserCoverageEnabled(userId, enable);

        if (enable) {
            processingService.startProcessingAsync(userId);
        }

        return coverageService.getCoverageStatus(userId);
    }

    @POST
    @Path("/recalculate")
    @APIResponse(responseCode = "200", description = "Coverage recalculation started")
    @APIResponse(responseCode = "400", description = "Coverage is not enabled")
    @APIResponse(responseCode = "409", description = "An import already manages recalculation")
    public CoverageStatus recalculateCoverage() {
        UserEntity user = currentUserService.getCurrentUser();
        if (!user.isCoverageEnabled()) {
            throw problem(COVERAGE_DISABLED, "Coverage is not enabled for this user");
        }

        UUID userId = user.getId();
        if (importJobService.hasActiveImportJob(userId)) {
            throw problem(COVERAGE_RECALCULATION_CONFLICT,
                    "Coverage recalculation is already managed by the active import job");
        }

        processingService.startFullRecalculationAsync(userId);

        return coverageService.getCoverageStatus(userId);
    }

    @GET
    @Path("/cells")
    @APIResponse(responseCode = "200", description = "Coverage cells retrieved")
    @APIResponse(responseCode = "400", description = "Invalid coverage query")
    @APIResponse(responseCode = "403", description = "Coverage is not enabled")
    public List<CoverageCell> getCoverageCells(@QueryParam("bbox") String bbox,
                                                @QueryParam("grid") Integer gridMeters,
                                                @QueryParam("limit") @Min(1) Integer limit) {
        UserEntity user = currentUserService.getCurrentUser();
        if (!user.isCoverageEnabled()) {
            throw problem(COVERAGE_DISABLED, "Coverage is not enabled for this user");
        }

        if (bbox == null || bbox.isBlank()) {
            throw problem(INVALID_BOUNDING_BOX, "bbox is required (minLon,minLat,maxLon,maxLat)");
        }

        double[] bounds;
        try {
            bounds = parseBbox(bbox);
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_BOUNDING_BOX, e.getMessage());
        }

        int grid = gridMeters == null ? CoverageDefaults.DEFAULT_GRID_METERS : gridMeters;
        if (!coverageService.isGridSupported(grid)) {
            throw problem(UNSUPPORTED_COVERAGE_GRID, "Unsupported grid size", Map.of("grid", grid));
        }
        int cellLimit = CoverageDefaults.DEFAULT_CELLS_PER_VIEW;
        if (limit != null) {
            if (limit < 1) {
                throw problem(INVALID_LIMIT, "limit must be greater than 0", Map.of("min", 1));
            }
            cellLimit = Math.min(limit, CoverageDefaults.MAX_CELLS_PER_VIEW);
        }

        UUID userId = user.getId();

        double minLon = Math.min(bounds[0], bounds[2]);
        double minLat = Math.min(bounds[1], bounds[3]);
        double maxLon = Math.max(bounds[0], bounds[2]);
        double maxLat = Math.max(bounds[1], bounds[3]);

        List<CoverageCell> cells = coverageService.getCoverageCells(
                userId,
                minLon,
                minLat,
                maxLon,
                maxLat,
                grid,
                cellLimit
        );

        return cells;
    }

    @GET
    @Path("/summary")
    @APIResponse(responseCode = "200", description = "Coverage summary retrieved")
    @APIResponse(responseCode = "400", description = "Invalid coverage grid")
    @APIResponse(responseCode = "403", description = "Coverage is not enabled")
    public CoverageSummary getCoverageSummary(@QueryParam("grid") Integer gridMeters) {
        UserEntity user = currentUserService.getCurrentUser();
        if (!user.isCoverageEnabled()) {
            throw problem(COVERAGE_DISABLED, "Coverage is not enabled for this user");
        }

        int grid = gridMeters == null ? CoverageDefaults.DEFAULT_GRID_METERS : gridMeters;
        if (!coverageService.isGridSupported(grid)) {
            throw problem(UNSUPPORTED_COVERAGE_GRID, "Unsupported grid size", Map.of("grid", grid));
        }

        UUID userId = user.getId();

        return coverageService.getCoverageSummary(userId, grid);
    }


    private double[] parseBbox(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("bbox must have 4 comma-separated values");
        }

        double[] bounds = new double[4];
        for (int i = 0; i < 4; i++) {
            bounds[i] = Double.parseDouble(parts[i].trim());
            if (!Double.isFinite(bounds[i])) {
                throw new IllegalArgumentException("bbox values must be finite numbers");
            }
        }

        if (bounds[0] < -180 || bounds[0] > 180 || bounds[2] < -180 || bounds[2] > 180) {
            throw new IllegalArgumentException("bbox longitude values must be between -180 and 180");
        }
        if (bounds[1] < -90 || bounds[1] > 90 || bounds[3] < -90 || bounds[3] > 90) {
            throw new IllegalArgumentException("bbox latitude values must be between -90 and 90");
        }

        return bounds;
    }
}
