package org.github.tess1o.geopulse.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.mapper.LocationMapper;
import org.github.tess1o.geopulse.model.dto.LocationMessage;
import org.github.tess1o.geopulse.model.dto.LocationPathDTO;
import org.github.tess1o.geopulse.model.dto.LocationPathPointDTO;
import org.github.tess1o.geopulse.model.entity.LocationEntity;
import org.github.tess1o.geopulse.repository.LocationRepository;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Slf4j
public class LocationService {

    private final LocationMapper locationMapper;
    private final LocationRepository locationRepository;

    @Inject
    public LocationService(LocationMapper locationMapper, LocationRepository locationRepository) {
        this.locationMapper = locationMapper;
        this.locationRepository = locationRepository;
    }

    @Transactional
    public void saveLocation(LocationMessage message, String userId, String deviceId) {
        LocationEntity entity = locationMapper.toEntity(message, userId, deviceId);
        locationRepository.persist(entity);
        log.info("Saved location: {}", entity);
    }

    /**
     * Get a location path for a user within a specified time period.
     * This method retrieves individual points and constructs a path from them.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @return A LocationPathDTO containing the path points
     */
    public LocationPathDTO getLocationPath(String userId, Instant startTime, Instant endTime) {
        List<LocationEntity> locations = locationRepository.findByUserIdAndTimePeriod(userId, startTime, endTime);
        List<LocationPathPointDTO> pathPoints = locationMapper.toPathPoints(locations);

        return new LocationPathDTO(userId, pathPoints);
    }

    /**
     * Get a location path for a user within a specified time period using PostGIS features.
     * This method uses PostGIS ST_MakeLine function to generate a LineString representing the path.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @return A LocationPathDTO containing the path points
     */
    public LocationPathDTO getLocationPathUsingPostGIS(String userId, Instant startTime, Instant endTime) {
        try {
            // Generate a LineString using PostGIS
            LineString lineString = locationRepository.generatePathLineString(userId, startTime, endTime);

            if (lineString == null) {
                // Fallback to the original method if no path is found
                return getLocationPath(userId, startTime, endTime);
            }

            // Convert the LineString to a list of LocationPathPointDTO
            List<LocationPathPointDTO> pathPoints = new ArrayList<>();
            for (int i = 0; i < lineString.getNumPoints(); i++) {
                Point point = lineString.getPointN(i);

                // We don't have timestamp, accuracy, altitude, and velocity in the LineString
                // So we'll set them to null or default values
                pathPoints.add(new LocationPathPointDTO(
                        point.getY(), // Latitude
                        point.getX(), // Longitude
                        null, // Timestamp (not available in LineString)
                        null, // Accuracy (not available in LineString)
                        null, // Altitude (not available in LineString)
                        null  // Velocity (not available in LineString)
                ));
            }

            return new LocationPathDTO(userId, pathPoints);
        } catch (Exception e) {
            // Fallback to the original method if there's an error
            return getLocationPath(userId, startTime, endTime);
        }
    }
}
