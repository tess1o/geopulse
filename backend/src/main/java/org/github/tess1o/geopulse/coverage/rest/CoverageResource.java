package org.github.tess1o.geopulse.coverage.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.coverage.model.CoverageSettingsRequest;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
import java.util.UUID;

@Path("/api/coverage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({ "USER", "ADMIN" })
public class CoverageResource {

    private final CoverageService coverageService;
    private final CoverageProcessingService processingService;
    private final CurrentUserService currentUserService;

    @Inject
    public CoverageResource(CoverageService coverageService,
                            CoverageProcessingService processingService,
                            CurrentUserService currentUserService) {
        this.coverageService = coverageService;
        this.processingService = processingService;
        this.currentUserService = currentUserService;
    }

    @GET
    @Path("/status")
    public Response getCoverageStatus() {
        UUID userId = currentUserService.getCurrentUserId();
        CoverageStatus status = coverageService.getCoverageStatus(userId);
        return Response.ok(ApiResponse.success(status)).build();
    }

    @PUT
    @Path("/settings")
    public Response updateCoverageSettings(CoverageSettingsRequest request) {
        if (request == null || request.enabled() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("enabled is required"))
                    .build();
        }

        UUID userId = currentUserService.getCurrentUserId();
        boolean enable = request.enabled();

        coverageService.setUserCoverageEnabled(userId, enable);

        if (enable) {
            processingService.startProcessingAsync(userId);
        }

        CoverageStatus status = coverageService.getCoverageStatus(userId);
        return Response.ok(ApiResponse.success(status)).build();
    }

    @GET
    @Path("/cells")
    public Response getCoverageCells(@QueryParam("bbox") String bbox,
                                     @QueryParam("grid") Integer gridMeters,
                                     @QueryParam("limit") @Min(1) Integer limit) {
        UserEntity user = currentUserService.getCurrentUser();
        if (!user.isCoverageEnabled()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Coverage is not enabled for this user"))
                    .build();
        }

        if (bbox == null || bbox.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("bbox is required (minLon,minLat,maxLon,maxLat)"))
                    .build();
        }

        double[] bounds;
        try {
            bounds = parseBbox(bbox);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }

        int grid = gridMeters == null ? CoverageDefaults.DEFAULT_GRID_METERS : gridMeters;
        if (!coverageService.isGridSupported(grid)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Unsupported grid size"))
                    .build();
        }
        int cellLimit = CoverageDefaults.DEFAULT_CELLS_PER_VIEW;
        if (limit != null) {
            if (limit < 1) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("limit must be greater than 0"))
                        .build();
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

        return Response.ok(ApiResponse.success(cells)).build();
    }

    @GET
    @Path("/summary")
    public Response getCoverageSummary(@QueryParam("grid") Integer gridMeters) {
        UserEntity user = currentUserService.getCurrentUser();
        if (!user.isCoverageEnabled()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Coverage is not enabled for this user"))
                    .build();
        }

        int grid = gridMeters == null ? CoverageDefaults.DEFAULT_GRID_METERS : gridMeters;
        if (!coverageService.isGridSupported(grid)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Unsupported grid size"))
                    .build();
        }

        UUID userId = user.getId();

        CoverageSummary summary = coverageService.getCoverageSummary(userId, grid);
        return Response.ok(ApiResponse.success(summary)).build();
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
