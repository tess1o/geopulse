package org.github.tess1o.geopulse.admin.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.jboss.resteasy.reactive.RestResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_REGENERATION_CAMPAIGN_INVALID;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_REGENERATION_CAMPAIGN_NOT_FOUND;

@Path("/admin/timeline-regeneration-campaigns")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = ApiTags.ADMIN_TIMELINE_REGENERATION)
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
    @Operation(summary = "Preview a regeneration campaign",
            description = "Returns how many users have GPS data at or after `affectedFrom` and would be included in "
                    + "a regeneration campaign starting from that time.")
    public TimelineRegenerationCampaignPreviewDTO previewCampaign(TimelineRegenerationCampaignPreviewRequest request) {
        try {
            TimelineRegenerationCampaignPreviewDTO preview = campaignService.previewAdminCampaign(request);
            return preview;
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(TIMELINE_REGENERATION_CAMPAIGN_INVALID, TIMELINE_REGENERATION_CAMPAIGN_INVALID.title(), e);
        }
    }

    @POST
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Start a regeneration campaign",
            description = "Starts regenerating the timelines of all affected users from `affectedFrom`, for example "
                    + "after changing global timeline settings. Users are processed in the background. The campaign "
                    + "is recorded in the audit log.")
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
            throw new GeoPulseException(TIMELINE_REGENERATION_CAMPAIGN_INVALID, TIMELINE_REGENERATION_CAMPAIGN_INVALID.title(), e);
        }
    }

    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    @Operation(summary = "List regeneration campaigns",
            description = "Returns all timeline regeneration campaigns with their status and progress counts.")
    public List<TimelineRegenerationCampaignSummaryDTO> listCampaigns() {
        return campaignService.listCampaigns();
    }

    @GET
    @Path("/{campaignId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    @Operation(summary = "Get a regeneration campaign",
            description = "Returns a campaign with its progress and the users whose regeneration failed, with the "
                    + "errors.")
    public TimelineRegenerationCampaignDetailDTO getCampaign(
            @Parameter(description = "Campaign ID.")
            @PathParam("campaignId") UUID campaignId) {
        try {
            TimelineRegenerationCampaignDetailDTO details = campaignService.getCampaignDetails(campaignId);
            return details;
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(TIMELINE_REGENERATION_CAMPAIGN_NOT_FOUND, TIMELINE_REGENERATION_CAMPAIGN_NOT_FOUND.title(), e);
        }
    }

    @POST
    @Path("/{campaignId}/retry-failed")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Retry failed users",
            description = "Queues the users whose regeneration failed in a campaign again.")
    public TimelineRegenerationCampaignSummaryDTO retryFailed(
            @Parameter(description = "Campaign ID.")
            @PathParam("campaignId") UUID campaignId) {
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
            throw new GeoPulseException(TIMELINE_REGENERATION_CAMPAIGN_NOT_FOUND, TIMELINE_REGENERATION_CAMPAIGN_NOT_FOUND.title(), e);
        } catch (IllegalStateException e) {
            throw new GeoPulseException(TIMELINE_REGENERATION_CAMPAIGN_INVALID, TIMELINE_REGENERATION_CAMPAIGN_INVALID.title(), e);
        }
    }
}
