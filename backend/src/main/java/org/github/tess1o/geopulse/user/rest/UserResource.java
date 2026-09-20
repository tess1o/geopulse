package org.github.tess1o.geopulse.user.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.streaming.service.boat.BoatSetupService;
import org.github.tess1o.geopulse.user.mapper.UserMapper;
import org.github.tess1o.geopulse.user.model.*;
import org.github.tess1o.geopulse.user.service.UserService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

/**
 * REST resource for user management.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = "User: Users", description = "Register users and manage the authenticated user profile, avatar, password, and preferences.")
public class UserResource {

    private final UserService userService;
    private final UserMapper userMapper;
    private final CurrentUserService currentUserService;
    private final BoatSetupService boatSetupService;

    @Inject
    public UserResource(UserService userService,
                        UserMapper userMapper,
                        CurrentUserService currentUserService,
                        BoatSetupService boatSetupService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.currentUserService = currentUserService;
        this.boatSetupService = boatSetupService;
    }

    /**
     * Register a new user.
     *
     * @param request The user registration request
     * @return The created user
     */

    @POST
    @Path("/registrations")
    public RestResponse<UserResponse> registerUser(@Valid UserRegistrationRequest request) {
        try {
            UserEntity user = userService.registerUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getTimezone()
            );
            UserResponse response = userMapper.toResponse(user);
            return RestResponse.status(Response.Status.CREATED, response);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(USER_REGISTRATION_CONFLICT, USER_REGISTRATION_CONFLICT.title(), e);
        }
    }


    @PATCH
    @Path("/users/me")
    @RolesAllowed({"USER", "ADMIN"})
    public UserResponse updateProfile(@Valid UpdateProfileRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Updating user profile");
        return userMapper.toResponse(userService.updateProfile(userId, request));
    }

    @PUT
    @Path("/users/me/avatar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"USER", "ADMIN"})
    public AvatarResponse uploadAvatar(@RestForm("file") FileUpload file) {
        if (file == null || file.uploadedFile() == null || file.size() == 0) {
            throw new GeoPulseException(INVALID_AVATAR, "No avatar file uploaded");
        }
        String contentType = file.contentType();
        if (contentType == null || contentType.isBlank()) {
            throw new GeoPulseException(INVALID_AVATAR, "Avatar content type is missing");
        }
        try {
            UUID userId = currentUserService.getCurrentUserId();
            byte[] imageBytes = Files.readAllBytes(file.uploadedFile());
            String avatarPath = userService.upsertCustomAvatar(userId, imageBytes, contentType);
            return new AvatarResponse(avatarPath);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_AVATAR, INVALID_AVATAR.title(), e);
        } catch (IOException e) {
            throw new GeoPulseException(INTERNAL_ERROR, "Failed to read uploaded avatar", e);
        }
    }

    @GET
    @Path("/users/{userId}/avatar")
    @Produces({"image/jpeg", "image/png", "image/webp"})
    @RolesAllowed({"USER", "ADMIN"})
    @APIResponse(responseCode = "200", description = "User avatar",
            content = {
                    @Content(mediaType = "image/jpeg", schema = @Schema(type = SchemaType.STRING, format = "binary")),
                    @Content(mediaType = "image/png", schema = @Schema(type = SchemaType.STRING, format = "binary")),
                    @Content(mediaType = "image/webp", schema = @Schema(type = SchemaType.STRING, format = "binary"))
            })
    public Response getUserAvatar(@PathParam("userId") UUID userId, @HeaderParam("If-None-Match") String ifNoneMatch) {
        Optional<UserAvatarEntity> avatarOpt = userService.findUserAvatar(userId);
        if (avatarOpt.isEmpty()) {
            throw new GeoPulseException(NOT_FOUND, "Avatar not found");
        }

        UserAvatarEntity avatar = avatarOpt.get();
        String etag = "\"" + avatar.getUpdatedAt().toEpochMilli() + "-" + avatar.getSizeBytes() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return Response.notModified()
                    .header("ETag", etag)
                    .header("Cache-Control", "private, no-cache, max-age=0")
                    .build();
        }

        return Response.ok(avatar.getImageData(), avatar.getContentType())
                .header("ETag", etag)
                .header("Cache-Control", "private, no-cache, max-age=0")
                .build();
    }

    @PUT
    @Path("/users/me/password")
    @RolesAllowed({"USER", "ADMIN"})
    public PasswordStatusResponse changePassword(@Valid UpdateUserPasswordRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            userService.changePassword(userId, request);
            return new PasswordStatusResponse(true);
        } catch (InvalidPasswordException e) {
            throw new GeoPulseException(INVALID_PASSWORD, "Invalid password", e);
        }
    }

    @PUT
    @RolesAllowed({"USER", "ADMIN"})
    @Path("/preferences/timeline")
    @APIResponseSchema(value = TimelinePreferencesUpdateResponse.class, responseCode = "200",
            responseDescription = "Timeline regeneration or boat setup started")
    @APIResponse(responseCode = "204", description = "Preferences updated without starting a job")
    public Response updateTimelinePreferences(@Valid UpdateTimelinePreferencesRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Updating timeline preferences for user {}", userId);
        log.debug("Updating timeline preferences");

        // Update preferences within transaction
        String changeType = userService.updateTimelinePreferences(userId, request);

        // Create async job AFTER transaction commits (if needed)
        if ("structural".equals(changeType)) {
            UUID jobId = userService.createTimelineRegenerationJob(userId);
            if (jobId != null) {
                return Response.ok(new TimelinePreferencesUpdateResponse(jobId, null, null)).build();
            }
        }
        if ("boat-setup".equals(changeType)) {
            var setup = boatSetupService.startSetup(userId);
            return Response.ok(new TimelinePreferencesUpdateResponse(
                    null, setup.jobId(), setup.status())).build();
        }

        // No job created (classification-only or no changes)
        return Response.noContent().build();
    }

    @DELETE
    @RolesAllowed({"USER", "ADMIN"})
    @Path("/preferences/timeline")
    @APIResponseSchema(value = TimelinePreferencesUpdateResponse.class, responseCode = "200",
            responseDescription = "Timeline regeneration started")
    @APIResponse(responseCode = "204", description = "Preferences reset without starting a job")
    public Response resetPreferencesToDefaults() {
        UUID userId = currentUserService.getCurrentUserId();

        // Reset preferences within transaction
        boolean needsRegeneration = userService.resetTimelinePreferencesToDefaults(userId);

        // Create async job AFTER transaction commits
        if (needsRegeneration) {
            UUID jobId = userService.createTimelineRegenerationJob(userId);
            if (jobId != null) {
                return Response.ok(new TimelinePreferencesUpdateResponse(jobId, null, null)).build();
            }
        }

        // No job created
        return Response.noContent().build();
    }

    /**
     * Update timeline display preferences.
     * These settings affect ONLY how timelines are rendered in the UI.
     * Changing these settings does NOT trigger timeline regeneration.
     *
     * @param request the display preferences update request
     * @return 204 No Content on success
     */
    @PUT
    @RolesAllowed({"USER", "ADMIN"})
    @Path("/preferences/timeline-display")
    @APIResponseSchema(value = TimelineDisplayPreferences.class, responseCode = "200",
            responseDescription = "Updated timeline display preferences")
    public Response updateTimelineDisplayPreferences(@Valid UpdateTimelineDisplayPreferencesRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Updating timeline display preferences for user {}", userId);
        log.debug("Updating timeline display preferences");

        try {
            userService.updateTimelineDisplayPreferences(userId, request);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_TIMELINE_PREFERENCES, INVALID_TIMELINE_PREFERENCES.title(), e);
        }

        TimelineDisplayPreferences updatedPreferences = userService.getTimelineDisplayPreferences(userId);
        return Response.ok(updatedPreferences).build();
    }

    /**
     * Get timeline display preferences for the current user.
     *
     * @return the user's timeline display preferences
     */
    @GET
    @RolesAllowed({"USER", "ADMIN"})
    @Path("/preferences/timeline-display")
    public TimelineDisplayPreferences getTimelineDisplayPreferences() {
        UUID userId = currentUserService.getCurrentUserId();
        log.debug("Getting timeline display preferences for user {}", userId);

        TimelineDisplayPreferences preferences = userService.getTimelineDisplayPreferences(userId);
        return preferences;
    }

    /**
     * Get current user profile information.
     *
     * @return The current user's profile data
     */
    @GET
    @Path("/users/me")
    @RolesAllowed({"USER", "ADMIN"})
    public UserResponse getCurrentUserProfile() {
        return userMapper.toResponse(currentUserService.getCurrentUser());
    }
}
