package org.github.tess1o.geopulse.mapmatching.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

public interface ValhallaRestClient {
    @POST
    @Path("/trace_route")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ValhallaTraceRouteResponse traceRoute(ValhallaTraceRouteRequest request);
}
