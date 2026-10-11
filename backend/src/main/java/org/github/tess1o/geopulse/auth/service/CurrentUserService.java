package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import java.util.Optional;
import java.util.UUID;

@RequestScoped
public class CurrentUserService {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    UserService userService;

    /**
     * Get the current user's ID from the authenticated session or API token
     */
    public UUID getCurrentUserId() {
        String userId = attribute(IdentityAttributes.USER_ID);
        if (userId == null) {
            throw new SecurityException("No authenticated user");
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid user ID in security identity", e);
        }
    }

    /**
     * Get the current user's email from the authenticated session or API token
     */
    public String getCurrentUserEmail() {
        String email = attribute(IdentityAttributes.USER_EMAIL);
        if (email == null) {
            throw new SecurityException("No authenticated user");
        }
        return email;
    }

    /**
     * The session making this request, or empty for API-token and anonymous requests.
     */
    public Optional<UUID> getCurrentSessionId() {
        return Optional.ofNullable(attribute(IdentityAttributes.SESSION_ID)).map(UUID::fromString);
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

    private String attribute(String name) {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return null;
        }
        String value = securityIdentity.getAttribute(name);
        return value == null || value.isBlank() ? null : value;
    }
}
