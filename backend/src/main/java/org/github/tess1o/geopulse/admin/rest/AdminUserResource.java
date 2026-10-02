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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

/**
 * REST resource for admin user management.
 */
@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Admin: Users", description = "Manage users, roles, status, passwords, and account deletion.")
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
    public PageResponse<UserListResponse> getUsers(
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("10") @Min(1) @Max(200) int size,
            @QueryParam("sortBy") @DefaultValue("createdAt") String sortBy,
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
    public UserDetailsResponse getUserById(@PathParam("id") UUID id) {
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
    public void updateUserStatus(@PathParam("id") UUID id, UpdateUserStatusRequest request) {

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
    public void updateUserRole(@PathParam("id") UUID id, UpdateUserRoleRequest request) {

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
    public ResetPasswordResponse resetPassword(@PathParam("id") UUID id) {

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
    public void deleteUser(@PathParam("id") UUID id) {

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
