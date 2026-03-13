package org.github.tess1o.geopulse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.insight.repository.UserBadgeRepository;
import org.github.tess1o.geopulse.periods.repository.PeriodTagRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.github.tess1o.geopulse.trips.repository.TripPlaceVisitMatchRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;

@ApplicationScoped
public class CleanupHelper {

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

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

    @Inject
    TripPlaceVisitMatchRepository tripPlaceVisitMatchRepository;

    @Inject
    TripPlanItemRepository tripPlanItemRepository;

    @Inject
    TripRepository tripWorkspaceRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    PeriodTagRepository periodTagRepository;

    @Transactional
    public void cleanupTimeline() {
        this.badgeRepository.deleteAll();
        this.tripMovementOverrideRepository.deleteAll();
        this.stayRepository.deleteAll();
        this.timelineTripRepository.deleteAll();
        this.dataGapRepository.deleteAll();
    }

    @Transactional
    public void cleanupTripWorkspace() {
        this.tripPlaceVisitMatchRepository.deleteAll();
        this.tripPlanItemRepository.deleteAll();
        this.tripWorkspaceRepository.deleteAll();
        this.favoritesRepository.deleteAll();
        this.periodTagRepository.deleteAll();
    }

    @Transactional
    public void cleanupTripWorkspaceAndUsers() {
        this.cleanupTripWorkspace();
        this.cleanupTimeline();
        this.userRepository.deleteAll();
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
