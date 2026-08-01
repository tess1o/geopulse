package org.github.tess1o.geopulse.weather.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.integration.dto.ExternalIntegrationHealthDto;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;
import org.github.tess1o.geopulse.integration.service.ExternalIntegrationHealthService;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.client.OpenMeteoWeatherClient;
import org.github.tess1o.geopulse.weather.client.WeatherProviderErrorKind;
import org.github.tess1o.geopulse.weather.client.WeatherProviderException;
import org.github.tess1o.geopulse.weather.dto.*;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherSampleTargetEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetClaim;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;
import org.locationtech.jts.geom.Point;

import javax.net.ssl.SSLHandshakeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@ApplicationScoped
@Slf4j
public class WeatherService {

    private static final int ONGOING_PRIORITY = 100;
    private static final int ADMIN_PRIORITY = 80;
    private static final int HISTORICAL_BACKFILL_PRIORITY = 70;
    private static final Duration HISTORICAL_BACKFILL_DELAY = Duration.ofHours(2);
    private static final ExternalIntegrationType WEATHER_INTEGRATION = ExternalIntegrationType.WEATHER;
    private static final String OPEN_METEO_PROVIDER_KEY = WeatherConfigurationService.PROVIDER_OPEN_METEO;
    private static final Duration INTERNAL_QUOTA_RESET_GRACE = Duration.ofMinutes(10);
    private static final Duration[] PROVIDER_UNAVAILABLE_BACKOFFS = {
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(60)
    };

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    WeatherSamplingPolicy samplingPolicy;

    @Inject
    WeatherQuotaService quotaService;

    @Inject
    ExternalIntegrationHealthService integrationHealthService;

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

    @ConfigProperty(name = "geopulse.weather.targets.in-progress-timeout-minutes", defaultValue = "60")
    int inProgressTimeoutMinutes;

    @ConfigProperty(name = "geopulse.weather.provider.ssl-handshake-retry-attempts", defaultValue = "2")
    int sslHandshakeRetryAttempts;

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

