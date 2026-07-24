package org.github.tess1o.geopulse.weather.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.client.OpenMeteoWeatherClient;
import org.github.tess1o.geopulse.weather.dto.*;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherSampleTargetEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetClaim;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@Slf4j
public class WeatherService {

    private static final int ONGOING_PRIORITY = 100;
    private static final int ADMIN_PRIORITY = 80;
    private static final int HISTORICAL_BACKFILL_PRIORITY = 70;
    private static final Duration HISTORICAL_BACKFILL_DELAY = Duration.ofHours(2);

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    WeatherSamplingPolicy samplingPolicy;

    @Inject
    WeatherQuotaService quotaService;

    @Inject
    WeatherSampleRepository sampleRepository;

    @Inject
    WeatherSampleTargetRepository targetRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    OpenMeteoWeatherClient weatherClient;

    @Inject
    EntityManager entityManager;

    public WeatherSamplesResponse findSamples(UUID userId, Instant startTime, Instant endTime,
                                              Double minLat, Double minLon, Double maxLat, Double maxLon) {
        List<WeatherSampleDTO> samples = List.of();
        if (configurationService.isEnabled() && startTime != null && endTime != null) {
            samples = sampleRepository.toDtos(sampleRepository.findByUserAndRange(
                    userId, startTime, endTime, minLat, minLon, maxLat, maxLon));
        }

        return WeatherSamplesResponse.builder()
                .enabled(configurationService.isEnabled())
                .configured(configurationService.isConfigured())
                .provider(WeatherConfigurationService.PROVIDER_OPEN_METEO)
                .attributionUrl(WeatherConfigurationService.ATTRIBUTION_URL)
                .units(metricUnits())
                .samples(samples)
                .build();
    }

    @Transactional
    public WeatherTargetQueueResponse discoverHistoricalBackfillTargets() {
        if (!configurationService.isEnabled() || !configurationService.backfillEnabled()) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }

