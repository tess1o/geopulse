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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.jboss.resteasy.reactive.RestResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

/**
 * REST resource for user management.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = ApiTags.PROFILE)
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
    @Tag(name = ApiTags.REGISTRATION)
    @Path("/registrations")
    @Operation(summary = "Register an account",
            description = "Creates an account with email and password. Only available when password registration is "
                    + "enabled by the administrator; otherwise use an invitation link. The new user signs in "
                    + "afterwards.")
    public RestResponse<UserResponse> registerUser(@Valid UserRegistrationRequest request) {
        try {
            UserEntity user = userService.registerUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getTimezone(),
                    request.getLanguage()
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
    @Operation(summary = "Update your profile",
            description = "Updates the signed-in user's full name, avatar choice, timezone, and UI preferences such "
                    + "as the distance unit. The timezone is used for daily statistics, digests, and other "
                    + "day-based views.")
    public UserResponse updateProfile(@Valid UpdateProfileRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Updating user profile");
        return userMapper.toResponse(userService.updateProfile(userId, request));
    }

    @PUT
    @Path("/users/me/avatar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Upload an avatar image",
            description = "Uploads a custom avatar image (multipart field `file`) for the signed-in user and returns "
                    + "its path.")
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
    @Operation(summary = "Get a user's avatar",
            description = "Returns the custom avatar image of a user. Supports `If-None-Match` with the returned "
                    + "`ETag`, answering `304 Not Modified` when the image has not changed.")
    public Response getUserAvatar(
            @Parameter(description = "User ID.")
            @PathParam("userId") UUID userId,
            @Parameter(description = "ETag from a previous response, to receive `304 Not Modified` when the avatar "
                    + "is unchanged.")
            @HeaderParam("If-None-Match") String ifNoneMatch) {
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
    @Operation(summary = "Change your password",
            description = "Changes the signed-in user's password. The current password is required when one is set; "
                    + "accounts created through OIDC can set a first password this way.")
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
    @Operation(summary = "Update timeline settings",
            description = "Changes the settings that control how stays, trips, and data gaps are detected. Changes "
                    + "that affect detection start a timeline regeneration and return its job ID; enabling boat "
                    + "detection starts the boat setup job instead. Changes that only affect movement-type "
                    + "classification reclassify existing trips in the background and return `204 No Content`.")
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
    @Operation(summary = "Reset timeline settings",
            description = "Removes the user's own timeline settings so the server defaults apply again, and starts a "
                    + "timeline regeneration when that changes anything.")
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
     * @param patch the preferences to change; null fields are left unchanged, empty strings reset to default
     * @return the effective settings after the update
     */
    @PUT
    @RolesAllowed({"USER", "ADMIN"})
    @Path("/preferences/timeline-display")
    @APIResponseSchema(value = TimelineDisplaySettings.class, responseCode = "200",
            responseDescription = "Updated timeline display settings")
    @Operation(summary = "Update timeline display settings",
            description = "Changes how the timeline is shown, such as path styles and map options. Does not "
                    + "regenerate the timeline. Fields that are not sent stay unchanged; empty strings reset a "
                    + "field to its default.")
    public Response updateTimelineDisplayPreferences(@Valid TimelineDisplayPreferences patch) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Updating timeline display preferences for user {}", userId);

        try {
            userService.updateTimelineDisplayPreferences(userId, patch);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_TIMELINE_PREFERENCES, INVALID_TIMELINE_PREFERENCES.title(), e);
        }

        return Response.ok(userService.getTimelineDisplaySettings(userId)).build();
    }

    /**
     * Get timeline display settings for the current user.
     *
     * @return the user's preferences with defaults applied, plus server capabilities
     */
    @GET
    @RolesAllowed({"USER", "ADMIN"})
    @Path("/preferences/timeline-display")
    @Operation(summary = "Get timeline display settings",
            description = "Returns the signed-in user's timeline display settings with defaults applied, and related "
                    + "server capabilities.")
    public TimelineDisplaySettings getTimelineDisplayPreferences() {
        UUID userId = currentUserService.getCurrentUserId();
        log.debug("Getting timeline display preferences for user {}", userId);
        return userService.getTimelineDisplaySettings(userId);
    }

    /**
     * Get current user profile information.
     *
     * @return The current user's profile data
     */
    @GET
    @Path("/users/me")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Get your profile",
            description = "Returns the signed-in user: ID, email, name, role, timezone, avatar, and preferences. "
                    + "Useful for checking which user an API token belongs to.")
    public UserResponse getCurrentUserProfile() {
        return userMapper.toResponse(currentUserService.getCurrentUser());
    }
}