    @ActivateRequestContext
    public int fetchQueuedSamples() {
        if (!configurationService.isEnabled()) {
            log.info("Weather sample fetch skipped: weather integration is disabled");
            return 0;
        }

        if (!configurationService.isConfigured()) {
            Instant retryAt = recordConfigurationError("Weather provider URLs are not configured");
            log.info("Weather sample fetch skipped: weather provider is not configured; retryAt={}", retryAt);
            return 0;
        }

        long resetTargets = resetRecoverableTargetsForRetry();
        if (resetTargets > 0) {
            log.info("Weather sample fetch recovered {} stale/retryable targets", resetTargets);
        }

        long usedBefore = quotaService.requestsUsedToday();
        int dailyLimit = configurationService.dailyRequestLimit();
        int backfillAllowed = (int) Math.max(0, dailyLimit - configurationService.ongoingReserve() - usedBefore);
        int limit = (int) Math.max(0, dailyLimit - usedBefore);
        if (limit <= 0) {
            Instant retryAt = recordInternalQuotaExceeded("GeoPulse weather daily request limit reached");
            log.info("Weather sample fetch skipped: daily quota exhausted; requestsUsedToday={}, dailyLimit={}, retryAt={}",
                    usedBefore, dailyLimit, retryAt);
            return 0;
        }
        integrationHealthService.clearInternalQuotaIfRecovered(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY);

        Instant now = Instant.now();
        ExternalIntegrationHealthDto providerHealth = integrationHealthService.currentHealth(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY);
        if (integrationHealthService.isFetchBlocked(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY, now)) {
            log.info("Weather sample fetch skipped: {}", providerFetchBlockedReason(providerHealth, now));
            return 0;
        }

        long claimablePendingTargets = targetRepository.countClaimablePendingTargets(now);
        if (claimablePendingTargets <= 0) {
            log.info("Weather sample fetch skipped: zero claimable pending targets at {}", now);
            return 0;
        }

        long claimableBackfillTargets = targetRepository.countClaimablePendingBackfillTargets(now);
        if (backfillAllowed <= 0 && claimableBackfillTargets > 0 && claimableBackfillTargets >= claimablePendingTargets) {
            log.info("Weather sample fetch skipped: daily backfill reserve exhausted; requestsUsedToday={}, "
                            + "dailyLimit={}, ongoingReserve={}, claimablePendingTargets={}, claimableBackfillTargets={}",
                    usedBefore, dailyLimit, configurationService.ongoingReserve(), claimablePendingTargets, claimableBackfillTargets);
            return 0;
        }

        List<WeatherSampleTargetClaim> targets = targetRepository.claimPendingTargetClaims(limit);
        if (targets.isEmpty()) {
            log.info("Weather sample fetch skipped: claimed zero targets from {} claimable pending targets; "
                    + "another worker may have claimed them", claimablePendingTargets);
            return 0;
        }

        log.info("Weather sample fetch starting: claimedTargets={}, claimablePendingTargets={}, "
                        + "claimableBackfillTargets={}, requestsUsedToday={}, dailyLimit={}, backfillAllowed={}",
                targets.size(), claimablePendingTargets, claimableBackfillTargets, usedBefore, dailyLimit, backfillAllowed);

        int processed = 0;
        int backfillProcessed = 0;
        int backfillDeferredForReserve = 0;
        for (int i = 0; i < targets.size(); i++) {
            WeatherSampleTargetClaim target = targets.get(i);
            if (target.source() != WeatherTargetSource.ONGOING && backfillProcessed >= backfillAllowed) {
                targetRepository.releaseForQuota(target.id());
                backfillDeferredForReserve++;
                continue;
            }

            try {
                targetRepository.markAttemptStarted(target.id());
                if (target.source() != WeatherTargetSource.ONGOING) {
                    backfillProcessed++;
                }
                if (fetchAndStoreTargetWithSslRetry(target)) {
                    processed++;
                    integrationHealthService.recordSuccess(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY);
                }
            } catch (WeatherProviderException e) {
                ProviderFailureDecision decision = handleProviderFailure(target, e);
                if (decision.stopBatch()) {
                    releaseRemainingClaimedTargets(targets, i + 1, decision.retryAt(), decision.reason());
                    break;
                }
            } catch (Exception e) {
                log.error("Weather target {} failed for user {} at {}: {}",
                        target.id(), target.userId(), target.targetAt(), e.getMessage(), e);
                targetRepository.markFailedOrRetry(target.id(), e.getMessage());
            }
        }
        if (backfillDeferredForReserve > 0) {
            log.info("Weather sample fetch deferred {} backfill targets because the daily backfill reserve is exhausted; "
                            + "requestsUsedToday={}, dailyLimit={}, ongoingReserve={}, backfillAllowed={}",
                    backfillDeferredForReserve, usedBefore, dailyLimit, configurationService.ongoingReserve(), backfillAllowed);
        }
        log.info("Weather sample fetch completed: processed={}, claimedTargets={}, deferredBackfillTargets={}",
                processed, targets.size(), backfillDeferredForReserve);
        return processed;
    }

    @Transactional
    public long resetStaleFailedTargetsForRetry() {
        return resetRecoverableTargetsForRetry();
    }

    @Transactional
    public long resetRecoverableTargetsForRetry() {
        if (!configurationService.isEnabled()) {
            return 0;
        }

        long resetTargets = 0;
        Instant staleLockBefore = Instant.now().minus(Duration.ofMinutes(Math.max(1, inProgressTimeoutMinutes)));
        resetTargets += targetRepository.resetStaleInProgressTargets(staleLockBefore);

        if (!configurationService.failedTargetRetryEnabled()) {
            return resetTargets;
        }

        Instant retryBefore = Instant.now().minus(Duration.ofHours(configurationService.failedTargetRetryCooldownHours()));
        resetTargets += targetRepository.resetFailedTargetsForRetry(retryBefore);
        return resetTargets;
    }

