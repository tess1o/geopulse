package org.github.tess1o.geopulse.notifications.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.notifications.model.dto.UnreadCountDto;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationDto;
import org.github.tess1o.geopulse.notifications.service.UserNotificationService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/notifications")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class NotificationResource {

    private final UserNotificationService notificationService;
    private final CurrentUserService currentUserService;

    @Inject
    public NotificationResource(UserNotificationService notificationService,
                                CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GET
    public Response getNotifications(@QueryParam("limit") @DefaultValue("50") int limit) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<UserNotificationDto> notifications = notificationService.listNotifications(userId, limit);
            return Response.ok(ApiResponse.success(notifications)).build();
        } catch (Exception e) {
            log.error("Failed to load notifications", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load notifications"))
                    .build();
        }
    }

    @GET
    @Path("/unread-count")
    public Response getUnreadCount() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            UnreadCountDto unreadCount = notificationService.getUnreadCount(userId);
            return Response.ok(ApiResponse.success(unreadCount)).build();
        } catch (Exception e) {
            log.error("Failed to load unread notification count", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load unread notification count"))
                    .build();
        }
    }

    @POST
    @Path("/{notificationId}/seen")
    public Response markSeen(@PathParam("notificationId") Long notificationId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            UserNotificationDto updated = notificationService.markSeen(userId, notificationId);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to mark notification {} as seen", notificationId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to mark notification as seen"))
                    .build();
        }
    }

    @POST
    @Path("/seen-all")
    public Response markAllSeen() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            long updatedCount = notificationService.markAllSeen(userId);
            return Response.ok(ApiResponse.success(Map.of("updatedCount", updatedCount))).build();
        } catch (Exception e) {
            log.error("Failed to mark notifications as seen", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to mark notifications as seen"))
                    .build();
        }
    }
}
