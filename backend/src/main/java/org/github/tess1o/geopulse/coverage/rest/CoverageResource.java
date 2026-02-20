package org.github.tess1o.geopulse.coverage.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.List;
import java.util.UUID;

@Path("/api/coverage")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({ "USER", "ADMIN" })
public class CoverageResource {

    @Inject
    CoverageService coverageService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/cells")
    public Response getCoverageCells(@QueryParam("bbox") String bbox,
                                     @QueryParam("grid") Integer gridMeters) {
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

        var user = currentUserService.getCurrentUser();
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
                grid
        );

        return Response.ok(ApiResponse.success(cells)).build();
    }

    @GET
    @Path("/summary")
    public Response getCoverageSummary(@QueryParam("grid") Integer gridMeters) {
        int grid = gridMeters == null ? CoverageDefaults.DEFAULT_GRID_METERS : gridMeters;
        if (!coverageService.isGridSupported(grid)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Unsupported grid size"))
                    .build();
        }

        var user = currentUserService.getCurrentUser();
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
