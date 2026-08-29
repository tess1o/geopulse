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
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.client.WeatherProviderClient;
import org.github.tess1o.geopulse.weather.client.WeatherProviderErrorKind;
import org.github.tess1o.geopulse.weather.client.WeatherProviderException;
import org.github.tess1o.geopulse.weather.client.WeatherProviderRegistry;
import org.github.tess1o.geopulse.weather.dto.*;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherSampleTargetEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetClaim;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherBackfillReconciliation;
import org.github.tess1o.geopulse.weather.repository.WeatherBackfillReconciliationRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherTargetBatchRow;
import org.locationtech.jts.geom.Point;

import javax.net.ssl.SSLHandshakeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

@ApplicationScoped
@Slf4j
public class WeatherService {

    private static final int ONGOING_PRIORITY = 100;
    private static final int HISTORICAL_BACKFILL_PRIORITY = 70;
    private static final Duration HISTORICAL_RECONCILIATION_CHUNK = Duration.ofDays(90);
    private static final ExternalIntegrationType WEATHER_INTEGRATION = ExternalIntegrationType.WEATHER;
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
    WeatherBackfillReconciliationRepository backfillReconciliationRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    WeatherProviderRegistry providerRegistry;

    @Inject
    EntityManager entityManager;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

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
                .provider(configurationService.primaryProvider())
                .attributionUrl(configurationService.attributionUrl(configurationService.primaryProvider()))
                .units(metricUnits())
                .samples(samples)
                .build();
    }

    public WeatherIntegrationStatusResponse integrationStatus() {
        String provider = configurationService.primaryProvider();
        return WeatherIntegrationStatusResponse.builder()
                .enabled(configurationService.isEnabled())
                .configured(configurationService.isConfigured())
                .provider(provider)
                .attributionUrl(configurationService.attributionUrl(provider))
                .build();
    }

    /**
     * Marks every active user's complete timeline as needing historical reconciliation.
     * Used only when a setting changes the definition of weather coverage. The new transaction
     * is required because this method is called from an {@code AFTER_SUCCESS} observer, where
     * the producer transaction is complete but can still be associated with the callback thread.
     *
     * @return whether work was queued or why it was skipped
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public WeatherReconciliationQueueStatus queueFullHistoricalBackfill() {
        if (!configurationService.isEnabled()) {
            return WeatherReconciliationQueueStatus.WEATHER_DISABLED;
        }
        if (!configurationService.backfillEnabled()) {
            return WeatherReconciliationQueueStatus.BACKFILL_DISABLED;
        }

        backfillReconciliationRepository.enqueueAllActiveUsers(Instant.now());
        return WeatherReconciliationQueueStatus.QUEUED;
    }

    /**
     * Durably records the exact timeline range that may need historical weather targets.
     * Repeated ranges for a user are coalesced by the repository. This transaction commits
     * after the timeline/import transaction and before asynchronous reconciliation is submitted.
     *
     * @return whether work was queued or why it was skipped
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public WeatherReconciliationQueueStatus queueHistoricalBackfill(
            UUID userId,
            Instant startTime,
            Instant endTime) {
        if (!configurationService.isEnabled()) {
            return WeatherReconciliationQueueStatus.WEATHER_DISABLED;
        }
        if (!configurationService.backfillEnabled()) {
            return WeatherReconciliationQueueStatus.BACKFILL_DISABLED;
        }
        if (userId == null || startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            return WeatherReconciliationQueueStatus.INVALID_RANGE;
        }

        backfillReconciliationRepository.enqueue(userId, startTime, endTime);
        return WeatherReconciliationQueueStatus.QUEUED;
    }

    /**
     * Reconciles at most {@code maxChunks} persisted ranges. Each chunk covers at most 90
     * days and runs in its own transaction, so memory and transaction lifetime are bounded.
     * Eligible chunks are processed immediately; pacing is controlled only by provider
     * availability and quota in the fetch phase.
     */
    @ActivateRequestContext
    public WeatherBackfillRunResult processPendingHistoricalBackfillChunks(int maxChunks) {
        if (!configurationService.isEnabled() || !configurationService.backfillEnabled()) {
            return new WeatherBackfillRunResult(0, 0, 0, 1, 0);
        }

        int chunksProcessed = 0;
        int created = 0;
        int known = 0;
        int skipped = 0;
        int chunkLimit = Math.max(1, maxChunks);
        for (int i = 0; i < chunkLimit; i++) {
            long chunkStart = metricsStart();
            String chunkResult = "success";
            WeatherTargetQueueResponse response = QuarkusTransaction.requiringNew().call(this::processNextHistoricalBackfillChunk);
            if (response == null) {
                recordWeatherTimer("geopulse.weather.reconciliation.chunk.duration", chunkStart,
                        "historical_backfill", "empty");
                break;
            }
            chunksProcessed++;
            created += response.getTargetsCreated();
            known += response.getTargetsAlreadyKnown();
            skipped += response.getTargetsSkipped();
            countWeatherTargets(WeatherTargetSource.HISTORICAL_BACKFILL, "created", response.getTargetsCreated());
            countWeatherTargets(WeatherTargetSource.HISTORICAL_BACKFILL, "known", response.getTargetsAlreadyKnown());
            countWeatherTargets(WeatherTargetSource.HISTORICAL_BACKFILL, "skipped", response.getTargetsSkipped());
            recordWeatherTimer("geopulse.weather.reconciliation.chunk.duration", chunkStart,
                    "historical_backfill", chunkResult);
            countWeatherChunks("historical_backfill", "success", 1);
        }

        long pendingUserRanges = QuarkusTransaction.requiringNew()
                .call(backfillReconciliationRepository::countPendingUserRanges);
        return new WeatherBackfillRunResult(
                chunksProcessed,
                created,
                known,
                skipped,
                pendingUserRanges
        );
    }

    private WeatherTargetQueueResponse processNextHistoricalBackfillChunk() {
        long stageStart = metricsStart();
        WeatherBackfillReconciliation reconciliation = backfillReconciliationRepository.claimNext(
                Instant.now(),
                HISTORICAL_RECONCILIATION_CHUNK
        );
        recordWeatherStage(stageStart, "claim_chunk", "historical_backfill", reconciliation == null ? "empty" : "success");
        if (reconciliation == null) {
            return null;
        }

        Optional<WeatherBackfillReconciliationRepository.TimelineWeatherBounds> bounds =
                backfillReconciliationRepository.findTimelineBounds(reconciliation.userId());
        if (bounds.isEmpty()) {
            backfillReconciliationRepository.delete(reconciliation.userId());
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }
        reconciliation = backfillReconciliationRepository.clampClaimedRange(
                reconciliation, bounds.get(), HISTORICAL_RECONCILIATION_CHUNK);
        if (reconciliation == null) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }

        WeatherTargetQueueResponse response = enqueueForChunk(
                reconciliation.userId(),
                reconciliation.chunkStart(),
                reconciliation.chunkEnd(),
                WeatherTargetSource.HISTORICAL_BACKFILL,
                HISTORICAL_BACKFILL_PRIORITY
        );
        backfillReconciliationRepository.completeChunk(reconciliation);
        entityManager.clear();
        return response;
    }

    @Transactional
    public int queueAdminBackfill(WeatherBackfillRequest request) {
        if (request == null || request.getStartTime() == null || request.getEndTime() == null
                || !request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        if (!configurationService.isEnabled() || !configurationService.backfillEnabled()) {
            return 0;
        }

        if (request.getUserId() != null) {
            if (entityManager.find(UserEntity.class, request.getUserId()) == null) {
                throw new NotFoundException("User not found: " + request.getUserId());
            }
            return queueHistoricalBackfill(request.getUserId(), request.getStartTime(), request.getEndTime())
                    == WeatherReconciliationQueueStatus.QUEUED ? 1 : 0;
        }

        int queued = 0;
        for (UserEntity user : activeUsers()) {
            if (queueHistoricalBackfill(user.getId(), request.getStartTime(), request.getEndTime())
                    == WeatherReconciliationQueueStatus.QUEUED) {
                queued++;
            }
        }
        return queued;
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
            long stageStart = metricsStart();
            Optional<WeatherSampleCandidate> candidate = latestActiveCandidate(user, now, intervalMinutes);
            recordWeatherStage(stageStart, "ongoing_candidate", WeatherTargetSource.ONGOING.name(), candidate.isPresent() ? "success" : "empty");
            if (candidate.isEmpty()) {
                skipped++;
                continue;
            }

            stageStart = metricsStart();
            EnqueueResult result = enqueueCandidate(user, candidate.get());
            recordWeatherStage(stageStart, "enqueue_targets", WeatherTargetSource.ONGOING.name(), result.created ? "created" : result.known ? "known" : "skipped");
            created += result.created ? 1 : 0;
            known += result.known ? 1 : 0;
            skipped += result.skipped ? 1 : 0;
        }
        countWeatherTargets(WeatherTargetSource.ONGOING, "created", created);
        countWeatherTargets(WeatherTargetSource.ONGOING, "known", known);
        countWeatherTargets(WeatherTargetSource.ONGOING, "skipped", skipped);

        return WeatherTargetQueueResponse.builder()
                .targetsCreated(created)
                .targetsAlreadyKnown(known)
                .targetsSkipped(skipped)
                .build();
    }

    @ActivateRequestContext
    public int fetchQueuedSamples() {
        int processed = 0;
        while (true) {
            WeatherFetchBatchResult result = fetchNextQueuedSampleGroup();
            processed += result.processedTargets();
            if (!result.workClaimed() || result.blockedReason() != null) {
                return processed;
            }
        }
    }

    @ActivateRequestContext
    public WeatherFetchBatchResult fetchNextQueuedSampleGroup() {
        if (!configurationService.isEnabled()) {
            return WeatherFetchBatchResult.blocked("Weather integration is disabled");
        }
        if (!targetRepository.hasPendingTargets()) {
            return WeatherFetchBatchResult.empty();
        }
        if (!configurationService.isConfigured()) {
            recordConfigurationError(primaryProviderKey(), "Primary weather provider is not configured");
            return WeatherFetchBatchResult.blocked("Primary weather provider is not configured");
        }

        List<String> providerOrder = configurationService.providerOrder(primaryProviderKey());
        if (providerOrder.isEmpty()) {
            recordConfigurationError(primaryProviderKey(), "No enabled and configured weather providers are available");
            return WeatherFetchBatchResult.blocked("No enabled and configured weather providers are available");
        }
        Instant now = Instant.now();
        providerOrder.forEach(provider -> integrationHealthService.clearInternalQuotaIfRecovered(WEATHER_INTEGRATION, provider));
        if (allProvidersBlocked(providerOrder, now)) {
            return WeatherFetchBatchResult.blocked(allProvidersBlockedReason(providerOrder, now));
        }

        List<WeatherSampleTargetClaim> targets = targetRepository.claimNextTargetGroup(24);
        if (targets.isEmpty()) {
            return WeatherFetchBatchResult.empty();
        }
        countWeatherTargets(null, "claimed", targets.size());
        WeatherTargetSource requestSource = targets.stream().anyMatch(target -> target.source() == WeatherTargetSource.ONGOING)
                ? WeatherTargetSource.ONGOING
                : targets.getFirst().source();
        targets.forEach(target -> targetRepository.markAttemptStarted(target.id()));

        long startedAt = metricsStart();
        try {
            ProviderFetchBatchResult fetchResult = fetchProviderSamples(targets, requestSource);
            int processed = 0;
            for (WeatherSampleTargetClaim target : targets) {
                Instant targetHour = samplingPolicy.truncateToHour(target.targetAt());
                WeatherProviderSample sample = fetchResult.samples().get(targetHour);
                if (sample == null) {
                    targetRepository.releaseImmediately(target.id(),
                            "Provider response did not include this hour; retrying as the next batch anchor");
                    continue;
                }
                if (requiringNew(() -> storeProviderSample(target,
                        new ProviderFetchResult(fetchResult.providerKey(), sample)))) {
                    processed++;
                    countWeatherTargets(target.source(), "processed", 1);
                }
            }
            recordWeatherStage(startedAt, "provider_fetch_group", requestSource.name(), "success");
            return new WeatherFetchBatchResult(true, processed, null);
        } catch (InternalQuotaExceededException e) {
            Instant retryAt = recordInternalQuotaExceeded(e.getMessage());
            releaseRemainingClaimedTargets(targets, 0, retryAt, e.getMessage());
            recordWeatherStage(startedAt, "provider_fetch_group", requestSource.name(), "quota");
            return new WeatherFetchBatchResult(true, 0, e.getMessage());
        } catch (WeatherProviderException e) {
            recordWeatherStage(startedAt, "provider_fetch_group", requestSource.name(), e.getKind().name());
            if (e.getKind() == WeatherProviderErrorKind.NO_DATA) {
                targets.forEach(target -> targetRepository.markSkipped(target.id(),
                        "Weather provider has no data: " + e.getMessage()));
                return new WeatherFetchBatchResult(true, 0, null);
            }
            if (e.getKind() == WeatherProviderErrorKind.INVALID_RESPONSE) {
                targets.forEach(target -> targetRepository.markFailedOrRetry(target.id(), e.getMessage()));
                return new WeatherFetchBatchResult(true, 0, null);
            }
            String providerKey = providerKey(e, targets.getFirst().provider());
            Instant retryAt = recordProviderFailure(providerKey, e);
            releaseRemainingClaimedTargets(targets, 0, retryAt, e.getMessage());
            return new WeatherFetchBatchResult(true, 0, e.getMessage());
        } catch (Exception e) {
            recordWeatherStage(startedAt, "provider_fetch_group", requestSource.name(), "error");
            targets.forEach(target -> targetRepository.markFailedOrRetry(target.id(), e.getMessage()));
            log.error("Weather provider batch failed for {} targets: {}", targets.size(), e.getMessage(), e);
            return new WeatherFetchBatchResult(true, 0, null);
        }
    }

    public boolean providersAvailableForFetch(Instant now) {
        if (!configurationService.isEnabled() || !configurationService.isConfigured()) {
            return false;
        }
        List<String> providers = configurationService.providerOrder(primaryProviderKey());
        return !providers.isEmpty() && !allProvidersBlocked(providers, now == null ? Instant.now() : now);
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
        Instant staleLockBefore = Instant.now().minus(Duration.ofMinutes(configurationService.inProgressTargetTimeoutMinutes()));
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
        long deletedTargets = targetRepository.cleanupCompletedTargets(completedBefore, failedBefore);
        long deletedUsageRows = quotaService.cleanupOldUsage(14);
        return deletedTargets + deletedUsageRows;
    }

    @Transactional
    public WeatherStatusResponse status() {
        boolean enabled = configurationService.isEnabled();
        boolean configured = configurationService.isConfigured();
        long usedToday = quotaService.requestsUsedToday();
        int dailyLimit = configurationService.dailyRequestLimit();
        int ongoingReserve = configurationService.ongoingReserve();
        long remainingToday = Math.max(0, dailyLimit - usedToday);
        Instant now = Instant.now();
        long claimablePendingTargets = targetRepository.countClaimablePendingTargets(now);
        String primaryProvider = primaryProviderKey();
        ExternalIntegrationHealthDto providerHealth = integrationHealthService.currentHealth(WEATHER_INTEGRATION, primaryProvider);
        return WeatherStatusResponse.builder()
                .enabled(enabled)
                .configured(configured)
                .provider(primaryProvider)
                .dailyRequestLimit(dailyLimit)
                .ongoingReserve(ongoingReserve)
                .requestsUsedToday(usedToday)
                .requestsRemainingToday(remainingToday)
                .targetsByStatus(targetRepository.countByStatus())
                .claimablePendingTargets(claimablePendingTargets)
                .oldestPendingTargetAt(targetRepository.oldestPendingTargetAt())
                .newestPendingTargetAt(targetRepository.newestPendingTargetAt())
                .reconciliation(reconciliationStatus(now))
                .providerHealth(providerHealth)
                .build();
    }

    @Transactional
    public WeatherReconciliationStatus reconciliationStatus(Instant now) {
        Instant eligibleThrough = now == null ? Instant.now() : now;
        WeatherBackfillReconciliationRepository.ReconciliationSummary summary =
                backfillReconciliationRepository.summary();
        return WeatherReconciliationStatus.builder()
                .pendingUserRanges(summary.pendingUserRanges())
                .eligibleUserRanges(summary.pendingUserRanges())
                .oldestRangeStart(summary.oldestRangeStart())
                .oldestCursorAt(summary.oldestCursorAt())
                .newestRangeEnd(summary.newestRangeEnd())
                .eligibleThrough(eligibleThrough)
                .build();
    }

    public synchronized WeatherTestResponse testProviderConnection() {
        String providerKey = primaryProviderKey();
        WeatherTestResponse result = testProviderConnectionWithSslRetry();
        if (result.isSuccess()) {
            integrationHealthService.recordSuccess(WEATHER_INTEGRATION, providerKey);
            log.info("Weather provider connection test succeeded for {} using forecastUrl={} and archiveUrl={}",
                    providerKey,
                    result.getForecast() == null ? null : result.getForecast().getUrl(),
                    result.getArchive() == null ? null : result.getArchive().getUrl());
        } else {
            if (result.getStatusCode() == 429
                    && result.getMessage() != null
                    && result.getMessage().contains("Daily weather request limit exhausted")) {
                recordInternalQuotaExceeded(result.getMessage());
            }
            log.error("Weather provider connection test failed for {}: message={}, forecast={}, archive={}",
                    providerKey,
                    result.getMessage(),
                    endpointTestSummary(result.getForecast()),
                    endpointTestSummary(result.getArchive()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private WeatherTargetQueueResponse enqueueForChunk(UUID userId, Instant startTime, Instant endTime,
                                                       WeatherTargetSource source, int priority) {
        List<WeatherSampleCandidate> candidates = new ArrayList<>();

        long stageStart = metricsStart();
        List<Object[]> stayRows = entityManager.createNativeQuery("""
                SELECT timestamp, stay_duration, ST_Y(location), ST_X(location)
                FROM timeline_stays
                WHERE user_id = ?1
                  AND timestamp <= ?3
                  AND timestamp + (stay_duration * INTERVAL '1 second') >= ?2
                ORDER BY timestamp
                """)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        recordWeatherStage(stageStart, "load_stays", source.name(), "success");
        for (Object[] row : stayRows) {
            Instant stayStart = TimestampUtils.getInstantSafe(row[0]);
            long durationSeconds = ((Number) row[1]).longValue();
            double latitude = ((Number) row[2]).doubleValue();
            double longitude = ((Number) row[3]).doubleValue();
            for (Instant targetAt : samplingPolicy.sampleTimesForStay(stayStart, durationSeconds, startTime, endTime)) {
                candidates.add(new WeatherSampleCandidate(latitude, longitude, targetAt, source, priority));
            }
        }

        stageStart = metricsStart();
        List<Object[]> tripRows = entityManager.createNativeQuery("""
                SELECT timestamp,
                       trip_duration,
                       ST_Y(start_point),
                       ST_X(start_point),
                       ST_Y(end_point),
                       ST_X(end_point)
                FROM timeline_trips
                WHERE user_id = ?1
                  AND timestamp <= ?3
                  AND timestamp + (trip_duration * INTERVAL '1 second') >= ?2
                ORDER BY timestamp
                """)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        recordWeatherStage(stageStart, "load_trips", source.name(), "success");
        for (Object[] row : tripRows) {
            TimelineTripSlice trip = new TimelineTripSlice(
                    TimestampUtils.getInstantSafe(row[0]),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).doubleValue(),
                    ((Number) row[3]).doubleValue(),
                    ((Number) row[4]).doubleValue(),
                    ((Number) row[5]).doubleValue()
            );
            candidates.addAll(tripCandidates(userId, trip, startTime, endTime, source, priority));
        }

        int skipped = 0;
        int validCandidates = 0;
        Map<WeatherTargetKey, WeatherTargetBatchRow> batchRows = new LinkedHashMap<>();
        for (WeatherSampleCandidate candidate : candidates) {
            if (!isValidCoordinate(candidate.latitude(), candidate.longitude())) {
                skipped++;
                continue;
            }

            validCandidates++;
            double latitudeBucket = configurationService.bucketCoordinate(candidate.latitude());
            double longitudeBucket = configurationService.bucketCoordinate(candidate.longitude());
            Instant targetAt = samplingPolicy.truncateToHour(candidate.targetAt());
            WeatherTargetKey key = new WeatherTargetKey(latitudeBucket, longitudeBucket, targetAt);
            batchRows.putIfAbsent(key, new WeatherTargetBatchRow(
                    candidate.latitude(),
                    candidate.longitude(),
                    latitudeBucket,
                    longitudeBucket,
                    targetAt
            ));
        }

        stageStart = metricsStart();
        int created = targetRepository.enqueueMissingBatch(
                userId,
                primaryProviderKey(),
                List.copyOf(batchRows.values()),
                source,
                priority
        );
        recordWeatherStage(stageStart, "enqueue_targets", source.name(), "success");
        return WeatherTargetQueueResponse.builder()
                .targetsCreated(created)
                .targetsAlreadyKnown(Math.max(0, validCandidates - created))
                .targetsSkipped(skipped)
                .build();
    }

    private List<WeatherSampleCandidate> tripCandidates(UUID userId, TimelineTripSlice trip,
                                                        Instant rangeStart, Instant rangeEnd,
                                                        WeatherTargetSource source, int priority) {
        List<WeatherSampleCandidate> result = new ArrayList<>();
        for (Instant targetAt : samplingPolicy.sampleTimesForTrip(
                trip.startTime(), trip.durationSeconds(), rangeStart, rangeEnd)) {
            double[] coordinates = findTripCoordinateAt(userId, trip.startTime(), trip.durationSeconds(), targetAt)
                    .orElseGet(() -> interpolateTripCoordinate(trip, targetAt));
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

        if (sampleRepository.existsAtBucketHour(user.getId(), primaryProviderKey(), latitudeBucket, longitudeBucket, targetAt)) {
            return EnqueueResult.knownResult();
        }

        boolean created = targetRepository.enqueueIfMissing(
                user,
                primaryProviderKey(),
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

    private Optional<double[]> findTripCoordinateAt(UUID userId, TimelineTripEntity trip, Instant targetAt) {
        return findTripCoordinateAt(userId, trip.getTimestamp(), trip.getTripDuration(), targetAt);
    }

    private Optional<double[]> findTripCoordinateAt(UUID userId, Instant tripStart, long durationSeconds, Instant targetAt) {
        long stageStart = metricsStart();
        Optional<double[]> coordinates = backfillReconciliationRepository.findNearestTripCoordinate(
                userId, tripStart, durationSeconds, targetAt);
        recordWeatherStage(stageStart, "trip_coordinate_lookup", "trip", coordinates.isPresent() ? "success" : "empty");
        return coordinates;
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

    private double[] interpolateTripCoordinate(TimelineTripSlice trip, Instant targetAt) {
        long elapsedSeconds = Math.max(0, Math.min(
                trip.durationSeconds(),
                Duration.between(trip.startTime(), targetAt).toSeconds()
        ));
        double ratio = elapsedSeconds / (double) trip.durationSeconds();
        double latitude = trip.startLatitude() + ((trip.endLatitude() - trip.startLatitude()) * ratio);
        double longitude = trip.startLongitude() + ((trip.endLongitude() - trip.startLongitude()) * ratio);
        return new double[]{latitude, longitude};
    }

    private void releaseRemainingClaimedTargets(List<WeatherSampleTargetClaim> targets, int startIndex, Instant retryAt, String reason) {
        for (int i = startIndex; i < targets.size(); i++) {
            targetRepository.releaseUntil(targets.get(i).id(), retryAt, reason);
        }
    }

    private Instant recordProviderFailure(String providerKey, WeatherProviderException e) {
        return switch (e.getKind()) {
            case QUOTA_EXCEEDED -> recordProviderQuotaExceeded(providerKey, e);
            case CONFIG_ERROR -> recordConfigurationError(providerKey, e.getMessage());
            case PROVIDER_UNAVAILABLE -> recordProviderUnavailable(providerKey, e);
            case NO_DATA, INVALID_RESPONSE -> recordProviderUnavailable(providerKey, new WeatherProviderException(
                    WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                    e.getStatusCode(),
                    e.getRetryAfter(),
                    e.getMessage(),
                    e
            ));
        };
    }

    private Instant recordProviderQuotaExceeded(String providerKey, WeatherProviderException e) {
        countQuotaBlock("provider", providerKey);
        Instant retryAt = e.getRetryAfter() != null && e.getRetryAfter().isAfter(Instant.now())
                ? e.getRetryAfter()
                : nextUtcDayStart().plus(INTERNAL_QUOTA_RESET_GRACE);
        return integrationHealthService.recordQuotaExceeded(
                WEATHER_INTEGRATION,
                providerKey,
                ExternalIntegrationHealthStatus.PROVIDER_QUOTA_EXCEEDED,
                errorCode(e),
                e.getMessage(),
                retryAt,
                retryAt
        );
    }

    private Instant recordInternalQuotaExceeded(String message) {
        countQuotaBlock("internal", primaryProviderKey());
        Instant retryAt = nextUtcDayStart().plus(INTERNAL_QUOTA_RESET_GRACE);
        return integrationHealthService.recordQuotaExceeded(
                WEATHER_INTEGRATION,
                primaryProviderKey(),
                ExternalIntegrationHealthStatus.INTERNAL_QUOTA_EXCEEDED,
                "INTERNAL_QUOTA",
                message,
                retryAt,
                retryAt
        );
    }

    private Instant recordProviderUnavailable(String providerKey, WeatherProviderException e) {
        Duration backoff = providerUnavailableBackoff(providerKey);
        Instant retryAt = Instant.now().plus(backoff);
        return integrationHealthService.recordFailure(
                WEATHER_INTEGRATION,
                providerKey,
                ExternalIntegrationHealthStatus.PROVIDER_UNAVAILABLE,
                errorCode(e),
                e.getMessage(),
                retryAt,
                retryAt
        );
    }

    private Instant recordConfigurationError(String providerKey, String message) {
        Instant retryAt = Instant.now().plus(Duration.ofMinutes(15));
        return integrationHealthService.recordFailure(
                WEATHER_INTEGRATION,
                providerKey,
                ExternalIntegrationHealthStatus.CONFIG_ERROR,
                "CONFIG_ERROR",
                message,
                retryAt,
                retryAt
        );
    }

    private Duration providerUnavailableBackoff(String providerKey) {
        int failureCount = integrationHealthService.currentHealth(WEATHER_INTEGRATION, providerKey)
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

    private WeatherTestResponse testProviderConnectionWithSslRetry() {
        int maxAttempts = Math.max(1, 1 + Math.max(0, sslHandshakeRetryAttempts));
        WeatherTestResponse lastResult = null;
        WeatherProviderClient client = providerClient(primaryProviderKey());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            WeatherTestResponse result = client.testConnection(this::reserveConnectionTestCall);
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

    private String primaryProviderKey() {
        return configurationService.primaryProvider();
    }

    private String providerKey(WeatherProviderException e, String fallbackProviderKey) {
        return e.getProviderKey() == null || e.getProviderKey().isBlank()
                ? configurationService.normalizeProviderKey(fallbackProviderKey)
                : e.getProviderKey();
    }

    private WeatherProviderClient providerClient(String providerKey) {
        return providerRegistry.client(providerKey)
                .orElseThrow(() -> new WeatherProviderException(
                        WeatherProviderErrorKind.CONFIG_ERROR,
                        0,
                        null,
                        "Unknown weather provider: " + providerKey,
                        null,
                        providerKey));
    }

    private WeatherProviderException providerExceptionWithProvider(WeatherProviderException e, String providerKey) {
        if (e.getProviderKey() != null && !e.getProviderKey().isBlank()) {
            return e;
        }
        return new WeatherProviderException(
                e.getKind(),
                e.getStatusCode(),
                e.getRetryAfter(),
                e.getMessage(),
                e.getCause(),
                configurationService.normalizeProviderKey(providerKey));
    }

    private boolean allProvidersBlocked(List<String> providerOrder, Instant now) {
        return providerOrder.stream()
                .allMatch(provider -> integrationHealthService.isFetchBlocked(WEATHER_INTEGRATION, provider, now));
    }

    private String allProvidersBlockedReason(List<String> providerOrder, Instant now) {
        return providerOrder.stream()
                .map(provider -> providerFetchBlockedReason(
                        integrationHealthService.currentHealth(WEATHER_INTEGRATION, provider),
                        now))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("All weather providers are blocked by health state");
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

    private synchronized ProviderFetchBatchResult fetchProviderSamples(
            List<WeatherSampleTargetClaim> targets,
            WeatherTargetSource requestSource) {
        WeatherSampleTargetClaim anchor = targets.getFirst();
        List<String> providerOrder = configurationService.providerOrder(anchor.provider());
        if (providerOrder.isEmpty()) {
            throw providerExceptionWithProvider(
                    new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR,
                            "No enabled and configured weather providers are available"),
                    anchor.provider());
        }

        List<Instant> targetHours = targets.stream()
                .map(WeatherSampleTargetClaim::targetAt)
                .map(samplingPolicy::truncateToHour)
                .distinct()
                .sorted()
                .toList();
        WeatherProviderException lastFailure = null;
        for (int providerIndex = 0; providerIndex < providerOrder.size(); providerIndex++) {
            String providerKey = providerOrder.get(providerIndex);
            if (integrationHealthService.isFetchBlocked(WEATHER_INTEGRATION, providerKey, Instant.now())) {
                lastFailure = providerExceptionWithProvider(
                        new WeatherProviderException(WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                                "Weather provider health blocks fetch for " + providerKey),
                        providerKey);
                continue;
            }

            int maxAttempts = Math.max(1, 1 + Math.max(0, sslHandshakeRetryAttempts));
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (!quotaService.tryReserve(
                        requestSource,
                        configurationService.dailyRequestLimit(),
                        configurationService.ongoingReserve())) {
                    throw new InternalQuotaExceededException(requestSource == WeatherTargetSource.ONGOING
                            ? "Daily weather request limit exhausted"
                            : "Daily weather backfill reserve exhausted");
                }

                long providerStart = metricsStart();
                try {
                    WeatherProviderClient client = providerClient(providerKey);
                    Map<Instant, WeatherProviderSample> samples = client.fetchHourlyBatch(
                            anchor.latitude(), anchor.longitude(), targetHours);
                    if (samples == null || samples.isEmpty()) {
                        throw new WeatherProviderException(WeatherProviderErrorKind.NO_DATA,
                                "Provider response did not include any requested hour");
                    }
                    recordProviderRequest(providerKey, requestSource, "success");
                    recordWeatherStage(providerStart, "provider_request", requestSource.name(), "success");
                    integrationHealthService.recordSuccess(WEATHER_INTEGRATION, providerKey);
                    return new ProviderFetchBatchResult(providerKey, samples);
                } catch (WeatherProviderException e) {
                    WeatherProviderException failure = providerExceptionWithProvider(e, providerKey);
                    lastFailure = failure;
                    recordProviderRequest(providerKey, requestSource, e.getKind().name());
                    recordWeatherStage(providerStart, "provider_request", requestSource.name(), e.getKind().name());
                    if (shouldRetrySslHandshakeFailure(e, attempt, maxAttempts)) {
                        log.info("Weather provider SSL handshake failed for a target group; retrying {}/{}",
                                attempt + 1, maxAttempts);
                        continue;
                    }
                    if (providerIndex + 1 < providerOrder.size()) {
                        recordFallbackProviderFailure(providerKey, failure);
                        log.warn("Weather provider {} failed for a {}-target group; trying fallback: kind={}, message={}",
                                providerKey, targets.size(), failure.getKind(), failure.getMessage());
                        break;
                    }
                    throw failure;
                } catch (Exception e) {
                    recordProviderRequest(providerKey, requestSource, "error");
                    recordWeatherStage(providerStart, "provider_request", requestSource.name(), "error");
                    throw e;
                }
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw providerExceptionWithProvider(
                new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR,
                        "No weather provider client is available"), anchor.provider());
    }

    private void recordFallbackProviderFailure(String providerKey, WeatherProviderException failure) {
        if (failure.getKind() == WeatherProviderErrorKind.NO_DATA
                || failure.getKind() == WeatherProviderErrorKind.INVALID_RESPONSE) {
            return;
        }
        recordProviderFailure(providerKey, failure);
    }

    private boolean storeProviderSample(WeatherSampleTargetClaim target, ProviderFetchResult fetchResult) {
        WeatherSampleTargetEntity targetEntity = targetRepository.findById(target.id());
        if (targetEntity == null) {
            return false;
        }

        WeatherProviderSample providerSample = fetchResult.sample();
        Instant observedAt = samplingPolicy.truncateToHour(providerSample.getObservedAt());
        if (sampleRepository.existsAtBucketHour(
                target.userId(),
                fetchResult.providerKey(),
                target.latitudeBucket(),
                target.longitudeBucket(),
                observedAt)) {
            targetRepository.markSkipped(targetEntity, "Weather sample already exists");
            return true;
        }

        WeatherSampleEntity sample = WeatherSampleEntity.builder()
                .user(targetEntity.getUser())
                .provider(fetchResult.providerKey())
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
                .rawData(null)
                .build();

        sampleRepository.persist(sample);
        targetRepository.markCompleted(targetEntity);
        entityManager.flush();
        return true;
    }

    protected <T> T requiringNew(Supplier<T> supplier) {
        return QuarkusTransaction.requiringNew().call(supplier::get);
    }

    private record ProviderFetchResult(String providerKey, WeatherProviderSample sample) {
    }

    private record ProviderFetchBatchResult(String providerKey, Map<Instant, WeatherProviderSample> samples) {
    }

    private static class InternalQuotaExceededException extends RuntimeException {
        private InternalQuotaExceededException(String message) {
            super(message);
        }
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

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordWeatherTimer(String name, long startedAtNanos, String source, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer(name, startedAtNanos,
                "component", "weather",
                "source", normalizeWeatherSource(source),
                "result", result);
    }

    private void recordWeatherStage(long startedAtNanos, String stage, String source, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.weather.stage.duration", startedAtNanos,
                "component", "weather",
                "stage", stage,
                "source", normalizeWeatherSource(source),
                "result", result);
    }

    private void countWeatherTargets(WeatherTargetSource source, String result, long count) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.weather.targets", count,
                "component", "weather",
                "source", source == null ? "ALL" : source.name(),
                "result", result);
    }

    private void countWeatherChunks(String trigger, String result, long count) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.weather.reconciliation.chunks", count,
                "component", "weather",
                "trigger", normalizeWeatherSource(trigger),
                "result", result);
    }

    private void recordProviderRequest(String providerKey, WeatherTargetSource source, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.increment("geopulse.weather.provider.requests",
                "component", "weather",
                "provider", providerKey,
                "source", source.name(),
                "result", result);
    }

    private boolean reserveConnectionTestCall() {
        boolean reserved = quotaService.tryReserveConnectionTest(1, configurationService.dailyRequestLimit());
        if (reserved && workloadMetrics != null) {
            workloadMetrics.increment("geopulse.weather.provider.requests",
                    "component", "weather",
                    "provider", primaryProviderKey(),
                    "source", "CONNECTION_TEST",
                    "result", "attempted");
        }
        return reserved;
    }

    private void countQuotaBlock(String type, String providerKey) {
        if (workloadMetrics != null) {
            workloadMetrics.increment("geopulse.weather.quota.blocks",
                    "component", "weather",
                    "type", type,
                    "provider", providerKey);
        }
    }

    private String normalizeWeatherSource(String source) {
        return source == null || source.isBlank() ? "unknown" : source;
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

    private record WeatherTargetKey(double latitudeBucket, double longitudeBucket, Instant targetAt) {
    }

    private record TimelineTripSlice(
            Instant startTime,
            long durationSeconds,
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
    }

}
