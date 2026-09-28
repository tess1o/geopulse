package org.github.tess1o.geopulse.home.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.home.model.HomeContentResponse;
import org.github.tess1o.geopulse.home.service.HomeContentService;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/home-content")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User: Home", description = "Read content used by the home page.")
public class HomeContentResource {

    private final HomeContentService homeContentService;

    @jakarta.inject.Inject
    public HomeContentResource(HomeContentService homeContentService) {
        this.homeContentService = homeContentService;
    }

    /**
     * {@code locale} is optional and unauthenticated -- the home page is shown before login, so this
     * cannot rely on the signed-in user's stored language preference. The frontend passes its current
     * UI locale explicitly; an unsupported or missing value falls back to English (see
     * {@code SupportedLanguages.normalizeOrDefault}).
     */
    @GET
    public HomeContentResponse getHomeContent(
            @Parameter(description = "UI locale for translated tips (e.g. en, uk); defaults to en")
            @QueryParam("locale") String locale
    ) {
        return homeContentService.getContent(locale);
    }
}
