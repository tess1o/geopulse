package org.github.tess1o.geopulse.gpssource.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.gpssource.model.*;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.List;
import java.util.UUID;

@Path("/api/gps/source")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("USER")
@RequestScoped
@Slf4j
public class GpsSourceConfigResource {

    private final GpsSourceService gpsSourceService;
    private final CurrentUserService currentUserService;

    public GpsSourceConfigResource(GpsSourceService gpsSourceService, CurrentUserService currentUserService) {
        this.gpsSourceService = gpsSourceService;
        this.currentUserService = currentUserService;
    }

    @Path("/")
    @GET
    public Response getGpsSourceConfigs() {
        UUID userId = currentUserService.getCurrentUserId();
        List<GpsSourceConfigDTO> configs = gpsSourceService.findGpsSourceConfigs(userId);
        return Response.ok(configs).build();
    }

    @Path("/")
    @POST
    //TODO: duplication check!!!
    public Response addGpsSourceConfig(CreateGpsSourceConfigDto config) {
        try {
            config.setUserId(currentUserService.getCurrentUserId());
            GpsSourceConfigDTO dto = gpsSourceService.addGpsSourceConfig(config);
            return Response.ok(dto).build();
        } catch (IllegalArgumentException e) {
            log.error("Unable to add config", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }

    @Path("/{id}")
    @DELETE
    public Response deleteGpsSourceConfig(@PathParam("id") UUID configId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            boolean isDeleted = gpsSourceService.deleteGpsSourceConfig(configId, userId);
            if (!isDeleted) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (Exception e) {
            log.error("Unable to delete config", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid config id"))
                    .build();
        }
        return Response.ok().build();
    }

    @Path("/{id}/status")
    @PUT
    public Response updateStatus(@PathParam("id") UUID configId, UpdateGpsSourceConfigStatusDto newStatus) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            boolean isDeleted = gpsSourceService.updateGpsConfigSourceStatus(configId, userId, newStatus.isStatus());
            if (!isDeleted) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (Exception e) {
            log.error("Unable to update status", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Unable to change the status"))
                    .build();
        }
        return Response.ok().build();
    }

    @Path("/")
    @PUT
    public Response updateGpsConfigSource(UpdateGpsSourceConfigDto config) {
        log.info("Updating config {}", config);
        try {
            UUID userId = currentUserService.getCurrentUserId();
            boolean updated = gpsSourceService.updateGpsConfigSource(config, userId);
            if (!updated) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (Exception e) {
            log.error("Unable to update config", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid config id"))
                    .build();
        }
        return Response.ok().build();
    }

    @Path("/endpoints")
    @GET
    public Response getGpsSourceEndpoints() {
        return Response.ok(new GpsSourceEndpointsDto(
                gpsSourceService.getOwntrackUrl(),
                gpsSourceService.getOverlandUrl()
        )).build();
    }
}
