package org.github.tess1o.geopulse.auth.oidc.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.oidc.model.OidcSessionStateEntity;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class OidcSessionStateRepository implements PanacheRepository<OidcSessionStateEntity> {
    
    /**
     * Find OIDC session state by state token
     */
    public Optional<OidcSessionStateEntity> findByStateToken(String stateToken) {
        return find("stateToken", stateToken).firstResultOptional();
    }
    
    /**
     * Find OIDC session state by state token that hasn't expired
     */
    public Optional<OidcSessionStateEntity> findValidByStateToken(String stateToken) {
        return find("stateToken = ?1 AND expiresAt > ?2", stateToken, Instant.now()).firstResultOptional();
    }
    
    /**
     * Delete expired session states
     */
    public long deleteExpired() {
        return delete("expiresAt < ?1", Instant.now());
    }
    
    /**
     * Delete session states older than specified time
     */
    public long deleteOlderThan(Instant cutoffTime) {
        return delete("createdAt < ?1", cutoffTime);
    }
    
    /**
     * Count all unexpired session states
     */
    public long countUnexpired() {
        return count("expiresAt > ?1", Instant.now());
    }
}