package org.github.tess1o.geopulse.user.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.user.mapper.UserMapper;
import org.github.tess1o.geopulse.user.model.*;
import org.github.tess1o.geopulse.user.service.UserService;

import java.util.UUID;

/**
 * REST resource for user management.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
public class UserResource {

    private final UserService userService;
    private final UserMapper userMapper;
    private final CurrentUserService currentUserService;

    @Inject
    public UserResource(UserService userService, UserMapper userMapper, CurrentUserService currentUserService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Register a new user.
     *
     * @param request The user registration request
     * @return The created user
     */

    @POST
    @Path("/register")
    public Response registerUser(@Valid UserRegistrationRequest request) {
        try {
            UserEntity user = userService.registerUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName()
            );
            UserResponse response = userMapper.toResponse(user);
            return Response.status(Response.Status.CREATED).entity(ApiResponse.success(response)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to register user"))
                    .build();
        }
    }


    @POST
    @Path("/update")
    @RolesAllowed("USER")
    public Response updateProfile(@Valid UpdateProfileRequest request) {
        try {
            if (request.getUserId() == null) {
                request.setUserId(currentUserService.getCurrentUserId());
            }
            if (!request.getUserId().equals(currentUserService.getCurrentUserId())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("You can modify only your profile"))
                        .build();
            }
            log.info("Updating profile with {}", request);
            userService.updateProfile(request);
            return Response.status(Response.Status.CREATED).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update user profile"))
                    .build();
        }
    }

    @POST
    @Path("/changePassword")
    @RolesAllowed("USER")
    public Response changePassword(@Valid UpdateUserPasswordRequest request) {
        try {
            if (request.getUserId() == null) {
                request.setUserId(currentUserService.getCurrentUserId());
            }
            if (!request.getUserId().equals(currentUserService.getCurrentUserId())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("You can modify only your profile"))
                        .build();
            }
            userService.changePassword(request);
            return Response.status(Response.Status.CREATED).build();
        } catch (InvalidPasswordException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Invalid password"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to change the password"))
                    .build();
        }
    }

    @PUT
    @RolesAllowed("USER")
    @Path("/preferences/timeline")
    public Response updateTimelinePreferences(@Valid UpdateTimelinePreferencesRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Updating timeline preferences for user {}", userId);
        log.debug("Timeline preferences: {}", request);
        userService.updateTimelinePreferences(userId, request);
        return Response.noContent().build();
    }

    @DELETE
    @RolesAllowed("USER")
    @Path("/preferences/timeline")
    public Response resetPreferencesToDefaults() {
        UUID userId = currentUserService.getCurrentUserId();
        userService.resetTimelinePreferencesToDefaults(userId);
        return Response.noContent().build();
    }
}
