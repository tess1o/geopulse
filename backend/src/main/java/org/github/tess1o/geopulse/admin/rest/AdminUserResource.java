package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.service.AdminUserService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for admin user management.
 */
@Path("/api/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
public class AdminUserResource {

    @Inject
    AdminUserService adminUserService;

    @Inject
    AuditLogService auditLogService;

    @Inject
    JsonWebToken jwt;

    @Context
    SecurityContext securityContext;

    /**
     * Get paginated list of users.
     */
    @GET
    public Response getUsers(
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("sortBy") @DefaultValue("createdAt") String sortBy,
            @QueryParam("sortDir") @DefaultValue("desc") String sortDir) {

        List<UserEntity> users = adminUserService.getUsers(search, page, size, sortBy, sortDir);
        long total = adminUserService.countUsers(search);

        List<UserListResponse> userResponses = users.stream()
                .map(this::toUserListResponse)
                .collect(Collectors.toList());

        PagedResponse<UserListResponse> response = PagedResponse.<UserListResponse>builder()
                .content(userResponses)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .page(page)
                .size(size)
                .build();

        return Response.ok(response).build();
    }

    /**
     * Get user details by ID.
     */
    @GET
    @Path("/{id}")
    public Response getUserById(@PathParam("id") UUID id) {
        return adminUserService.getUserById(id)
                .map(this::toUserDetailsResponse)
                .map(Response::ok)
                .orElse(Response.status(Response.Status.NOT_FOUND))
                .build();
    }

    /**
     * Update user status (enable/disable).
     */
    @PUT
    @Path("/{id}/status")
    public Response updateUserStatus(
            @PathParam("id") UUID id,
            UpdateUserStatusRequest request,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = UUID.fromString(jwt.getSubject());

        // Prevent admin from disabling themselves
        if (id.equals(adminId) && !request.isActive()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Cannot disable your own account"))
                    .build();
        }

        adminUserService.setUserStatus(id, request.isActive());

        // Audit log
        String ipAddress = forwardedFor != null ? forwardedFor : realIp;
        auditLogService.logUserStatusChange(adminId, id, request.isActive(), ipAddress);

        return Response.ok(Map.of("success", true)).build();
    }

    /**
     * Update user role.
     */
    @PUT
    @Path("/{id}/role")
    public Response updateUserRole(
            @PathParam("id") UUID id,
            UpdateUserRoleRequest request,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = UUID.fromString(jwt.getSubject());

        UserEntity user = adminUserService.getUserById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String oldRole = user.getRole().name();

        try {
            adminUserService.changeUserRole(id, request.getRole());
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }

        // Audit log
        String ipAddress = forwardedFor != null ? forwardedFor : realIp;
        auditLogService.logUserRoleChange(adminId, id, oldRole, request.getRole().name(), ipAddress);

        return Response.ok(Map.of("success", true)).build();
    }

    /**
     * Reset user password.
     */
    @POST
    @Path("/{id}/reset-password")
    public Response resetPassword(
            @PathParam("id") UUID id,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = UUID.fromString(jwt.getSubject());

        String tempPassword = adminUserService.resetPassword(id);

        // Audit log
        String ipAddress = forwardedFor != null ? forwardedFor : realIp;
        auditLogService.logPasswordReset(adminId, id, ipAddress);

        return Response.ok(ResetPasswordResponse.builder()
                .temporaryPassword(tempPassword)
                .build()).build();
    }

    /**
     * Delete user and all associated data.
     */
    @DELETE
    @Path("/{id}")
    public Response deleteUser(
            @PathParam("id") UUID id,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = UUID.fromString(jwt.getSubject());

        // Prevent admin from deleting themselves
        if (id.equals(adminId)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Cannot delete your own account"))
                    .build();
        }

        UserEntity user = adminUserService.getUserById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String userEmail = user.getEmail();

        try {
            adminUserService.deleteUser(id);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }

        // Audit log
        String ipAddress = forwardedFor != null ? forwardedFor : realIp;
        auditLogService.logUserDeleted(adminId, id, userEmail, ipAddress);

        return Response.ok(Map.of("success", true)).build();
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
                .linkedOidcProviders(adminUserService.getLinkedOidcProviders(user.getId()))
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isBlank())
                .build();
    }
}
