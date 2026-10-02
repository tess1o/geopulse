package org.github.tess1o.geopulse.notifications.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.notifications.model.dto.NotificationPreferencesDto;
import org.github.tess1o.geopulse.notifications.model.dto.ReleaseAnnouncementResponse;
import org.github.tess1o.geopulse.notifications.model.dto.UnreadCountDto;
import org.github.tess1o.geopulse.notifications.model.dto.UpdateNotificationPreferencesRequest;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationDto;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.service.NotificationPreferencesService;
import org.github.tess1o.geopulse.notifications.service.ReleaseAnnouncementService;
import org.github.tess1o.geopulse.notifications.service.UserNotificationService;
import org.github.tess1o.geopulse.shared.api.UpdatedCountResponse;

import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_NOTIFICATION_PREFERENCES;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_RELEASE_ANNOUNCEMENT;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NOTIFICATION_NOT_FOUND;

@Path("/notifications")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Notifications", description = "Read and update notifications and preferences.")
public class NotificationResource {

    private final UserNotificationService notificationService;
    private final CurrentUserService currentUserService;
    private final NotificationPreferencesService preferencesService;
    private final ReleaseAnnouncementService releaseAnnouncementService;

    @Inject
    public NotificationResource(UserNotificationService notificationService,
                                CurrentUserService currentUserService,
                                NotificationPreferencesService preferencesService,
                                ReleaseAnnouncementService releaseAnnouncementService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
        this.preferencesService = preferencesService;
        this.releaseAnnouncementService = releaseAnnouncementService;
    }

    @GET
    @Path("/preferences")
    public NotificationPreferencesDto getPreferences() {
        return preferencesService.get(currentUserService.getCurrentUserId());
    }

    @PUT
    @Path("/preferences")
    public NotificationPreferencesDto updatePreferences(@NotNull @Valid UpdateNotificationPreferencesRequest request) {
        try {
            return preferencesService.update(currentUserService.getCurrentUserId(), request);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_NOTIFICATION_PREFERENCES, INVALID_NOTIFICATION_PREFERENCES.title(), exception);
        }
    }

    @POST
    @Path("/release/current")
    public ReleaseAnnouncementResponse currentReleaseAnnouncement() {
        try {
            return releaseAnnouncementService.current(currentUserService.getCurrentUserId());
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_RELEASE_ANNOUNCEMENT, INVALID_RELEASE_ANNOUNCEMENT.title(), exception);
        }
    }

    @GET
    public PageResponse<UserNotificationDto> getNotifications(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("25") @Min(1) @Max(100) int pageSize,
            @QueryParam("seen") Boolean seen,
            @QueryParam("source") NotificationSource source,
            @QueryParam("type") NotificationType type) {
        return notificationService.listNotificationsPage(
                currentUserService.getCurrentUserId(), page, pageSize, seen, source, type);
    }

    @GET
    @Path("/unread-count")
    public UnreadCountDto getUnreadCount() {
        return notificationService.getUnreadCount(currentUserService.getCurrentUserId());
    }

    @PATCH
    @Path("/{notificationId}/read-status")
    public UserNotificationDto markSeen(@PathParam("notificationId") Long notificationId) {
        try {
            return notificationService.markSeen(currentUserService.getCurrentUserId(), notificationId);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(NOTIFICATION_NOT_FOUND, NOTIFICATION_NOT_FOUND.title(), exception);
        }
    }

    @PATCH
    @Path("/read-status")
    public UpdatedCountResponse markAllSeen() {
        UUID userId = currentUserService.getCurrentUserId();
        return new UpdatedCountResponse(notificationService.markAllSeen(userId));
    }
}
