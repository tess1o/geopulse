package org.github.tess1o.geopulse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.insight.repository.UserBadgeRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;

@ApplicationScoped
public class CleanupHelper {

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineTripMovementOverrideRepository tripMovementOverrideRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    UserBadgeRepository badgeRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    ReverseGeocodingLocationRepository reverseGeocodingLocationRepository;

    @Inject
    FriendshipRepository friendshipRepository;

    @Transactional
    public void cleanupTimeline() {
        this.badgeRepository.deleteAll();
        this.tripMovementOverrideRepository.deleteAll();
        this.stayRepository.deleteAll();
        this.tripRepository.deleteAll();
        this.dataGapRepository.deleteAll();
    }

    @Transactional
    public void cleanupAll(){
        this.cleanupTimeline();
        gpsSourceRepository.deleteAll();
        gpsPointRepository.deleteAll();
        reverseGeocodingLocationRepository.deleteAll();
        friendshipRepository.deleteAll();
        userRepository.deleteAll();
    }
}
