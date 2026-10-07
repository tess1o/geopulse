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
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.service.AdminUserService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
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

        List<UserListResponse> userResponses = users.stream()
                .map(this::toUserListResponse)
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
     * Delete user and all associated data.
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Delete a user",
            description = "Permanently deletes a user and all of their data. Administrators cannot delete their own "
                    + "account or the last administrator. This cannot be undone. The deletion is recorded in the "
                    + "audit log.")
    public void deleteUser(
            @Parameter(description = "User ID.")
            @PathParam("id") UUID id) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Prevent admin from deleting themselves
        if (id.equals(adminId)) {
            throw new GeoPulseException(ADMIN_SELF_DELETE_FORBIDDEN, "Cannot delete your own account");
        }

        UserEntity user = adminUserService.getUserById(id)
                .orElseThrow(() -> new GeoPulseException(ADMIN_USER_NOT_FOUND, "User not found"));

        String userEmail = user.getEmail();

        try {
            adminUserService.deleteUser(id);
        } catch (IllegalStateException e) {
            throw new GeoPulseException(ADMIN_USER_UPDATE_INVALID, ADMIN_USER_UPDATE_INVALID.title(), e);
        }

        // Audit log
        String ipAddress = UserIpAddress.resolve(httpRequest);
        auditLogService.logUserDeleted(adminId, id, userEmail, ipAddress);

    }

    private UserListResponse toUserListResponse(UserEntity user) {
        return UserListResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .gpsPointsCount(adminUserService.getGpsPointsCount(user.getId()))
                .linkedOidcProviders(adminUserService.getLinkedOidcProviders(user.getId()))
                .build();
    }

    private UserDetailsResponse toUserDetailsResponse(UserEntity user) {
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
                .gpsPointsCount(adminUserService.getGpsPointsCount(user.getId()))
                .lastGpsPointAt(adminUserService.getLastGpsPointTimestamp(user.getId()))
                .linkedOidcProviders(adminUserService.getLinkedOidcProviders(user.getId()))
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isBlank())
                .build();
    }
}
