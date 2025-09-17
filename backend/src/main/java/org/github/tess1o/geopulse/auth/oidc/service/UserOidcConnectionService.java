package org.github.tess1o.geopulse.auth.oidc.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.oidc.dto.UserOidcConnectionResponse;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
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
     * Get OIDC connection for a user and specific provider
     */
    public Optional<UserOidcConnectionEntity> getUserConnection(UUID userId, String providerName) {
        return connectionRepository.findByUserIdAndProviderName(userId, providerName);
    }
    
    /**
     * Check if a user has any OIDC connections
     */
    public boolean hasOidcConnections(UUID userId) {
        return connectionRepository.hasOidcConnections(userId);
    }
    
    /**
     * Unlink an OIDC provider from a user account
     */
    @Transactional
    public void unlinkProvider(UUID userId, String providerName) {
        UserEntity user = userService.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        OidcProviderConfiguration provider = providerService.findByName(providerName)
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
    
    /**
     * Check if unlinking a provider would be allowed for a user
     */
    public boolean canUnlinkProvider(UUID userId, String providerName) {
        try {
            UserEntity user = userService.findById(userId).orElse(null);
            if (user == null) {
                return false;
            }
            
            // If user has a password, they can unlink any provider
            if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
                return true;
            }
            
            // If user has no password, check if they have other OIDC connections
            long otherConnections = connectionRepository.countByUserIdExcludingProvider(userId, providerName);
            return otherConnections > 0;
            
        } catch (Exception e) {
            log.error("Error checking if provider can be unlinked", e);
            return false;
        }
    }
    
    /**
     * Get connection by provider and external user ID
     */
    public Optional<UserOidcConnectionEntity> findByProviderAndExternalId(String providerName, String externalUserId) {
        return connectionRepository.findByProviderNameAndExternalUserId(providerName, externalUserId);
    }
    
    /**
     * Delete all OIDC connections for a user (useful for account deletion)
     */
    @Transactional
    public void deleteAllUserConnections(UUID userId) {
        long deletedCount = connectionRepository.deleteByUserId(userId);
        log.info("Deleted {} OIDC connections for user {}", deletedCount, userId);
    }
}