    @Transactional
    public long cleanupTargets(int completedRetentionDays, int failedRetentionDays) {
        Instant completedBefore = Instant.now().minus(Duration.ofDays(Math.max(1, completedRetentionDays)));
        Instant failedBefore = Instant.now().minus(Duration.ofDays(Math.max(1, failedRetentionDays)));
        return targetRepository.cleanupCompletedTargets(completedBefore, failedBefore);
    }

    public WeatherStatusResponse status() {
        boolean enabled = configurationService.isEnabled();
        boolean configured = configurationService.isConfigured();
        long usedToday = quotaService.requestsUsedToday();
        int dailyLimit = configurationService.dailyRequestLimit();
        int ongoingReserve = configurationService.ongoingReserve();
        long remainingToday = Math.max(0, dailyLimit - usedToday);
        Instant now = Instant.now();
        long claimablePendingTargets = targetRepository.countClaimablePendingTargets(now);
        long claimableBackfillTargets = targetRepository.countClaimablePendingBackfillTargets(now);
        ExternalIntegrationHealthDto providerHealth = integrationHealthService.currentHealth(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY);
        return WeatherStatusResponse.builder()
                .enabled(enabled)
                .configured(configured)
                .provider(WeatherConfigurationService.PROVIDER_OPEN_METEO)
                .dailyRequestLimit(dailyLimit)
                .ongoingReserve(ongoingReserve)
                .requestsUsedToday(usedToday)
                .requestsRemainingToday(remainingToday)
                .samples(sampleRepository.countSamples())
                .targetsByStatus(targetRepository.countByStatus())
                .claimablePendingTargets(claimablePendingTargets)
                .fetchBlockedReason(fetchBlockedReason(
                        enabled,
                        configured,
                        usedToday,
                        dailyLimit,
                        ongoingReserve,
                        claimablePendingTargets,
                        claimableBackfillTargets,
                        providerHealth,
                        now))
                .oldestPendingTargetAt(targetRepository.oldestPendingTargetAt())
                .newestPendingTargetAt(targetRepository.newestPendingTargetAt())
                .providerHealth(providerHealth)
                .build();
    }

    public WeatherTestResponse testProviderConnection() {
        WeatherTestResponse result = testProviderConnectionWithSslRetry();
        if (result.isSuccess()) {
            integrationHealthService.recordSuccess(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY);
            log.info("Weather provider connection test succeeded for {} using forecastUrl={} and archiveUrl={}",
                    OPEN_METEO_PROVIDER_KEY,
                    result.getForecast() == null ? null : result.getForecast().getUrl(),
                    result.getArchive() == null ? null : result.getArchive().getUrl());
        } else {
            log.error("Weather provider connection test failed for {}: message={}, forecast={}, archive={}",
                    OPEN_METEO_PROVIDER_KEY,
                    result.getMessage(),
                    endpointTestSummary(result.getForecast()),
                    endpointTestSummary(result.getArchive()));
        }
        return result;
    }

