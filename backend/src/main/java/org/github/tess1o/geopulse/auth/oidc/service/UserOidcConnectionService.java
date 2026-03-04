package org.github.tess1o.geopulse.auth.oidc.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.oidc.dto.UserOidcConnectionResponse;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;
import org.github.tess1o.geopulse.auth.oidc.repository.UserOidcConnectionRepository;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class UserOidcConnectionService {
    
    @Inject
    UserOidcConnectionRepository connectionRepository;
    
    @Inject
    OidcProviderService providerService;
    
    @Inject
    UserService userService;

    @Inject
    EntityManager em;
    
    /**
     * Get all OIDC connections for a user as a list of DTOs, fetched efficiently.
     */
    public List<UserOidcConnectionResponse> getUserConnections(UUID userId) {
        String query = """
            SELECT NEW org.github.tess1o.geopulse.auth.oidc.dto.UserOidcConnectionResponse(
                c.providerName, 
                c.displayName, 
                u.email, 
                c.avatarUrl, 
                c.linkedAt
            )
            FROM UserOidcConnectionEntity c JOIN c.user u
            WHERE u.id = :userId
            """;

        List<UserOidcConnectionResponse> connections = em.createQuery(query, UserOidcConnectionResponse.class)
                .setParameter("userId", userId)
                .getResultList();

        // Post-process to add the provider's display name and icon from config
        connections.forEach(c -> {
            providerService.findByName(c.getProviderName()).ifPresent(p -> {
                c.setProviderDisplayName(p.getDisplayName());
                c.setProviderIcon(p.getIcon());
            });
        });

        return connections;
    }
    
    /**
     * Unlink an OIDC provider from a user account
     */
    @Transactional
    public void unlinkProvider(UUID userId, String providerName) {
        UserEntity user = userService.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        providerService.findByName(providerName)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        
        Optional<UserOidcConnectionEntity> connection = 
                connectionRepository.findByUserIdAndProviderName(userId, providerName);
        
        if (connection.isEmpty()) {
            throw new IllegalArgumentException("No connection found for this provider");
        }
        
        // Ensure user has a password or other OIDC connections before unlinking
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            long otherConnections = connectionRepository.countByUserIdExcludingProvider(userId, providerName);
            if (otherConnections == 0) {
                throw new IllegalArgumentException("Cannot unlink the only authentication method. Please set a password first.");
            }
        }
        
        connectionRepository.delete(connection.get());
        log.info("Unlinked OIDC provider {} from user {}", providerName, userId);
    }
}