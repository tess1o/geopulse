package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.notes.repository.TimelineNoteRepository;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;
import org.github.tess1o.geopulse.periods.repository.PeriodTagRepository;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapStayOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapStayOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleRepository;
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
    EntityManager entityManager;

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

    @Inject
    SystemSettingsService settingsService;

    @Inject
    PeriodTagRepository periodTagRepository;

    @Inject
    TimelineTripMovementOverrideRepository tripMovementOverrideRepository;

    @Inject
    TimelineDataGapStayOverrideRepository dataGapStayOverrideRepository;

    @Inject
    TripRepository tripRepository;

    @Inject
    NotificationTemplateRepository notificationTemplateRepository;

    @Inject
    GeofenceRuleRepository geofenceRuleRepository;

    @Inject
    TimelineNoteRepository timelineNoteRepository;

    @Inject
    WeatherSampleRepository weatherSampleRepository;

    public List<TimelineTripPathMatchEntity> collectMapMatchingPathMatches(ExportJob job) {
        return entityManager.createQuery("""
                SELECT pathMatch FROM TimelineTripPathMatchEntity pathMatch
                JOIN FETCH pathMatch.trip trip
                WHERE pathMatch.user.id = :userId
                  AND pathMatch.status = :status
                  AND pathMatch.matchedSegmentsJson IS NOT NULL
                  AND trip.timestamp >= :startDate
                  AND trip.timestamp <= :endDate
                ORDER BY trip.timestamp ASC, pathMatch.id ASC
                """, TimelineTripPathMatchEntity.class)
                .setParameter("userId", job.getUserId())
                .setParameter("status", MapMatchingStatus.MATCHED)
                .setParameter("startDate", job.getDateRange().getStartDate())
                .setParameter("endDate", job.getDateRange().getEndDate())
                .getResultList();
    }

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
        int tripPointLimit = settingsService.getInteger("export.trip-point-limit");
        return gpsPointRepository.findByUserAndDateRange(
                userId,
                startTime,
                endTime,
                0,
                tripPointLimit,
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

        List<ReverseGeocodingLocationEntity> locations = reverseGeocodingLocationRepository.findByIds(geocodingIds.stream().toList());

        log.debug("Collected {} reverse geocoding locations", locations.size());
        return locations;
    }

    public List<PeriodTagEntity> collectPeriodTags(ExportJob job) {
        var startDate = job.getDateRange().getStartDate();
        var endDate = job.getDateRange().getEndDate();
        return periodTagRepository.findByUserId(job.getUserId()).stream()
                .filter(tag -> !tag.getStartTime().isAfter(endDate))
                .filter(tag -> tag.getEndTime() == null || !tag.getEndTime().isBefore(startDate))
                .toList();
    }

    public List<TimelineTripMovementOverrideEntity> collectTripMovementOverrides(UUID userId) {
        return tripMovementOverrideRepository.findByUserId(userId);
    }

    public List<TimelineDataGapStayOverrideEntity> collectDataGapStayOverrides(UUID userId) {
        return dataGapStayOverrideRepository.findByUserId(userId);
    }

    public List<TripEntity> collectTrips(UUID userId) {
        return tripRepository.findByUserId(userId);
    }

    public List<TripPlanItemEntity> collectTripPlanItems(List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "SELECT item FROM TripPlanItemEntity item " +
                                "WHERE item.trip.id IN :tripIds " +
                                "ORDER BY item.trip.id, item.orderIndex ASC, item.createdAt ASC",
                        TripPlanItemEntity.class)
                .setParameter("tripIds", tripIds)
                .getResultList();
    }

    public List<TripCollaboratorEntity> collectTripCollaborators(List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "SELECT collaborator FROM TripCollaboratorEntity collaborator " +
                                "JOIN FETCH collaborator.collaborator " +
                                "WHERE collaborator.trip.id IN :tripIds " +
                                "ORDER BY collaborator.trip.id, collaborator.createdAt ASC",
                        TripCollaboratorEntity.class)
                .setParameter("tripIds", tripIds)
                .getResultList();
    }

    public List<NotificationTemplateEntity> collectNotificationTemplates(UUID userId) {
        return notificationTemplateRepository.findByUser(userId);
    }

    public List<GeofenceRuleEntity> collectGeofenceRules(UUID userId) {
        return geofenceRuleRepository.findByOwner(userId);
    }

    public List<TimelineNoteEntity> collectNotes(ExportJob job) {
        return timelineNoteRepository.findByUserIdAndTimeRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );
    }

    public List<WeatherSampleEntity> collectWeatherSamples(ExportJob job) {
        return weatherSampleRepository.findByUserAndRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate(),
                null,
                null,
                null,
                null
        );
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
