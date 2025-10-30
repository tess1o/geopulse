package org.github.tess1o.geopulse.gps.service.filter;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;

/**
 * Service for filtering GPS data based on per-source configuration.
 * Filters GPS points that exceed configured accuracy or speed thresholds.
 * <p>
 * This service implements a unified filtering strategy that works across all GPS source types
 * (OwnTracks, Overland, Dawarich, HomeAssistant, etc.).
 */
@ApplicationScoped
@Slf4j
public class GpsDataFilteringService {

    /**
     * Filter GPS point entity based on source configuration.
     * <p>
     * If filtering is disabled for this source, all points are accepted.
     * If filtering is enabled, points are checked against configured thresholds.
     * Rejected points are logged with the reason and actual values.
     * <p>
     * Note: The entity's velocity should already be in km/h (mapper handles conversions).
     *
     * @param entity GPS point entity to filter (already mapped from source message)
     * @param config GPS source configuration containing filter settings
     * @return FilterResult indicating whether the point was accepted or rejected
     */
    public GpsFilterResult filter(GpsPointEntity entity, GpsSourceConfigEntity config) {

        // If filtering is not enabled for this source, accept all points
        if (!config.isFilterInaccurateData()) {
            return GpsFilterResult.accepted();
        }

        // Extract values from entity
        Double accuracy = entity.getAccuracy();
        Double speedKmh = entity.getVelocity(); // Already in km/h from mapper

        // Check accuracy threshold
        if (config.getMaxAllowedAccuracy() != null && accuracy != null) {
            if (accuracy > config.getMaxAllowedAccuracy()) {
                log.info("Rejected GPS point for user {} source {} - accuracy {}m exceeds limit {}m",
                        entity.getUser().getId(), entity.getSourceType(), accuracy, config.getMaxAllowedAccuracy());
                return GpsFilterResult.rejectedByAccuracy(accuracy, config.getMaxAllowedAccuracy());
            }
        }

        // Check speed threshold
        if (config.getMaxAllowedSpeed() != null && speedKmh != null) {
            if (speedKmh > config.getMaxAllowedSpeed()) {
                log.info("Rejected GPS point for user {} source {} - speed {} km/h exceeds limit {} km/h",
                        entity.getUser().getId(), entity.getSourceType(), speedKmh, config.getMaxAllowedSpeed());
                return GpsFilterResult.rejectedBySpeed(speedKmh, config.getMaxAllowedSpeed());
            }
        }

        // All checks passed
        return GpsFilterResult.accepted();
    }
}
