package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service responsible for collecting export data from various repositories.
 * Handles pagination and data retrieval for all export types.
 */
@ApplicationScoped
@Slf4j
public class ExportDataCollectorService {

    private static final int DEFAULT_PAGE_SIZE = 1000;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @Inject
    ReverseGeocodingLocationRepository reverseGeocodingLocationRepository;

    /**
     * Collects all GPS points for the given export job using pagination.
     *
     * @param job the export job containing user ID and date range
     * @return list of all GPS points in the date range
     */
    public List<GpsPointEntity> collectGpsPoints(ExportJob job) {
        log.debug("Collecting GPS points for user {} in date range {} to {}",
                job.getUserId(), job.getDateRange().getStartDate(), job.getDateRange().getEndDate());

        List<GpsPointEntity> allPoints = new ArrayList<>();
        int page = 0;

        while (true) {
            var pageData = gpsPointRepository.findByUserAndDateRange(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate(),
                    page,
                    DEFAULT_PAGE_SIZE,
                    "timestamp",
                    "asc"
            );

            if (pageData.isEmpty()) {
                break;
            }

            allPoints.addAll(pageData);
            page++;
        }

        log.debug("Collected {} GPS points", allPoints.size());
        return allPoints;
    }

    /**
     * Collects GPS points for a specific time range (used for trip/stay processing).
     *
     * @param userId    the user ID
     * @param startTime the start time
     * @param endTime   the end time
     * @return list of GPS points in the time range
     */
    public List<GpsPointEntity> collectGpsPointsInTimeRange(UUID userId,
                                                             java.time.Instant startTime,
                                                             java.time.Instant endTime) {
        return gpsPointRepository.findByUserAndDateRange(
                userId,
                startTime,
                endTime,
                0,
                10000, // Large limit to get all points in a typical trip
                "timestamp",
                "asc"
        );
    }

    /**
     * Collects timeline stays for the given export job.
     *
     * @param job the export job
     * @return list of timeline stays
     */
    public List<TimelineStayEntity> collectTimelineStays(ExportJob job) {
        log.debug("Collecting timeline stays for user {}", job.getUserId());

        var stays = timelineStayRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        log.debug("Collected {} timeline stays", stays.size());
        return stays;
    }

    /**
     * Collects timeline stays with full entity expansion (for GPX export).
     *
     * @param job the export job
     * @return list of timeline stays with expanded entities
     */
    public List<TimelineStayEntity> collectTimelineStaysWithExpansion(ExportJob job) {
        log.debug("Collecting timeline stays with expansion for user {}", job.getUserId());

        var stays = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        log.debug("Collected {} timeline stays with expansion", stays.size());
        return stays;
    }

    /**
     * Collects timeline trips for the given export job.
     *
     * @param job the export job
     * @return list of timeline trips
     */
    public List<TimelineTripEntity> collectTimelineTrips(ExportJob job) {
        log.debug("Collecting timeline trips for user {}", job.getUserId());

        var trips = timelineTripRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        log.debug("Collected {} timeline trips", trips.size());
        return trips;
    }

    /**
     * Collects timeline trips with full entity expansion (for GPX export).
     *
     * @param job the export job
     * @return list of timeline trips with expanded entities
     */
    public List<TimelineTripEntity> collectTimelineTripsWithExpansion(ExportJob job) {
        log.debug("Collecting timeline trips with expansion for user {}", job.getUserId());

        var trips = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        log.debug("Collected {} timeline trips with expansion", trips.size());
        return trips;
    }

    /**
     * Collects data gaps for the given export job.
     *
     * @param job the export job
     * @return list of data gaps
     */
    public List<TimelineDataGapEntity> collectDataGaps(ExportJob job) {
        log.debug("Collecting data gaps for user {}", job.getUserId());

        var dataGaps = timelineDataGapRepository.findByUserIdAndTimeRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        log.debug("Collected {} data gaps", dataGaps.size());
        return dataGaps;
    }

    /**
     * Collects all favorites for the given user.
     *
     * @param userId the user ID
     * @return list of favorites entities
     */
    public List<org.github.tess1o.geopulse.favorites.model.FavoritesEntity> collectFavorites(UUID userId) {
        log.debug("Collecting favorites for user {}", userId);

        var favorites = favoritesRepository.findByUserId(userId);

        log.debug("Collected {} favorites", favorites.size());
        return favorites;
    }

    /**
     * Collects user information.
     *
     * @param userId the user ID
     * @return the user entity
     * @throws IllegalStateException if user not found
     */
    public org.github.tess1o.geopulse.user.model.UserEntity collectUserInfo(UUID userId) {
        log.debug("Collecting user info for user {}", userId);

        var user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalStateException("User not found: " + userId);
        }

        log.debug("Collected user info for {}", user.getEmail());
        return user;
    }

    /**
     * Collects location sources for the given user.
     *
     * @param userId the user ID
     * @return list of GPS source config entities
     */
    public List<org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity> collectLocationSources(UUID userId) {
        log.debug("Collecting location sources for user {}", userId);

        var sources = gpsSourceRepository.findByUserId(userId);

        log.debug("Collected {} location sources", sources.size());
        return sources;
    }

    /**
     * Collects reverse geocoding locations by their IDs.
     *
     * @param geocodingIds set of geocoding location IDs
     * @return list of reverse geocoding location entities
     */
    public List<org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity> collectReverseGeocodingLocations(Set<Long> geocodingIds) {
        if (geocodingIds.isEmpty()) {
            log.debug("No reverse geocoding locations to collect");
            return List.of();
        }

        log.debug("Collecting {} reverse geocoding locations", geocodingIds.size());

        var locations = reverseGeocodingLocationRepository.findByIds(geocodingIds.stream().toList());

        log.debug("Collected {} reverse geocoding locations", locations.size());
        return locations;
    }

    /**
     * Fetches a single trip by ID for the given user.
     *
     * @param userId the user ID
     * @param tripId the trip ID
     * @return the trip entity
     * @throws IllegalArgumentException if trip not found or access denied
     */
    public TimelineTripEntity fetchTripById(UUID userId, Long tripId) {
        log.debug("Fetching trip {} for user {}", tripId, userId);

        var trip = timelineTripRepository.findById(tripId);
        if (trip == null || !trip.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Trip not found or access denied");
        }

        return trip;
    }

    /**
     * Fetches a single stay by ID for the given user.
     *
     * @param userId the user ID
     * @param stayId the stay ID
     * @return the stay entity
     * @throws IllegalArgumentException if stay not found or access denied
     */
    public TimelineStayEntity fetchStayById(UUID userId, Long stayId) {
        log.debug("Fetching stay {} for user {}", stayId, userId);

        var stay = timelineStayRepository.findById(stayId);
        if (stay == null || !stay.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Stay not found or access denied");
        }

        return stay;
    }
}
