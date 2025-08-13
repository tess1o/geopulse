package org.github.tess1o.geopulse.gpssource.service;

import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;

import java.util.Optional;

/**
 * Interface for providing GPS source configuration lookup operations.
 * This interface isolates GPS configuration lookup from the full CRUD operations,
 * allowing better separation of concerns between GPS data ingestion and configuration management.
 */
public interface GpsSourceConfigProvider {
    
    /**
     * Find GPS source configuration by username (for OwnTracks)
     */
    Optional<GpsSourceConfigEntity> findByUsername(String username);
    
    /**
     * Find GPS source configuration by token (for Overland)
     */
    Optional<GpsSourceConfigEntity> findByToken(String token);

    /**
     * Find GPS source configuration by username and connection type (for MQTT support)
     *
     * @param username The username to search for
     * @param connectionType The connection type (HTTP or MQTT)
     * @return Optional containing the config if found and active
     */
    Optional<GpsSourceConfigEntity> findByUsernameAndConnectionType(String username, GpsSourceConfigEntity.ConnectionType connectionType);
}