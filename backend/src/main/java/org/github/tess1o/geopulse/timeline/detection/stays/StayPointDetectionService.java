package org.github.tess1o.geopulse.timeline.detection.stays;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;

import java.util.List;
import java.util.Map;

/**
 * Unified service for stay point detection with algorithm selection.
 * Provides a clean interface for stay point detection while encapsulating
 * algorithm selection and configuration validation.
 */
@ApplicationScoped
@Slf4j
public class StayPointDetectionService {

    private final Map<String, StayPointDetector> detectors;

    @Inject
    public StayPointDetectionService(StayPointDetectorSimple simpleDetector,
                                   StayPointDetectorEnhanced enhancedDetector) {
        this.detectors = Map.of(
                "simple", simpleDetector,
                "enhanced", enhancedDetector
        );
    }

    /**
     * Detect stay points from track points using the configured algorithm.
     * 
     * @param config timeline configuration containing algorithm selection and parameters
     * @param trackPoints GPS track points for processing
     * @return detected stay points
     * @throws IllegalArgumentException if algorithm is unknown or configuration is invalid
     */
    public List<TimelineStayPoint> detectStayPoints(TimelineConfig config, List<TrackPoint> trackPoints) {
        validateInputs(config, trackPoints);
        
        String algorithmName = config.getStaypointDetectionAlgorithm();
        log.debug("Detecting stay points using algorithm: {}", algorithmName);
        
        StayPointDetector detector = getDetector(algorithmName);
        List<TimelineStayPoint> stayPoints = detector.detectStayPoints(config, trackPoints);
        
        log.debug("Detected {} stay points from {} track points", stayPoints.size(), trackPoints.size());
        return stayPoints;
    }

    /**
     * Get available stay point detection algorithms.
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
     * Get stay point detector for the specified algorithm.
     * 
     * @param algorithmName the algorithm name
     * @return stay point detector implementation
     * @throws IllegalArgumentException if algorithm is unknown
     */
    private StayPointDetector getDetector(String algorithmName) {
        StayPointDetector detector = detectors.get(algorithmName.toLowerCase());
        if (detector == null) {
            throw new IllegalArgumentException("Unknown stay point detection algorithm: " + algorithmName + 
                    ". Available algorithms: " + detectors.keySet());
        }
        return detector;
    }

    /**
     * Validate input parameters for stay point detection.
     * 
     * @param config timeline configuration
     * @param trackPoints track points to process
     * @throws IllegalArgumentException if inputs are invalid
     */
    private void validateInputs(TimelineConfig config, List<TrackPoint> trackPoints) {
        if (config == null) {
            throw new IllegalArgumentException("Timeline configuration cannot be null");
        }
        if (trackPoints == null) {
            throw new IllegalArgumentException("Track points cannot be null");
        }
        if (config.getStaypointDetectionAlgorithm() == null || config.getStaypointDetectionAlgorithm().trim().isEmpty()) {
            throw new IllegalArgumentException("Stay point detection algorithm must be specified in configuration");
        }
    }
}