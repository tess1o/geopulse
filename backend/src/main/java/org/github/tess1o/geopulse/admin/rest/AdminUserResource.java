package org.github.tess1o.geopulse.admin.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.github.tess1o.geopulse.admin.deletion.UserDeletionResponse;
import org.github.tess1o.geopulse.admin.deletion.UserDeletionService;
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.jboss.resteasy.reactive.RestResponse;
import org.github.tess1o.geopulse.admin.service.AdminUserService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

/**
 * REST resource for admin user management.
 */
@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.ADMIN_USERS)
public class AdminUserResource {

    @Context
    HttpServerRequest httpRequest;

    @Inject
    AdminUserService adminUserService;

    @Inject
    AuditLogService auditLogService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    UserDeletionService userDeletionService;

    /**
     * Get paginated list of users.
     */
    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    @Operation(summary = "List users",
            description = "Returns user accounts one page at a time, with role, status, number of GPS points, and "
                    + "linked OIDC providers.")
    public PageResponse<UserListResponse> getUsers(
            @Parameter(description = "Text to search for in email or full name.")
            @QueryParam("search") String search,
            @Parameter(description = "Page number, starting at 0.")
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Page size, from 1 to 200. Defaults to 10.")
            @QueryParam("size") @DefaultValue("10") @Min(1) @Max(200) int size,
            @Parameter(description = "Sort field, such as `createdAt` (default), `email`, `fullName`, `role`, or "
                    + "`active`.")
            @QueryParam("sortBy") @DefaultValue("createdAt") String sortBy,
            @Parameter(description = "Sort direction: `asc` or `desc`. Defaults to `desc`.")
            @QueryParam("sortDirection") @DefaultValue("desc") String sortDir) {

        List<UserEntity> users = adminUserService.getUsers(search, page, size, sortBy, sortDir);
        long total = adminUserService.countUsers(search);

        Map<UUID, UserDeletionResponse> deletions = userDeletionService.findPending(
                users.stream().map(UserEntity::getId).toList());
        List<UserListResponse> userResponses = users.stream()
                .map(user -> toUserListResponse(user, deletions.get(user.getId())))
                .collect(Collectors.toList());

        return new PageResponse<>(userResponses, page, size, total, (int) Math.ceil((double) total / size));
    }

    /**
     * Get user details by ID.
     */
    @GET
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    @Operation(summary = "Get a user",
            description = "Returns a user's account details, including timezone, last GPS point time, linked OIDC "
                    + "providers, and whether a password is set.")
    public UserDetailsResponse getUserById(
            @Parameter(description = "User ID.")
            @PathParam("id") UUID id) {
        return adminUserService.getUserById(id)
                .map(this::toUserDetailsResponse)
                .orElseThrow(() -> new GeoPulseException(ADMIN_USER_NOT_FOUND, "User not found"));
    }

