package org.github.tess1o.geopulse.home.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.home.service.HomeContentService;

@Path("/api/home/content")
@Produces(MediaType.APPLICATION_JSON)
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
