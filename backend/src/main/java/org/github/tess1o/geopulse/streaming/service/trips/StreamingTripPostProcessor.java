package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;

import java.util.List;
import java.util.UUID;

/**
 * Post-processes streaming timeline events to implement proper trip detection algorithms.
 * This handles the distinction between "single" and "multi" trip detection modes,
 * and applies proper validation and merging logic.
 */
@ApplicationScoped
@Slf4j
public class StreamingTripPostProcessor {

    @Inject
    SteamingTripAlgorithmFactory algorithmFactory;

    /**
     * Post-process timeline events to implement proper trip detection based on configuration.
     *
     * @param events Raw timeline events from streaming processor
     * @param config Timeline configuration with algorithm selection
     * @return Processed events with proper trip detection applied
     */
    public List<TimelineEvent> postProcessTrips(UUID userId, List<TimelineEvent> events, TimelineConfig config) {
        String algorithm = config.getTripDetectionAlgorithm();

        log.debug("Post-processing trips with algorithm: {}", algorithm);

        StreamTripAlgorithm streamTripAlgorithm = algorithmFactory.get(algorithm);
        return streamTripAlgorithm.apply(userId, events, config);

    }
}