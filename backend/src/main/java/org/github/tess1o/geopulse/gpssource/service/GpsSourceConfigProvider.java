package org.github.tess1o.geopulse.gpssource.service;

import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

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
     * Find GPS source configuration by username and source type (for HTTP Basic auth integrations).
     */
    Optional<GpsSourceConfigEntity> findByUsernameAndSourceType(String username, GpsSourceType sourceType);

    /**
     * Find GPS source configuration by token (for Overland)
     */
    Optional<GpsSourceConfigEntity> findByToken(String token);

    /**
     * Find GPS source configuration by token and source type (for token-based integrations).
     */
    Optional<GpsSourceConfigEntity> findByTokenAndSourceType(String token, GpsSourceType sourceType);

    /**
     * Find GPS source configuration by username and connection type (for MQTT support)
     *
     * @param username The username to search for
     * @param connectionType The connection type (HTTP or MQTT)
     * @return Optional containing the config if found and active
     */
    Optional<GpsSourceConfigEntity> findByUsernameAndConnectionType(String username, GpsSourceConfigEntity.ConnectionType connectionType);
}
