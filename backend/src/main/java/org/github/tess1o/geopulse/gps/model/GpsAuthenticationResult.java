package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;

import java.util.UUID;

/**
 * Result of GPS integration authentication.
 * Contains both the authenticated user ID and the actual GPS source configuration.
 * This avoids a second DB lookup - the authenticator already loaded the config for validation,
 * so we pass it along for downstream services to use (e.g., data filtering).
 */
@Data
@AllArgsConstructor
public class GpsAuthenticationResult {
    /**
     * The ID of the authenticated user
     */
    private UUID userId;

    /**
     * The GPS source configuration that was used for authentication.
     * Contains all settings including filtering thresholds.
     */
    private GpsSourceConfigEntity config;
}