    public boolean probeProviderHealth() {
        if (!configurationService.isEnabled()) {
            return false;
        }
        if (!configurationService.isConfigured()) {
            recordConfigurationError("Weather provider URLs are not configured");
            return false;
        }

        long usedBefore = quotaService.requestsUsedToday();
        int dailyLimit = configurationService.dailyRequestLimit();
        if (dailyLimit <= 0 || usedBefore >= dailyLimit) {
            recordInternalQuotaExceeded("GeoPulse weather daily request limit reached");
            return false;
        }
        integrationHealthService.clearInternalQuotaIfRecovered(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY);

        Instant now = Instant.now();
        if (!integrationHealthService.isProbeDue(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY, now)) {
            return false;
        }

        try {
            WeatherTestResponse result = testProviderConnectionWithSslRetry();
            if (!result.isSuccess()) {
                WeatherProviderException failure = providerExceptionFromTestFailure(result);
                recordProviderFailure(failure);
                log.error("Weather provider health probe failed for {}: message={}, forecast={}, archive={}",
                        OPEN_METEO_PROVIDER_KEY,
                        result.getMessage(),
                        endpointTestSummary(result.getForecast()),
                        endpointTestSummary(result.getArchive()));
                return false;
            }
            integrationHealthService.recordSuccess(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY);
            log.info("Weather provider health probe succeeded for {} using forecast and archive endpoints", OPEN_METEO_PROVIDER_KEY);
            return true;
        } catch (WeatherProviderException e) {
            recordProviderFailure(e);
            log.error("Weather provider health probe failed for {}: {}", OPEN_METEO_PROVIDER_KEY, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            recordProviderFailure(new WeatherProviderException(
                    WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                    "Open-Meteo health probe failed: " + e.getMessage(),
                    e
            ));
            log.error("Weather provider health probe failed for {}: {}", OPEN_METEO_PROVIDER_KEY, e.getMessage(), e);
            return false;
        }
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

    private ProviderFailureDecision handleProviderFailure(WeatherSampleTargetClaim target, WeatherProviderException e) {
        long targetId = target.id();
        if (e.getKind() == WeatherProviderErrorKind.NO_DATA) {
            targetRepository.markSkipped(targetId, "Weather provider has no data: " + e.getMessage());
            return ProviderFailureDecision.continueBatch();
        }

        if (e.getKind() == WeatherProviderErrorKind.INVALID_RESPONSE) {
            targetRepository.markFailedOrRetry(targetId, e.getMessage());
            return ProviderFailureDecision.continueBatch();
        }

        Instant retryAt = recordProviderFailure(e);
        targetRepository.releaseUntil(targetId, retryAt, e.getMessage());
        log.error("Weather sample fetch stopping after provider failure: targetId={}, userId={}, source={}, "
                        + "targetAt={}, provider={}, kind={}, statusCode={}, retryAt={}, message={}",
                target.id(),
                target.userId(),
                target.source(),
                target.targetAt(),
                target.provider(),
                e.getKind(),
                e.getStatusCode(),
                retryAt,
                e.getMessage(),
                e);
        return ProviderFailureDecision.stopBatch(retryAt, e.getMessage());
    }

    private void releaseRemainingClaimedTargets(List<WeatherSampleTargetClaim> targets, int startIndex, Instant retryAt, String reason) {
        for (int i = startIndex; i < targets.size(); i++) {
            targetRepository.releaseUntil(targets.get(i).id(), retryAt, reason);
        }
    }

    private Instant recordProviderFailure(WeatherProviderException e) {
        return switch (e.getKind()) {
            case QUOTA_EXCEEDED -> recordProviderQuotaExceeded(e);
            case CONFIG_ERROR -> recordConfigurationError(e.getMessage());
            case PROVIDER_UNAVAILABLE -> recordProviderUnavailable(e);
            case NO_DATA, INVALID_RESPONSE -> recordProviderUnavailable(new WeatherProviderException(
                    WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                    e.getStatusCode(),
                    e.getRetryAfter(),
                    e.getMessage(),
                    e
            ));
        };
    }

    private Instant recordProviderQuotaExceeded(WeatherProviderException e) {
        Instant retryAt = e.getRetryAfter() != null && e.getRetryAfter().isAfter(Instant.now())
                ? e.getRetryAfter()
                : nextUtcDayStart().plus(INTERNAL_QUOTA_RESET_GRACE);
        return integrationHealthService.recordQuotaExceeded(
                WEATHER_INTEGRATION,
                OPEN_METEO_PROVIDER_KEY,
                ExternalIntegrationHealthStatus.PROVIDER_QUOTA_EXCEEDED,
                errorCode(e),
                e.getMessage(),
                retryAt,
                retryAt
        );
    }

    private Instant recordInternalQuotaExceeded(String message) {
        Instant retryAt = nextUtcDayStart().plus(INTERNAL_QUOTA_RESET_GRACE);
        return integrationHealthService.recordQuotaExceeded(
                WEATHER_INTEGRATION,
                OPEN_METEO_PROVIDER_KEY,
                ExternalIntegrationHealthStatus.INTERNAL_QUOTA_EXCEEDED,
                "INTERNAL_QUOTA",
                message,
                retryAt,
                retryAt
        );
    }

    private Instant recordProviderUnavailable(WeatherProviderException e) {
        Duration backoff = providerUnavailableBackoff();
        Instant retryAt = Instant.now().plus(backoff);
        return integrationHealthService.recordFailure(
                WEATHER_INTEGRATION,
                OPEN_METEO_PROVIDER_KEY,
                ExternalIntegrationHealthStatus.PROVIDER_UNAVAILABLE,
                errorCode(e),
                e.getMessage(),
                retryAt,
                retryAt
        );
    }

    private Instant recordConfigurationError(String message) {
        Instant retryAt = Instant.now().plus(Duration.ofMinutes(15));
        return integrationHealthService.recordFailure(
                WEATHER_INTEGRATION,
                OPEN_METEO_PROVIDER_KEY,
                ExternalIntegrationHealthStatus.CONFIG_ERROR,
                "CONFIG_ERROR",
                message,
                retryAt,
                retryAt
        );
    }

    private Duration providerUnavailableBackoff() {
        int failureCount = integrationHealthService.currentHealth(WEATHER_INTEGRATION, OPEN_METEO_PROVIDER_KEY)
                .getFailureCount();
        int index = Math.min(failureCount, PROVIDER_UNAVAILABLE_BACKOFFS.length - 1);
        return PROVIDER_UNAVAILABLE_BACKOFFS[index];
    }

    private String errorCode(WeatherProviderException e) {
        if (e.getStatusCode() > 0) {
            return "HTTP_" + e.getStatusCode();
        }
        return e.getKind().name();
    }

    private WeatherProviderException providerExceptionFromTestFailure(WeatherTestResponse result) {
        int statusCode = result == null ? 0 : result.getStatusCode();
        WeatherProviderErrorKind kind = statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 404
                ? WeatherProviderErrorKind.CONFIG_ERROR
                : WeatherProviderErrorKind.PROVIDER_UNAVAILABLE;
        String message = result == null || result.getMessage() == null || result.getMessage().isBlank()
                ? "Open-Meteo health probe failed"
                : result.getMessage();
        return new WeatherProviderException(kind, statusCode, null, message);
    }

    private WeatherTestResponse testProviderConnectionWithSslRetry() {
        int maxAttempts = Math.max(1, 1 + Math.max(0, sslHandshakeRetryAttempts));
        WeatherTestResponse lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            WeatherTestResponse result = weatherClient.testConnection();
            lastResult = result;
            if (!hasSslHandshakeFailure(result) || attempt >= maxAttempts) {
                return result;
            }

            log.info("Weather provider connection test hit SSL handshake failure; retrying before health/circuit flow: "
                            + "nextAttempt={}/{}, message={}, forecast={}, archive={}",
                    attempt + 1,
                    maxAttempts,
                    result.getMessage(),
                    endpointTestSummary(result.getForecast()),
                    endpointTestSummary(result.getArchive()));
        }

        return lastResult;
    }

    private boolean hasSslHandshakeFailure(WeatherTestResponse result) {
        return result != null
                && !result.isSuccess()
                && (containsSslHandshakeFailure(result.getMessage())
                || hasSslHandshakeFailure(result.getForecast())
                || hasSslHandshakeFailure(result.getArchive()));
    }

    private boolean hasSslHandshakeFailure(WeatherEndpointTestResponse result) {
        return result != null
                && !result.isSuccess()
                && containsSslHandshakeFailure(result.getMessage());
    }

    private boolean containsSslHandshakeFailure(String value) {
        return value != null && value.contains(SSLHandshakeException.class.getSimpleName());
    }

    private String fetchBlockedReason(boolean enabled,
                                      boolean configured,
                                      long requestsUsedToday,
                                      int dailyLimit,
                                      int ongoingReserve,
                                      long claimablePendingTargets,
                                      long claimableBackfillTargets,
                                      ExternalIntegrationHealthDto providerHealth,
                                      Instant now) {
        if (!enabled) {
            return "Weather integration is disabled";
        }
        if (!configured) {
            return "Weather provider URLs are not configured";
        }
        if (dailyLimit <= 0 || requestsUsedToday >= dailyLimit) {
            return "Daily weather request limit exhausted";
        }
        if (providerHealthBlocksFetch(providerHealth, now)) {
            return providerFetchBlockedReason(providerHealth, now);
        }
        if (claimablePendingTargets <= 0) {
            return "No claimable pending weather targets";
        }

        long backfillAllowed = Math.max(0, dailyLimit - ongoingReserve - requestsUsedToday);
        if (backfillAllowed <= 0 && claimableBackfillTargets > 0 && claimableBackfillTargets >= claimablePendingTargets) {
            return "Daily backfill reserve exhausted";
        }
        return null;
    }

    private boolean providerHealthBlocksFetch(ExternalIntegrationHealthDto providerHealth, Instant now) {
        return providerHealth != null
                && providerHealth.getStatus() != null
                && providerHealth.getStatus() != ExternalIntegrationHealthStatus.HEALTHY
                && providerHealth.getCircuitOpenUntil() != null
                && providerHealth.getCircuitOpenUntil().isAfter(now);
    }

    private String providerFetchBlockedReason(ExternalIntegrationHealthDto providerHealth, Instant now) {
        if (providerHealth == null) {
            return "Provider health blocks fetch; health details are unavailable";
        }
        return "Provider health blocks fetch: status=" + providerHealth.getStatus()
                + ", circuitOpenUntil=" + providerHealth.getCircuitOpenUntil()
                + ", nextProbeAt=" + providerHealth.getNextProbeAt()
                + ", failureCount=" + providerHealth.getFailureCount()
                + ", now=" + now
                + ", lastErrorCode=" + providerHealth.getLastErrorCode()
                + ", lastErrorMessage=" + providerHealth.getLastErrorMessage();
    }

    private String endpointTestSummary(WeatherEndpointTestResponse endpoint) {
        if (endpoint == null) {
            return "not tested";
        }
        return "{success=" + endpoint.isSuccess()
                + ", statusCode=" + endpoint.getStatusCode()
                + ", url=" + endpoint.getUrl()
                + ", message=" + endpoint.getMessage()
                + "}";
    }

    private Instant nextUtcDayStart() {
        return LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    private boolean fetchAndStoreTargetWithSslRetry(WeatherSampleTargetClaim target) {
        int maxAttempts = Math.max(1, 1 + Math.max(0, sslHandshakeRetryAttempts));
        WeatherProviderException lastSslFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return fetchAndStoreTarget(target);
            } catch (WeatherProviderException e) {
                if (!shouldRetrySslHandshakeFailure(e, attempt, maxAttempts)) {
                    throw e;
                }

                lastSslFailure = e;
                log.info("Weather provider SSL handshake failure while fetching target; retrying before circuit flow: "
                                + "targetId={}, userId={}, source={}, targetAt={}, provider={}, nextAttempt={}/{}, rootCause={}",
                        target.id(),
                        target.userId(),
                        target.source(),
                        target.targetAt(),
                        target.provider(),
                        attempt + 1,
                        maxAttempts,
                        rootCauseMessage(e),
                        e);
            }
        }

        throw lastSslFailure;
    }

    private boolean shouldRetrySslHandshakeFailure(WeatherProviderException e, int attempt, int maxAttempts) {
        return e.getKind() == WeatherProviderErrorKind.PROVIDER_UNAVAILABLE
                && attempt < maxAttempts
                && hasCause(e, SSLHandshakeException.class);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        return root.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
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

    private record ProviderFailureDecision(boolean stopBatch, Instant retryAt, String reason) {
        static ProviderFailureDecision continueBatch() {
            return new ProviderFailureDecision(false, null, null);
        }

        static ProviderFailureDecision stopBatch(Instant retryAt, String reason) {
            return new ProviderFailureDecision(true, retryAt, reason);
        }
    }
}
