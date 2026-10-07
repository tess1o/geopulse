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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

@Path("/home-content")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = ApiTags.HOME)
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
    @Operation(summary = "Get home page content",
            description = "Returns the tips and \"What's new\" release highlights shown on the GeoPulse home page, "
                    + "translated to the given locale.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public HomeContentResponse getHomeContent(
            @Parameter(description = "UI locale for translated tips (e.g. en, uk); defaults to en")
            @QueryParam("locale") String locale
            ) {
        return homeContentService.getContent(locale);
    }
}
