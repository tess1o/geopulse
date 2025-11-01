package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service responsible for collecting export data from various repositories.
 *
 * This service provides:
 * - Small dataset collections (favorites, user info, location sources, reverse geocoding)
 * - Bounded GPS point queries (for single trip/stay)
 * - Timeline data with entity expansion (for GPX exports)
 * - Single-entity fetches (trip by ID, stay by ID)
 *
 * For large GPS datasets, use repositories directly with pagination/streaming
 * (see StreamingExportService).
 */
@ApplicationScoped
@Slf4j
public class ExportDataCollectorService {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @Inject
    ReverseGeocodingLocationRepository reverseGeocodingLocationRepository;

    /**
     * Collects GPS points for a specific time range (used for trip/stay processing).
     * This is acceptable for small, bounded queries (single trip/stay).
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
     * Collects timeline stays with full entity expansion (for GPX export).
     * Timeline data is typically small (aggregated), so this is acceptable.
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
     * Collects timeline trips with full entity expansion (for GPX export).
     * Timeline data is typically small (aggregated), so this is acceptable.
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
     * Collects all favorites for the given user.
     * Favorites are typically a small dataset, so loading all into memory is acceptable.
     *
     * @param userId the user ID
     * @return list of favorites entities
     */
    public List<FavoritesEntity> collectFavorites(UUID userId) {
        log.debug("Collecting favorites for user {}", userId);

        var favorites = favoritesRepository.findByUserId(userId);

        log.debug("Collected {} favorites", favorites.size());
        return favorites;
    }

    /**
     * Collects user information.
     * Single entity, always safe to load.
     *
     * @param userId the user ID
     * @return the user entity
     * @throws IllegalStateException if user not found
     */
    public UserEntity collectUserInfo(UUID userId) {
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
     * Typically a small dataset (few location sources per user).
     *
     * @param userId the user ID
     * @return list of GPS source config entities
     */
    public List<GpsSourceConfigEntity> collectLocationSources(UUID userId) {
        log.debug("Collecting location sources for user {}", userId);

        var sources = gpsSourceRepository.findByUserId(userId);

        log.debug("Collected {} location sources", sources.size());
        return sources;
    }

    /**
     * Collects reverse geocoding locations by their IDs.
     * Typically a moderate dataset (geocoding for stays).
     *
     * @param geocodingIds set of geocoding location IDs
     * @return list of reverse geocoding location entities
     */
    public List<ReverseGeocodingLocationEntity> collectReverseGeocodingLocations(Set<Long> geocodingIds) {
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

    /**
     * Provides access to GPS point repository for streaming exports.
     * Use this for pagination-based streaming exports.
     *
     * @return the GPS point repository
     */
    public GpsPointRepository getGpsPointRepository() {
        return gpsPointRepository;
    }
}