        Instant endTime = Instant.now().minus(HISTORICAL_BACKFILL_DELAY);
        int created = 0;
        int known = 0;
        int skipped = 0;
        for (UserEntity user : activeUsers()) {
            WeatherTargetQueueResponse response = enqueueForRange(
                    user.getId(),
                    Instant.EPOCH,
                    endTime,
                    WeatherTargetSource.HISTORICAL_BACKFILL,
                    HISTORICAL_BACKFILL_PRIORITY
            );
            created += response.getTargetsCreated();
            known += response.getTargetsAlreadyKnown();
            skipped += response.getTargetsSkipped();
        }
        return WeatherTargetQueueResponse.builder()
                .targetsCreated(created)
                .targetsAlreadyKnown(known)
                .targetsSkipped(skipped)
                .build();
    }

    @Transactional
    public WeatherTargetQueueResponse discoverHistoricalBackfillTargets(UUID userId, Instant startTime, Instant endTime) {
        if (!configurationService.isEnabled() || !configurationService.backfillEnabled()) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }
        if (userId == null) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }

        Instant effectiveStart = startTime != null ? startTime : Instant.EPOCH;
        Instant latestHistoricalTime = Instant.now().minus(HISTORICAL_BACKFILL_DELAY);
        Instant requestedEnd = endTime != null ? endTime : latestHistoricalTime;
        Instant effectiveEnd = requestedEnd.isAfter(latestHistoricalTime) ? latestHistoricalTime : requestedEnd;
        if (effectiveEnd.isBefore(effectiveStart)) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }

        return enqueueForRange(
                userId,
                effectiveStart,
                effectiveEnd,
                WeatherTargetSource.HISTORICAL_BACKFILL,
                HISTORICAL_BACKFILL_PRIORITY
        );
    }

    @Transactional
    public WeatherTargetQueueResponse discoverAdminBackfillTargets(WeatherBackfillRequest request) {
        if (!configurationService.isEnabled() || !configurationService.backfillEnabled()) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }
        if (request == null || request.getStartTime() == null || request.getEndTime() == null || !request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }

        if (request.getUserId() != null) {
            return enqueueForRange(request.getUserId(), request.getStartTime(), request.getEndTime(), WeatherTargetSource.ADMIN_BACKFILL, ADMIN_PRIORITY);
        }

        int created = 0;
        int known = 0;
        int skipped = 0;
        for (UserEntity user : activeUsers()) {
            WeatherTargetQueueResponse response = enqueueForRange(user.getId(), request.getStartTime(), request.getEndTime(), WeatherTargetSource.ADMIN_BACKFILL, ADMIN_PRIORITY);
            created += response.getTargetsCreated();
            known += response.getTargetsAlreadyKnown();
            skipped += response.getTargetsSkipped();
        }
        return WeatherTargetQueueResponse.builder()
                .targetsCreated(created)
                .targetsAlreadyKnown(known)
                .targetsSkipped(skipped)
                .build();
    }

    @Transactional
    public WeatherTargetQueueResponse discoverOngoingTargets() {
        if (!configurationService.isEnabled() || !configurationService.ongoingEnabled()) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }

        Instant now = Instant.now();
        int intervalMinutes = configurationService.ongoingIntervalMinutes();
        int created = 0;
        int known = 0;
        int skipped = 0;

        for (UserEntity user : activeUsers()) {
            Optional<WeatherSampleCandidate> candidate = latestActiveCandidate(user, now, intervalMinutes);
            if (candidate.isEmpty()) {
                skipped++;
                continue;
            }

            EnqueueResult result = enqueueCandidate(user, candidate.get());
            created += result.created ? 1 : 0;
            known += result.known ? 1 : 0;
            skipped += result.skipped ? 1 : 0;
        }

        return WeatherTargetQueueResponse.builder()
                .targetsCreated(created)
                .targetsAlreadyKnown(known)
                .targetsSkipped(skipped)
                .build();
    }

    public int fetchQueuedSamples() {
        if (!configurationService.isEnabled() || !configurationService.isConfigured()) {
            return 0;
        }

        long usedBefore = quotaService.requestsUsedToday();
        int dailyLimit = configurationService.dailyRequestLimit();
        int backfillAllowed = (int) Math.max(0, dailyLimit - configurationService.ongoingReserve() - usedBefore);
        int limit = (int) Math.max(0, dailyLimit - usedBefore);
        if (limit <= 0) {
            log.debug("Weather sample fetch skipped: daily quota exhausted");
            return 0;
        }

        List<WeatherSampleTargetClaim> targets = targetRepository.claimPendingTargetClaims(limit);
        int processed = 0;
        int backfillProcessed = 0;
        for (WeatherSampleTargetClaim target : targets) {
            if (target.source() != WeatherTargetSource.ONGOING && backfillProcessed >= backfillAllowed) {
                targetRepository.releaseForQuota(target.id());
                continue;
            }

            try {
                targetRepository.markAttemptStarted(target.id());
                if (target.source() != WeatherTargetSource.ONGOING) {
                    backfillProcessed++;
                }
                if (fetchAndStoreTarget(target)) {
                    processed++;
                }
            } catch (Exception e) {
                log.warn("Weather target {} failed for user {} at {}: {}",
                        target.id(), target.userId(), target.targetAt(), e.getMessage());
                targetRepository.markFailedOrRetry(target.id(), e.getMessage());
            }
        }
        return processed;
    }

    @Transactional
    public long resetStaleFailedTargetsForRetry() {
        if (!configurationService.isEnabled()
                || !configurationService.backfillEnabled()
                || !configurationService.failedTargetRetryEnabled()) {
            return 0;
        }

        Instant retryBefore = Instant.now().minus(Duration.ofHours(configurationService.failedTargetRetryCooldownHours()));
        return targetRepository.resetFailedTargetsForRetry(retryBefore);
    }

    @Transactional
    public long cleanupTargets(int completedRetentionDays, int failedRetentionDays) {
        Instant completedBefore = Instant.now().minus(Duration.ofDays(Math.max(1, completedRetentionDays)));
        Instant failedBefore = Instant.now().minus(Duration.ofDays(Math.max(1, failedRetentionDays)));
        return targetRepository.cleanupCompletedTargets(completedBefore, failedBefore);
    }

    public WeatherStatusResponse status() {
        long usedToday = quotaService.requestsUsedToday();
        long remainingToday = Math.max(0, configurationService.dailyRequestLimit() - usedToday);
        return WeatherStatusResponse.builder()
                .enabled(configurationService.isEnabled())
                .configured(configurationService.isConfigured())
                .provider(WeatherConfigurationService.PROVIDER_OPEN_METEO)
                .dailyRequestLimit(configurationService.dailyRequestLimit())
                .ongoingReserve(configurationService.ongoingReserve())
                .requestsUsedToday(usedToday)
                .requestsRemainingToday(remainingToday)
                .samples(sampleRepository.countSamples())
                .targetsByStatus(targetRepository.countByStatus())
                .oldestPendingTargetAt(targetRepository.oldestPendingTargetAt())
                .newestPendingTargetAt(targetRepository.newestPendingTargetAt())
                .build();
    }

    public WeatherTestResponse testProviderConnection() {
        return weatherClient.testConnection();
    }

    private WeatherTargetQueueResponse enqueueForRange(UUID userId, Instant startTime, Instant endTime,
                                                      WeatherTargetSource source, int priority) {
        UserEntity user = entityManager.find(UserEntity.class, userId);
        if (user == null) {
            throw new NotFoundException("User not found: " + userId);
        }

        int created = 0;
        int known = 0;
        int skipped = 0;

        List<TimelineStayEntity> stays = stayRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (TimelineStayEntity stay : stays) {
            for (WeatherSampleCandidate candidate : samplingPolicy.forStay(stay, source, priority)) {
                if (!isTargetInRange(candidate.targetAt(), startTime, endTime)) {
                    continue;
                }
                EnqueueResult result = enqueueCandidate(user, candidate);
                created += result.created ? 1 : 0;
                known += result.known ? 1 : 0;
                skipped += result.skipped ? 1 : 0;
            }
        }

        List<TimelineTripEntity> trips = tripRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (TimelineTripEntity trip : trips) {
            for (WeatherSampleCandidate candidate : tripCandidates(userId, trip, source, priority)) {
                if (!isTargetInRange(candidate.targetAt(), startTime, endTime)) {
                    continue;
                }
                EnqueueResult result = enqueueCandidate(user, candidate);
                created += result.created ? 1 : 0;
                known += result.known ? 1 : 0;
                skipped += result.skipped ? 1 : 0;
            }
        }

        return WeatherTargetQueueResponse.builder()
                .targetsCreated(created)
                .targetsAlreadyKnown(known)
                .targetsSkipped(skipped)
                .build();
    }

    private boolean isTargetInRange(Instant targetAt, Instant startTime, Instant endTime) {
        return targetAt != null && !targetAt.isBefore(startTime) && !targetAt.isAfter(endTime);
    }

    private List<WeatherSampleCandidate> tripCandidates(UUID userId, TimelineTripEntity trip, WeatherTargetSource source, int priority) {
        List<WeatherSampleCandidate> result = new ArrayList<>();
        for (Instant targetAt : samplingPolicy.sampleTimesForTrip(trip)) {
            double[] coordinates = findTripCoordinateAt(userId, trip, targetAt)
                    .orElseGet(() -> interpolateTripCoordinate(trip, targetAt));
            if (coordinates == null) {
                continue;
            }
            result.add(new WeatherSampleCandidate(coordinates[0], coordinates[1], targetAt, source, priority));
        }
        return result;
    }

    private EnqueueResult enqueueCandidate(UserEntity user, WeatherSampleCandidate candidate) {
        if (!isValidCoordinate(candidate.latitude(), candidate.longitude())) {
            return EnqueueResult.skippedResult();
        }

        double latitudeBucket = configurationService.bucketCoordinate(candidate.latitude());
        double longitudeBucket = configurationService.bucketCoordinate(candidate.longitude());
        Instant targetAt = samplingPolicy.truncateToHour(candidate.targetAt());

        if (sampleRepository.existsAtBucketHour(user.getId(), WeatherConfigurationService.PROVIDER_OPEN_METEO, latitudeBucket, longitudeBucket, targetAt)) {
            return EnqueueResult.knownResult();
        }

        boolean created = targetRepository.enqueueIfMissing(
                user,
                WeatherConfigurationService.PROVIDER_OPEN_METEO,
                candidate.latitude(),
                candidate.longitude(),
                latitudeBucket,
                longitudeBucket,
                targetAt,
                candidate.source(),
                candidate.priority()
        );

        return created ? EnqueueResult.createdResult() : EnqueueResult.knownResult();
    }

    private Optional<WeatherSampleCandidate> latestActiveCandidate(UserEntity user, Instant now, int intervalMinutes) {
        Optional<TimelineStayEntity> latestStay = stayRepository.find("user.id = ?1 order by timestamp desc", user.getId()).firstResultOptional();
        Optional<TimelineTripEntity> latestTrip = tripRepository.find("user.id = ?1 order by timestamp desc", user.getId()).firstResultOptional();

        TimelineStayEntity stay = latestStay.orElse(null);
        TimelineTripEntity trip = latestTrip.orElse(null);
        Instant stayEnd = stay != null ? stay.getTimestamp().plusSeconds(stay.getStayDuration()) : Instant.EPOCH;
        Instant tripEnd = trip != null ? trip.getTimestamp().plusSeconds(trip.getTripDuration()) : Instant.EPOCH;
        Duration activeWindow = Duration.ofMinutes(Math.max(60, intervalMinutes * 2L));

        if (stayEnd.isAfter(tripEnd) && stay != null && !stayEnd.isBefore(now.minus(activeWindow)) && stay.getLocation() != null) {
            Point point = stay.getLocation();
            return Optional.of(new WeatherSampleCandidate(
                    point.getY(),
                    point.getX(),
                    samplingPolicy.ongoingSampleTime(now, intervalMinutes),
                    WeatherTargetSource.ONGOING,
                    ONGOING_PRIORITY
            ));
        }

        if (trip != null && !tripEnd.isBefore(now.minus(activeWindow))) {
            double[] coordinates = findTripCoordinateAt(user.getId(), trip, now)
                    .orElseGet(() -> interpolateTripCoordinate(trip, now));
            if (coordinates != null) {
                return Optional.of(new WeatherSampleCandidate(
                        coordinates[0],
                        coordinates[1],
                        samplingPolicy.ongoingSampleTime(now, intervalMinutes),
                        WeatherTargetSource.ONGOING,
                        ONGOING_PRIORITY
                ));
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<double[]> findTripCoordinateAt(UUID userId, TimelineTripEntity trip, Instant targetAt) {
        Instant start = trip.getTimestamp();
        Instant end = trip.getTimestamp().plusSeconds(trip.getTripDuration());
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT ST_Y(coordinates) AS latitude, ST_X(coordinates) AS longitude
                FROM gps_points
                WHERE user_id = ?1
                  AND coordinates IS NOT NULL
                  AND timestamp >= ?2
                  AND timestamp <= ?3
                ORDER BY ABS(EXTRACT(EPOCH FROM timestamp) - ?4)
                LIMIT 1
                """)
                .setParameter(1, userId)
                .setParameter(2, start)
                .setParameter(3, end)
                .setParameter(4, targetAt.getEpochSecond())
                .getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        return Optional.of(new double[]{((Number) row[0]).doubleValue(), ((Number) row[1]).doubleValue()});
    }

    private double[] interpolateTripCoordinate(TimelineTripEntity trip, Instant targetAt) {
        if (trip == null || trip.getStartPoint() == null || trip.getEndPoint() == null || trip.getTripDuration() <= 0) {
            return null;
        }
        long elapsedSeconds = Math.max(0, Math.min(trip.getTripDuration(), Duration.between(trip.getTimestamp(), targetAt).toSeconds()));
        double ratio = elapsedSeconds / (double) trip.getTripDuration();
        double latitude = trip.getStartPoint().getY() + ((trip.getEndPoint().getY() - trip.getStartPoint().getY()) * ratio);
        double longitude = trip.getStartPoint().getX() + ((trip.getEndPoint().getX() - trip.getStartPoint().getX()) * ratio);
        return new double[]{latitude, longitude};
    }

    private boolean fetchAndStoreTarget(WeatherSampleTargetClaim target) {
        WeatherProviderSample providerSample = target.source() == WeatherTargetSource.ONGOING
                ? weatherClient.fetchCurrent(target.latitude(), target.longitude())
                : weatherClient.fetchHourly(target.latitude(), target.longitude(), target.targetAt());

        try {
            return QuarkusTransaction.requiringNew().call(() -> storeProviderSample(target, providerSample));
        } catch (Exception e) {
            Instant observedAt = samplingPolicy.truncateToHour(providerSample.getObservedAt());
            if (markSkippedIfSampleExists(target, observedAt)) {
                return true;
            }
            throw e;
        }
    }

    private boolean storeProviderSample(WeatherSampleTargetClaim target, WeatherProviderSample providerSample) {
        WeatherSampleTargetEntity targetEntity = targetRepository.findById(target.id());
        if (targetEntity == null) {
            return false;
        }

        Instant observedAt = samplingPolicy.truncateToHour(providerSample.getObservedAt());
        if (sampleRepository.existsAtBucketHour(
                target.userId(),
                target.provider(),
                target.latitudeBucket(),
                target.longitudeBucket(),
                observedAt)) {
            targetRepository.markSkipped(targetEntity, "Weather sample already exists");
            return true;
        }

        WeatherSampleEntity sample = WeatherSampleEntity.builder()
                .user(targetEntity.getUser())
                .provider(target.provider())
                .source(target.source())
                .requestedLatitude(providerSample.getRequestedLatitude())
                .requestedLongitude(providerSample.getRequestedLongitude())
                .providerLatitude(providerSample.getProviderLatitude())
                .providerLongitude(providerSample.getProviderLongitude())
                .latitudeBucket(target.latitudeBucket())
                .longitudeBucket(target.longitudeBucket())
                .observedAt(observedAt)
                .fetchedAt(Instant.now())
                .timezone(providerSample.getTimezone())
                .weatherCode(providerSample.getWeatherCode())
                .temperature(providerSample.getTemperature())
                .apparentTemperature(providerSample.getApparentTemperature())
                .humidity(providerSample.getHumidity())
                .precipitation(providerSample.getPrecipitation())
                .rain(providerSample.getRain())
                .snowfall(providerSample.getSnowfall())
                .cloudCover(providerSample.getCloudCover())
                .windSpeed(providerSample.getWindSpeed())
                .windGust(providerSample.getWindGust())
                .windDirection(providerSample.getWindDirection())
                .pressure(providerSample.getPressure())
                .rawData(providerSample.getRawData())
                .build();

        sampleRepository.persist(sample);
        targetRepository.markCompleted(targetEntity);
        entityManager.flush();
        return true;
    }

    private boolean markSkippedIfSampleExists(WeatherSampleTargetClaim target, Instant observedAt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            if (!sampleRepository.existsAtBucketHour(
                    target.userId(),
                    target.provider(),
                    target.latitudeBucket(),
                    target.longitudeBucket(),
                    observedAt)) {
                return false;
            }

            targetRepository.markSkipped(target.id(), "Weather sample already exists");
            return true;
        });
    }

    private List<UserEntity> activeUsers() {
        return UserEntity.find("isActive = true").list();
    }

    private boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    private Map<String, String> metricUnits() {
        return Map.ofEntries(
                Map.entry("temperature", "°C"),
                Map.entry("apparentTemperature", "°C"),
                Map.entry("humidity", "%"),
                Map.entry("precipitation", "mm"),
                Map.entry("rain", "mm"),
                Map.entry("snowfall", "cm"),
                Map.entry("cloudCover", "%"),
                Map.entry("windSpeed", "km/h"),
                Map.entry("windGust", "km/h"),
                Map.entry("windDirection", "°"),
                Map.entry("pressure", "hPa")
        );
    }

    private record EnqueueResult(boolean created, boolean known, boolean skipped) {
        static EnqueueResult createdResult() {
            return new EnqueueResult(true, false, false);
        }

        static EnqueueResult knownResult() {
            return new EnqueueResult(false, true, false);
        }

        static EnqueueResult skippedResult() {
            return new EnqueueResult(false, false, true);
        }
    }
}
