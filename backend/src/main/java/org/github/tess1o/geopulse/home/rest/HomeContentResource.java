package org.github.tess1o.geopulse.home.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.home.service.HomeContentService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/home/content")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User: Home", description = "Read content used by the home page.")
public class HomeContentResource {

    private final HomeContentService homeContentService;

    @jakarta.inject.Inject
    public HomeContentResource(HomeContentService homeContentService) {
        this.homeContentService = homeContentService;
    }

    @GET
    public Response getHomeContent() {
        return Response.ok(homeContentService.getContent()).build();
    }
}
