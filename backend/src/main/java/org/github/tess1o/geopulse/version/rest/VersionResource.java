package org.github.tess1o.geopulse.version.rest;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.version.service.VersionStatusService;
import org.github.tess1o.geopulse.version.dto.GeoPulseVersionResponse;
import org.github.tess1o.geopulse.version.dto.VersionStatusResponse;

@Path("/api/version")
@Tag(name = "User: System", description = "Read application version and version status.")
public class VersionResource {

    @ConfigProperty(name = "quarkus.application.version")
    @StaticInitSafe
    String version;

    @Inject
    VersionStatusService versionStatusService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public GeoPulseVersionResponse getVersion() {
        return new GeoPulseVersionResponse(version);
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public VersionStatusResponse getVersionStatus() {
        return versionStatusService.getVersionStatus();
    }
}
