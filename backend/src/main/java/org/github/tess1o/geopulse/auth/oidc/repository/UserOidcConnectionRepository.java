package org.github.tess1o.geopulse.auth.oidc.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserOidcConnectionRepository implements PanacheRepository<UserOidcConnectionEntity> {
    
    /**
     * Find all OIDC connections for a specific user
     */
    public List<UserOidcConnectionEntity> findByUserId(UUID userId) {
        return list("userId", userId);
    }
    
    /**
     * Find OIDC connection by provider name and external user ID
     */
    public Optional<UserOidcConnectionEntity> findByProviderNameAndExternalUserId(String providerName, String externalUserId) {
        return find("providerName = ?1 AND externalUserId = ?2", providerName, externalUserId).firstResultOptional();
    }
    
    /**
     * Find OIDC connection by user ID and provider name
     */
    public Optional<UserOidcConnectionEntity> findByUserIdAndProviderName(UUID userId, String providerName) {
        return find("userId = ?1 AND providerName = ?2", userId, providerName).firstResultOptional();
    }
    
    /**
     * Count OIDC connections for a user excluding a specific provider
     */
    public long countByUserIdExcludingProvider(UUID userId, String excludeProviderName) {
        return count("userId = ?1 AND providerName != ?2", userId, excludeProviderName);
    }
    
    /**
     * Check if a user has any OIDC connections
     */
    public boolean hasOidcConnections(UUID userId) {
        return count("userId", userId) > 0;
    }
    
    /**
     * Delete all OIDC connections for a user
     */
    public long deleteByUserId(UUID userId) {
        return delete("userId", userId);
    }
}