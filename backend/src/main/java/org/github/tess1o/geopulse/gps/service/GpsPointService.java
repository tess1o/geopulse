package org.github.tess1o.geopulse.gps.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.user.model.UserEntity;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GpsPointService {
    private final GpsPointMapper gpsPointMapper;
    private final GpsPointRepository gpsPointRepository;
    private final GpsPointDuplicateDetectionService duplicateDetectionService;
    private final EntityManager em;

    @Inject
    public GpsPointService(GpsPointMapper gpsPointMapper, GpsPointRepository gpsPointRepository, 
                          GpsPointDuplicateDetectionService duplicateDetectionService, EntityManager em) {
        this.gpsPointMapper = gpsPointMapper;
        this.gpsPointRepository = gpsPointRepository;
        this.duplicateDetectionService = duplicateDetectionService;
        this.em = em;
    }

    @Transactional
    public void saveOwnTracksGpsPoint(OwnTracksLocationMessage message, UUID userId, String deviceId, GpsSourceType sourceType) {
        Instant timestamp = Instant.ofEpochSecond(message.getTst());
        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(message, user, deviceId, sourceType);
        gpsPointRepository.persist(entity);
        log.info("Saved OwnTracks GPS point for user {} at timestamp {}", userId, timestamp);
    }

    @Transactional
    public void saveOverlandGpsPoint(OverlandLocationMessage message, UUID userId, GpsSourceType sourceType) {
        Instant timestamp = message.getProperties().getTimestamp();
        
        if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
            log.info("Skipping duplicate Overland GPS point for user {} at timestamp {}", userId, timestamp);
            return;
        }
        
        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(message, user, sourceType);
        gpsPointRepository.persist(entity);
        log.info("Saved Overland GPS point for user {} at timestamp {}", userId, timestamp);
    }

    /**
     * Get a GPS point path for a user within a specified time period.
     * This method retrieves individual GPS points and constructs a path from them.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @return A GpsPointPathDTO containing the path points
     */
    public GpsPointPathDTO getGpsPointPath(UUID userId, Instant startTime, Instant endTime) {
        List<GpsPointEntity> gpsPoints = gpsPointRepository.findByUserIdAndTimePeriod(userId, startTime, endTime);
        List<GpsPointPathPointDTO> pathPoints = gpsPointMapper.toPathPoints(gpsPoints);

        return new GpsPointPathDTO(userId, pathPoints);
    }
}