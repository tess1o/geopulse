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

@Path("/api/journey-insights")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Journey Insights", description = "Read journey insight summaries.")
public class JourneyInsightResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    JourneyInsightService journeyInsightService;

    @GET
    public JourneyInsights getJourneyInsights() {
        UUID userId = currentUserService.getCurrentUserId();
        return journeyInsightService.getJourneyInsights(userId);
    }
}