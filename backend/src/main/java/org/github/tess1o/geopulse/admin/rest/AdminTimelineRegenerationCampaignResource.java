package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.github.tess1o.geopulse.streaming.model.dto.CreateTimelineRegenerationCampaignRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignDetailDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignPreviewRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineRegenerationCampaignSummaryDTO;
import org.github.tess1o.geopulse.streaming.service.TimelineRegenerationCampaignService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/admin/timeline-regeneration-campaigns")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
@Tag(name = "Admin: Timeline Regeneration", description = "Preview, create, inspect, and retry timeline regeneration campaigns.")
public class AdminTimelineRegenerationCampaignResource {

    @Context
    HttpServerRequest httpRequest;

    @Inject
    TimelineRegenerationCampaignService campaignService;

    @Inject
    AuditLogService auditLogService;

    @Inject
    CurrentUserService currentUserService;

    @POST
    @Path("/preview")
    public Response previewCampaign(TimelineRegenerationCampaignPreviewRequest request) {
        try {
            TimelineRegenerationCampaignPreviewDTO preview = campaignService.previewAdminCampaign(request);
            return Response.ok(preview).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to preview timeline regeneration campaign", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to preview timeline regeneration campaign"))
                    .build();
        }
    }

    @POST
    public Response createCampaign(CreateTimelineRegenerationCampaignRequest request) {
        UUID adminId = currentUserService.getCurrentUserId();

        try {
            TimelineRegenerationCampaignSummaryDTO created = campaignService.createAdminCampaign(request, adminId);
            auditLogService.logAction(
                    adminId,
                    ActionType.TIMELINE_REGENERATION_CAMPAIGN_CREATED,
                    TargetType.TIMELINE_REGENERATION_CAMPAIGN,
                    created.getId().toString(),
                    Map.of(
                            "campaignKey", created.getCampaignKey(),
                            "affectedFrom", created.getAffectedFrom().toString(),
                            "reason", created.getReason()
                    ),
                    UserIpAddress.resolve(httpRequest)
            );
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create timeline regeneration campaign", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to create timeline regeneration campaign"))
                    .build();
        }
    }

    @GET
    public Response listCampaigns() {
        List<TimelineRegenerationCampaignSummaryDTO> campaigns = campaignService.listCampaigns();
        return Response.ok(campaigns).build();
    }

    @GET
    @Path("/{campaignId}")
    public Response getCampaign(@PathParam("campaignId") UUID campaignId) {
        try {
            TimelineRegenerationCampaignDetailDTO details = campaignService.getCampaignDetails(campaignId);
            return Response.ok(details).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{campaignId}/retry-failed")
    public Response retryFailed(@PathParam("campaignId") UUID campaignId) {
        UUID adminId = currentUserService.getCurrentUserId();

        try {
            TimelineRegenerationCampaignSummaryDTO campaign = campaignService.retryFailedUsers(campaignId);
            auditLogService.logAction(
                    adminId,
                    ActionType.TIMELINE_REGENERATION_CAMPAIGN_RETRIED,
                    TargetType.TIMELINE_REGENERATION_CAMPAIGN,
                    campaignId.toString(),
                    Map.of("campaignKey", campaign.getCampaignKey()),
                    UserIpAddress.resolve(httpRequest)
            );
            return Response.ok(campaign).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to retry timeline regeneration campaign {}", campaignId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to retry timeline regeneration campaign"))
                    .build();
        }
    }
}
