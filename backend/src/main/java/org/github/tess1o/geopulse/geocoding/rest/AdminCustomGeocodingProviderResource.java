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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderRequest;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderResponse;
import org.github.tess1o.geopulse.geocoding.service.CustomGeocodingProviderService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/admin/geocoding/providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.ADMIN_GEOCODING_PROVIDERS)
public class AdminCustomGeocodingProviderResource {

    private final CustomGeocodingProviderService providerService;

    @Inject
    public AdminCustomGeocodingProviderResource(CustomGeocodingProviderService providerService) {
        this.providerService = providerService;
    }

    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    @Operation(summary = "List custom geocoding providers",
            description = "Returns the additional reverse-geocoding providers configured on this server.")
    public List<CustomGeocodingProviderResponse> list() {
        return providerService.list();
    }

    @POST
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Add a custom geocoding provider",
            description = "Adds a reverse-geocoding provider instance of type `nominatim` or `photon`, such as a "
                    + "self-hosted server, with optional language, extra HTTP headers, and a delay between "
                    + "requests. It can then be chosen as the primary or fallback provider in the geocoding "
                    + "settings.")
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
    @Operation(summary = "Update a custom geocoding provider",
            description = "Updates a custom reverse-geocoding provider.")
    public CustomGeocodingProviderResponse update(
            @Parameter(description = "Provider name (identifier).", example = "my-nominatim")
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
    @Operation(summary = "Delete a custom geocoding provider",
            description = "Deletes a custom reverse-geocoding provider. A provider selected as primary or fallback "
                    + "cannot be deleted.")
    public void delete(
            @Parameter(description = "Provider name (identifier).", example = "my-nominatim")
            @PathParam("name") String name) {
        try {
            providerService.delete(name);
        } catch (NotFoundException e) {
            throw new GeoPulseException(NOT_FOUND, NOT_FOUND.title(), e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_CUSTOM_GEOCODING_PROVIDER, INVALID_CUSTOM_GEOCODING_PROVIDER.title(), e);
        }
    }
}
