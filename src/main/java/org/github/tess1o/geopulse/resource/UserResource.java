package org.github.tess1o.geopulse.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.mapper.UserMapper;
import org.github.tess1o.geopulse.model.dto.ApiResponse;
import org.github.tess1o.geopulse.model.dto.UserRegistrationRequest;
import org.github.tess1o.geopulse.model.dto.UserResponse;
import org.github.tess1o.geopulse.model.entity.UserEntity;
import org.github.tess1o.geopulse.service.UserService;

import java.util.Optional;

/**
 * REST resource for user management.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private final UserService userService;
    private final UserMapper userMapper;

    @Inject
    public UserResource(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    /**
     * Register a new user.
     * This endpoint is exempt from authentication.
     *
     * @param request The user registration request
     * @return The created user
     */
    @POST
    @Path("/register")
    @PermitAll
    public Response registerUser(@Valid UserRegistrationRequest request) {
        try {
            UserEntity user = userService.registerUser(
                request.getUserId(),
                request.getPassword(),
                request.getDeviceId()
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

    /**
     * Get the current authenticated user.
     * This endpoint requires authentication.
     *
     * @return The authenticated user
     */
    @GET
    @Path("/me")
    public Response getCurrentUser(@jakarta.ws.rs.core.Context ContainerRequestContext requestContext) {
        // Get the authenticated user ID from the request context
        String userId = (String) requestContext.getProperty("userId");

        Optional<UserEntity> userOpt = userService.getUserById(userId);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiResponse.error("User not found"))
                .build();
        }

        UserResponse response = userMapper.toResponse(userOpt.get());
        return Response.ok(ApiResponse.success(response)).build();
    }
}
