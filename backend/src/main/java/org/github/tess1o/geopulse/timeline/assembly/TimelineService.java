package org.github.tess1o.geopulse.timeline.assembly;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.timeline.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.timeline.detection.gaps.DataGapDetectionService;
import org.github.tess1o.geopulse.timeline.detection.stays.StayPointDetectionService;
import org.github.tess1o.geopulse.timeline.detection.trips.TripDetectionService;
import org.github.tess1o.geopulse.timeline.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class TimelineService {

    private final TimelineDataService timelineDataService;
    private final TimelineConfigurationProvider configurationProvider;
    private final TimelineAssemblyService assemblyService;
    private final StayPointDetectionService stayPointDetectionService;
    private final TripDetectionService tripDetectionService;
    private final TimelineProcessingService processingService;
    private final DataGapDetectionService dataGapDetectionService;

    @Inject
    public TimelineService(TimelineDataService timelineDataService,
                          TimelineConfigurationProvider configurationProvider,
                          TimelineAssemblyService assemblyService,
                          StayPointDetectionService stayPointDetectionService,
                          TripDetectionService tripDetectionService,
                          TimelineProcessingService processingService,
                          DataGapDetectionService dataGapDetectionService) {
        this.timelineDataService = timelineDataService;
        this.configurationProvider = configurationProvider;
        this.assemblyService = assemblyService;
        this.stayPointDetectionService = stayPointDetectionService;
        this.tripDetectionService = tripDetectionService;
        this.processingService = processingService;
        this.dataGapDetectionService = dataGapDetectionService;
    }

    /**
     * Get the effective timeline configuration for a user.
     * 
     * @param userId the user identifier
     * @return effective timeline configuration
     */
    public TimelineConfig getEffectiveConfigForUser(UUID userId) {
        return configurationProvider.getConfigurationForUser(userId);
    }

    /**
     * Generate a movement timeline for a user within a time range.
     * This is the main orchestration method that coordinates all timeline processing steps.
     * 
     * @param userId the user identifier
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return complete movement timeline with stays and trips
     */
    public MovementTimelineDTO getMovementTimeline(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Generating movement timeline for user {} from {} to {}", userId, startTime, endTime);

        // 1. Get configuration
        TimelineConfig config = configurationProvider.getConfigurationForUser(userId);

        // 2. Get raw GPS data
        GpsPointPathDTO gpsData = timelineDataService.getGpsPointPath(userId, startTime, endTime);
        if (gpsData == null) {
            return assemblyService.createEmptyTimeline(userId);
        }

        // 3. Convert to track points for algorithm processing
        List<TrackPoint> trackPoints = timelineDataService.convertToTrackPoints(gpsData);

        // 4. Detect data gaps in GPS data
        List<TimelineDataGapDTO> dataGaps = dataGapDetectionService.detectDataGaps(config, trackPoints);

        // 5. Detect stay points
        List<TimelineStayPoint> stayPoints = stayPointDetectionService.detectStayPoints(config, trackPoints);

        // 6. Detect trips
        List<TimelineTrip> trips = tripDetectionService.detectTrips(config, gpsData.getPoints(), stayPoints);

        // 7. Assemble timeline
        MovementTimelineDTO timeline = assemblyService.assembleTimeline(userId, stayPoints, trips, dataGaps);

        // 8. Apply post-processing
        return processingService.processTimeline(config, timeline);
    }

}