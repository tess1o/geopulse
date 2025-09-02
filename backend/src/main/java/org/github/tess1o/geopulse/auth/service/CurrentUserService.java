package org.github.tess1o.geopulse.auth.service;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import java.util.Optional;
import java.util.UUID;

@RequestScoped
public class CurrentUserService {

    @Inject
    JsonWebToken jwt;

    @Inject
    UserService userService;

    /**
     * Get the current user's ID from JWT token
     */
    public UUID getCurrentUserId() {
        if (jwt == null) {
            throw new SecurityException("No JWT token found");
        }

        // Try userId claim first (recommended)
        String userIdStr = jwt.getClaim("userId");
        if (userIdStr != null) {
            try {
                return UUID.fromString(userIdStr);
            } catch (IllegalArgumentException e) {
                throw new SecurityException("Invalid userId in token", e);
            }
        }

        // Fallback to subject claim
        String subject = jwt.getSubject();
        if (subject != null) {
            try {
                return UUID.fromString(subject);
            } catch (IllegalArgumentException e) {
                throw new SecurityException("Invalid subject in token", e);
            }
        }

        throw new SecurityException("No user ID found in token");
    }

    /**
     * Get the current user's email from JWT token
     */
    public String getCurrentUserEmail() {
        if (jwt == null) {
            throw new SecurityException("No JWT token found");
        }

        String email = jwt.getClaim("upn");
        if (email == null) {
            throw new SecurityException("No email found in token");
        }

        return email;
    }

    /**
     * Get the current user entity (with database lookup)
     */
    public UserEntity getCurrentUser() {
        UUID userId = getCurrentUserId();
        Optional<UserEntity> user = userService.findById(userId);

        if (user.isEmpty()) {
            throw new SecurityException("User not found");
        }

        return user.get();
    }
}