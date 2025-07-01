package org.github.tess1o.geopulse.timeline.detection.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TimelineTrip;

import java.util.List;
import java.util.Map;

/**
 * Unified service for trip detection with algorithm selection.
 * Provides a clean interface for trip detection while encapsulating
 * algorithm selection and configuration validation.
 */
@ApplicationScoped
@Slf4j
public class TripDetectionService {

    private final Map<String, TimelineTripsDetector> detectors;

    @Inject
    public TripDetectionService(TimelineTripsDetectorSingle singleDetector,
                              TimelineTripsDetectorMulti multiDetector) {
        this.detectors = Map.of(
                "single", singleDetector,
                "simple", singleDetector,
                "uni", singleDetector,
                "multi", multiDetector,
                "multiple", multiDetector,
                "multimodal", multiDetector
        );
    }

    /**
     * Detect trips between stay points using the configured algorithm.
     * 
     * @param config timeline configuration containing algorithm selection and parameters
     * @param allPoints all GPS points for the time period
     * @param stayPoints detected stay points between which trips occur
     * @return detected trips with travel mode classification
     * @throws IllegalArgumentException if algorithm is unknown or configuration is invalid
     */
    public List<TimelineTrip> detectTrips(TimelineConfig config, 
                                         List<GpsPointPathPointDTO> allPoints, 
                                         List<TimelineStayPoint> stayPoints) {
        validateInputs(config, allPoints, stayPoints);
        
        String algorithmName = config.getTripDetectionAlgorithm();
        log.debug("Detecting trips using algorithm: {}", algorithmName);
        
        TimelineTripsDetector detector = getDetector(algorithmName);
        List<TimelineTrip> trips = detector.detectTrips(config, allPoints, stayPoints);
        
        log.debug("Detected {} trips between {} stay points", trips.size(), stayPoints.size());
        return trips;
    }

    /**
     * Get available trip detection algorithms.
     * 
     * @return set of available algorithm names
     */
    public java.util.Set<String> getAvailableAlgorithms() {
        return detectors.keySet();
    }

    /**
     * Check if an algorithm is available.
     * 
     * @param algorithmName the algorithm name to check
     * @return true if algorithm is available
     */
    public boolean isAlgorithmAvailable(String algorithmName) {
        return detectors.containsKey(algorithmName.toLowerCase());
    }

    /**
     * Get trip detector for the specified algorithm.
     * 
     * @param algorithmName the algorithm name
     * @return trip detector implementation
     * @throws IllegalArgumentException if algorithm is unknown
     */
    private TimelineTripsDetector getDetector(String algorithmName) {
        TimelineTripsDetector detector = detectors.get(algorithmName.toLowerCase());
        if (detector == null) {
            throw new IllegalArgumentException("Unknown trip detection algorithm: " + algorithmName + 
                    ". Available algorithms: " + detectors.keySet());
        }
        return detector;
    }

    /**
     * Validate input parameters for trip detection.
     * 
     * @param config timeline configuration
     * @param allPoints GPS points to process
     * @param stayPoints stay points for trip boundaries
     * @throws IllegalArgumentException if inputs are invalid
     */
    private void validateInputs(TimelineConfig config, List<GpsPointPathPointDTO> allPoints, List<TimelineStayPoint> stayPoints) {
        if (config == null) {
            throw new IllegalArgumentException("Timeline configuration cannot be null");
        }
        if (allPoints == null) {
            throw new IllegalArgumentException("GPS points cannot be null");
        }
        if (stayPoints == null) {
            throw new IllegalArgumentException("Stay points cannot be null");
        }
        if (config.getTripDetectionAlgorithm() == null || config.getTripDetectionAlgorithm().trim().isEmpty()) {
            throw new IllegalArgumentException("Trip detection algorithm must be specified in configuration");
        }
    }
}