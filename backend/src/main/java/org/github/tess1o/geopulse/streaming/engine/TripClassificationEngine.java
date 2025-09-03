package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Engine responsible for classifying trip types based on GPS movement patterns.
 * Reuses existing TravelClassification service for consistent trip classification.
 */
@ApplicationScoped
@Slf4j
public class TripClassificationEngine {

    @Inject
    TravelClassification travelClassification;

    /**
     * Classify a trip based on the GPS points along its path.
     * Uses existing TravelClassification service for consistent results.
     *
     * @param tripPoints the GPS points that make up the trip path
     * @param config     timeline configuration (unused but kept for compatibility)
     * @return the classified trip type
     */
    public TripType classifyTrip(List<GPSPoint> tripPoints, TimelineConfig config) {
        if (tripPoints == null || tripPoints.size() < 2) {
            log.debug("Insufficient GPS points for trip classification: {}",
                    tripPoints != null ? tripPoints.size() : 0);
            return TripType.UNKNOWN;
        }

        // Calculate trip duration
        Duration tripDuration = Duration.between(
                tripPoints.getFirst().getTimestamp(),
                tripPoints.getLast().getTimestamp()
        );

        // Use existing travel classification service (now returns TripType directly)
        return travelClassification.classifyTravelType(tripPoints, tripDuration, config);
    }
}