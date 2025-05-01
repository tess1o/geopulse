package org.github.tess1o.geopulse.filter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.github.tess1o.geopulse.model.dto.ApiResponse;
import org.github.tess1o.geopulse.service.UserService;

import java.util.Base64;

/**
 * Filter for handling basic authentication.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final String REGISTER_PATH = "/api/users/register";
    private static final String AUTHENTICATION_SCHEME = "Basic";

    private final UserService userService;

    @Inject
    public AuthenticationFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Skip authentication for registration endpoint
        if (requestContext.getUriInfo().getPath().equals(REGISTER_PATH)) {
            return;
        }

        // Get the Authorization header
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

        // Validate the Authorization header
        if (authHeader == null || !authHeader.startsWith(AUTHENTICATION_SCHEME + " ")) {
            abortWithUnauthorized(requestContext, "Missing or invalid Authorization header");
            return;
        }

        // Extract the credentials
        String base64Credentials = authHeader.substring(AUTHENTICATION_SCHEME.length()).trim();
        String credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(base64Credentials));
        } catch (IllegalArgumentException e) {
            abortWithUnauthorized(requestContext, "Invalid credentials encoding");
            return;
        }

        // Split username and password
        final String[] values = credentials.split(":", 2);
        if (values.length != 2) {
            abortWithUnauthorized(requestContext, "Invalid credentials format");
            return;
        }

        String username = values[0];
        String password = values[1];

        // Authenticate the user
        if (!userService.authenticate(username, password)) {
            abortWithUnauthorized(requestContext, "Invalid credentials");
            return;
        }

        // If authentication is successful, set the user ID in the security context
        requestContext.setProperty("userId", username);
    }

    private void abortWithUnauthorized(ContainerRequestContext requestContext, String message) {
        System.out.println("Aborting request with unauthorized response: " + message);
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATION_SCHEME)
                .entity(ApiResponse.error(message))
                .build()
        );
    }
}