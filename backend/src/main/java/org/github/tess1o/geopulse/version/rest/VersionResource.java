package org.github.tess1o.geopulse.version.rest;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.version.service.VersionStatusService;
import org.github.tess1o.geopulse.version.dto.GeoPulseVersionResponse;
import org.github.tess1o.geopulse.version.dto.VersionStatusResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/system/version")
@Tag(name = ApiTags.SYSTEM)
public class VersionResource {

    @ConfigProperty(name = "quarkus.application.version")
    @StaticInitSafe
    String version;

    @Inject
    VersionStatusService versionStatusService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get the server version",
            description = "Returns the version of the running GeoPulse server.")
    public GeoPulseVersionResponse getVersion() {
        return new GeoPulseVersionResponse(version);
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Check for updates",
            description = "Returns the running version, the latest released version, and whether an update is "
                    + "available, with a link to the release notes.")
    public VersionStatusResponse getVersionStatus() {
        return versionStatusService.getVersionStatus();
    }
}
