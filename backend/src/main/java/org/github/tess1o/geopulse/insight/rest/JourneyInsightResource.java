package org.github.tess1o.geopulse.insight.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.insight.model.JourneyInsights;
import org.github.tess1o.geopulse.insight.service.JourneyInsightService;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/journey-insights")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.JOURNEY_INSIGHTS)
public class JourneyInsightResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    JourneyInsightService journeyInsightService;

    @GET
    @Operation(summary = "Get journey insights",
            description = "Returns long-term insights computed from all of the signed-in user's data: geography "
                    + "(countries and cities), time patterns, distance traveled, weather, and achievements.")
    public JourneyInsights getJourneyInsights() {
        UUID userId = currentUserService.getCurrentUserId();
        return journeyInsightService.getJourneyInsights(userId);
    }
}