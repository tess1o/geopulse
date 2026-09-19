package org.github.tess1o.geopulse.geocoding.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderResponse;
import org.github.tess1o.geopulse.geocoding.service.CustomGeocodingProviderService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/admin/geocoding/providers")
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
    public List<CustomGeocodingProviderResponse> list() {
        return providerService.list();
    }

    @POST
    @RolesAllowed(SecurityRoles.ADMIN)
    public RestResponse<CustomGeocodingProviderResponse> create(@Valid CustomGeocodingProviderRequest request) {
        try {
            return RestResponse.status(Response.Status.CREATED, providerService.create(request));
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_CUSTOM_GEOCODING_PROVIDER, INVALID_CUSTOM_GEOCODING_PROVIDER.title(), e);
        }
    }

    @PUT
    @Path("/{name}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public CustomGeocodingProviderResponse update(
            @PathParam("name") String name,
            @Valid CustomGeocodingProviderRequest request) {
        try {
            return providerService.update(name, request);
        } catch (NotFoundException e) {
            throw new GeoPulseException(NOT_FOUND, NOT_FOUND.title(), e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_CUSTOM_GEOCODING_PROVIDER, INVALID_CUSTOM_GEOCODING_PROVIDER.title(), e);
        }
    }

    @DELETE
    @Path("/{name}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public void delete(@PathParam("name") String name) {
        try {
            providerService.delete(name);
        } catch (NotFoundException e) {
            throw new GeoPulseException(NOT_FOUND, NOT_FOUND.title(), e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_CUSTOM_GEOCODING_PROVIDER, INVALID_CUSTOM_GEOCODING_PROVIDER.title(), e);
        }
    }
}