    /**
     * Update user status (enable/disable).
     */
    @PUT
    @Path("/{id}/status")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Enable or disable a user",
            description = "Enables or disables a user account. Disabled users cannot sign in. Administrators cannot "
                    + "disable their own account. The change is recorded in the audit log.")
    public void updateUserStatus(
            @Parameter(description = "User ID.")
            @PathParam("id") UUID id, UpdateUserStatusRequest request) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Prevent admin from disabling themselves
        if (id.equals(adminId) && !request.isActive()) {
            throw new GeoPulseException(ADMIN_SELF_DISABLE_FORBIDDEN, "Cannot disable your own account");
        }

        adminUserService.setUserStatus(id, request.isActive());

        // Audit log
        String ipAddress = UserIpAddress.resolve(httpRequest);
        auditLogService.logUserStatusChange(adminId, id, request.isActive(), ipAddress);

    }

    /**
     * Update user role.
     */
    @PUT
    @Path("/{id}/role")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Change a user's role",
            description = "Sets a user's role to `USER` or `ADMIN`. The last administrator cannot be demoted. The "
                    + "change is recorded in the audit log.")
    public void updateUserRole(
            @Parameter(description = "User ID.")
            @PathParam("id") UUID id, UpdateUserRoleRequest request) {

        UUID adminId = currentUserService.getCurrentUserId();

        UserEntity user = adminUserService.getUserById(id)
                .orElseThrow(() -> new GeoPulseException(ADMIN_USER_NOT_FOUND, "User not found"));

        String oldRole = user.getRole().name();

        try {
            adminUserService.changeUserRole(id, request.getRole());
        } catch (IllegalStateException e) {
            throw new GeoPulseException(ADMIN_USER_UPDATE_INVALID, ADMIN_USER_UPDATE_INVALID.title(), e);
        }

        // Audit log
        String ipAddress = UserIpAddress.resolve(httpRequest);
        auditLogService.logUserRoleChange(adminId, id, oldRole, request.getRole().name(), ipAddress);

    }

    /**
     * Reset user password.
     */
    @POST
    @Path("/{id}/password-resets")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Reset a user's password",
            description = "Replaces a user's password with a generated temporary password and returns it once. Give "
                    + "it to the user so they can sign in and change it. The reset is recorded in the audit log.")
    public ResetPasswordResponse resetPassword(
            @Parameter(description = "User ID.")
            @PathParam("id") UUID id) {

        UUID adminId = currentUserService.getCurrentUserId();

        String tempPassword = adminUserService.resetPassword(id);

        // Audit log
        String ipAddress = UserIpAddress.resolve(httpRequest);
        auditLogService.logPasswordReset(adminId, id, ipAddress);

        return ResetPasswordResponse.builder()
                .temporaryPassword(tempPassword)
                .build();
    }

    /**
     * Start deleting a user and all associated data.
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed(SecurityRoles.ADMIN)
    @APIResponse(responseCode = "202", description = "Deletion started")
    @Operation(summary = "Delete a user",
            description = "Starts permanently deleting a user and all of their data, and returns at once. The user is "
                    + "locked out immediately: sessions, API tokens, GPS sources, shares and friendships are removed. "
                    + "The data is then deleted in the background; follow it with `GET /admin/users/{id}/deletion` "
                    + "or the `deletion` field of the user. Repeating the request returns the deletion in progress. "
                    + "Administrators cannot delete their own account or the last active administrator. This cannot "
                    + "be undone. The request is recorded in the audit log.")
    public RestResponse<UserDeletionResponse> deleteUser(
            @Parameter(description = "User ID.")
            @PathParam("id") UUID id) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Prevent admin from deleting themselves
        if (id.equals(adminId)) {
            throw new GeoPulseException(ADMIN_SELF_DELETE_FORBIDDEN, "Cannot delete your own account");
        }

        UserEntity user = adminUserService.getUserById(id)
                .orElseThrow(() -> new GeoPulseException(ADMIN_USER_NOT_FOUND, "User not found"));
        boolean alreadyPending = userDeletionService.isPending(id);

        UserDeletionResponse deletion = userDeletionService.requestDeletion(id, adminId);

        if (!alreadyPending) {
            auditLogService.logAction(adminId, ActionType.USER_DELETION_REQUESTED, TargetType.USER, id.toString(),
                    Map.of("email", user.getEmail()), UserIpAddress.resolve(httpRequest));
        }
        return RestResponse.status(Response.Status.ACCEPTED, deletion);
    }

    /**
     * Progress of a user deletion.
     */
    @GET
    @Path("/{id}/deletion")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    @Operation(summary = "Get a user deletion",
            description = "Returns the progress of a user's deletion. It stays available after the user is gone, "
                    + "with status `COMPLETED`.")
    public UserDeletionResponse getUserDeletion(
            @Parameter(description = "User ID.")
            @PathParam("id") UUID id) {
        return userDeletionService.find(id)
                .orElseThrow(() -> new GeoPulseException(USER_DELETION_NOT_FOUND, "User deletion not found"));
    }

    /**
     * Retry a failed user deletion.
     */
    @POST
    @Path("/{id}/deletion/retries")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Retry a user deletion",
            description = "Starts a failed user deletion again where it stopped. The user stays locked out while the "
                    + "deletion is pending, including after it failed.")
    public UserDeletionResponse retryUserDeletion(
            @Parameter(description = "User ID.")
            @PathParam("id") UUID id) {
        return userDeletionService.retry(id);
    }

    private UserListResponse toUserListResponse(UserEntity user, UserDeletionResponse deletion) {
        return UserListResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                // Counting the points of a user being deleted is slow and the count is about to be zero.
                .gpsPointsCount(deletion == null ? adminUserService.getGpsPointsCount(user.getId()) : null)
                .linkedOidcProviders(adminUserService.getLinkedOidcProviders(user.getId()))
                .deletion(deletion)
                .build();
    }

    private UserDetailsResponse toUserDetailsResponse(UserEntity user) {
        UserDeletionResponse deletion = userDeletionService.findPending(List.of(user.getId())).get(user.getId());
        return UserDetailsResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .emailVerified(user.isEmailVerified())
                .avatar(user.getAvatar())
                .timezone(user.getTimezone())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .gpsPointsCount(deletion == null ? adminUserService.getGpsPointsCount(user.getId()) : null)
                .lastGpsPointAt(deletion == null ? adminUserService.getLastGpsPointTimestamp(user.getId()) : null)
                .linkedOidcProviders(adminUserService.getLinkedOidcProviders(user.getId()))
                .deletion(deletion)
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isBlank())
                .build();
    }
}
