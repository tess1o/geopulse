package org.github.tess1o.geopulse.geocoding.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderRequest;
import org.github.tess1o.geopulse.geocoding.service.CustomGeocodingProviderService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

@Path("/api/admin/geocoding/providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Admin: Geocoding Providers", description = "Manage custom geocoding provider instances.")
public class AdminCustomGeocodingProviderResource {

    private final CustomGeocodingProviderService providerService;

    @Inject
    public AdminCustomGeocodingProviderResource(CustomGeocodingProviderService providerService) {
        this.providerService = providerService;
    }

    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public Response list() {
        return Response.ok(ApiResponse.success(providerService.list())).build();
    }

    @POST
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response create(@Valid CustomGeocodingProviderRequest request) {
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(providerService.create(request)))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{name}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response update(@PathParam("name") String name, @Valid CustomGeocodingProviderRequest request) {
        try {
            return Response.ok(ApiResponse.success(providerService.update(name, request))).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{name}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response delete(@PathParam("name") String name) {
        try {
            providerService.delete(name);
            return Response.ok(ApiResponse.success("Custom geocoding provider deleted")).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }
}
