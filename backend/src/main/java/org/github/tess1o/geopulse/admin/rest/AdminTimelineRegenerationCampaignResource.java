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
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
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
import org.jboss.resteasy.reactive.RestResponse;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INTERNAL_ERROR;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_REGENERATION_CAMPAIGN_INVALID;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_REGENERATION_CAMPAIGN_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/admin/timeline-regeneration-campaigns")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
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
    @RolesAllowed(SecurityRoles.ADMIN)
    public TimelineRegenerationCampaignPreviewDTO previewCampaign(TimelineRegenerationCampaignPreviewRequest request) {
        try {
            TimelineRegenerationCampaignPreviewDTO preview = campaignService.previewAdminCampaign(request);
            return preview;
        } catch (IllegalArgumentException e) {
            throw problem(TIMELINE_REGENERATION_CAMPAIGN_INVALID, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to preview timeline regeneration campaign", e);
            throw problem(INTERNAL_ERROR, "Failed to preview timeline regeneration campaign");
        }
    }

    @POST
    @RolesAllowed(SecurityRoles.ADMIN)
    public RestResponse<TimelineRegenerationCampaignSummaryDTO> createCampaign(CreateTimelineRegenerationCampaignRequest request) {
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
            return RestResponse.status(Response.Status.CREATED, created);
        } catch (IllegalArgumentException e) {
            throw problem(TIMELINE_REGENERATION_CAMPAIGN_INVALID, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create timeline regeneration campaign", e);
            throw problem(INTERNAL_ERROR, "Failed to create timeline regeneration campaign");
        }
    }

    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public List<TimelineRegenerationCampaignSummaryDTO> listCampaigns() {
        return campaignService.listCampaigns();
    }

    @GET
    @Path("/{campaignId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public TimelineRegenerationCampaignDetailDTO getCampaign(@PathParam("campaignId") UUID campaignId) {
        try {
            TimelineRegenerationCampaignDetailDTO details = campaignService.getCampaignDetails(campaignId);
            return details;
        } catch (IllegalArgumentException e) {
            throw problem(TIMELINE_REGENERATION_CAMPAIGN_NOT_FOUND, e.getMessage());
        }
    }

    @POST
    @Path("/{campaignId}/retry-failed")
    @RolesAllowed(SecurityRoles.ADMIN)
    public TimelineRegenerationCampaignSummaryDTO retryFailed(@PathParam("campaignId") UUID campaignId) {
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
            return campaign;
        } catch (IllegalArgumentException e) {
            throw problem(TIMELINE_REGENERATION_CAMPAIGN_NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw problem(TIMELINE_REGENERATION_CAMPAIGN_INVALID, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to retry timeline regeneration campaign {}", campaignId, e);
            throw problem(INTERNAL_ERROR, "Failed to retry timeline regeneration campaign");
        }
    }
}
