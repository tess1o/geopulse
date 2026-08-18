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
    private static final int IMPORT_BACKFILL_PRIORITY = 90;
    private static final int ADMIN_PRIORITY = 80;
    private static final int HISTORICAL_BACKFILL_PRIORITY = 70;
    private static final Duration HISTORICAL_BACKFILL_DELAY = Duration.ofHours(2);
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

    @ConfigProperty(name = "geopulse.weather.backfill.discovery.job.interval", defaultValue = "30m")
    String historicalDiscoveryInterval;

    @ConfigProperty(name = "geopulse.weather.sample-fetch.job.interval", defaultValue = "10m")
    String sampleFetchInterval;

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

        Optional<WeatherBackfillReconciliationRepository.TimelineWeatherBounds> bounds =
                backfillReconciliationRepository.findTimelineBounds(userId);
        if (bounds.isEmpty()) {
            return WeatherReconciliationQueueStatus.INVALID_RANGE;
        }
        Instant boundedStart = maxInstant(startTime, bounds.get().start());
        Instant boundedEnd = minInstant(endTime, bounds.get().end());
        if (!boundedEnd.isAfter(boundedStart)) {
            return WeatherReconciliationQueueStatus.INVALID_RANGE;
        }

        backfillReconciliationRepository.enqueue(userId, boundedStart, boundedEnd);
        return WeatherReconciliationQueueStatus.QUEUED;
    }

    /**
     * Reconciles at most {@code maxChunks} persisted ranges. Each chunk covers at most 90
     * days and runs in its own transaction, so memory and transaction lifetime are bounded.
     * Data newer than the historical eligibility delay remains queued for a later run.
     */
    @ActivateRequestContext
    public WeatherBackfillRunResult processPendingHistoricalBackfillChunks(int maxChunks) {
        if (!configurationService.isEnabled() || !configurationService.backfillEnabled()) {
            return new WeatherBackfillRunResult(0, 0, 0, 1, 0);
        }

        QuarkusTransaction.requiringNew()
                .run(backfillReconciliationRepository::normalizePendingRangesToTimelineBounds);

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
        Instant eligibleThrough = Instant.now().minus(HISTORICAL_BACKFILL_DELAY);
        long stageStart = metricsStart();
        WeatherBackfillReconciliation reconciliation = backfillReconciliationRepository.claimNext(
                eligibleThrough,
                HISTORICAL_RECONCILIATION_CHUNK
        );
        recordWeatherStage(stageStart, "claim_chunk", "historical_backfill", reconciliation == null ? "empty" : "success");
        if (reconciliation == null) {
            return null;
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
    public WeatherTargetQueueResponse discoverImportBackfillTargets(UUID userId, Instant startTime, Instant endTime) {
        if (!configurationService.isEnabled() || !configurationService.backfillEnabled()) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }
        if (userId == null || startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
        }

        return enqueueForRange(userId, startTime, endTime, WeatherTargetSource.IMPORT_BACKFILL, IMPORT_BACKFILL_PRIORITY);
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
        if (!configurationService.isEnabled()) {
            log.info("Weather sample fetch skipped: weather integration is disabled");
            return 0;
        }

        if (!configurationService.isConfigured()) {
            Instant retryAt = recordConfigurationError(primaryProviderKey(), "Primary weather provider is not configured");
            log.info("Weather sample fetch skipped: weather provider is not configured; retryAt={}", retryAt);
            return 0;
        }

        long stageStart = metricsStart();
        long resetTargets = resetRecoverableTargetsForRetry();
        recordWeatherStage(stageStart, "retry_reset", "sample_fetch", "success");
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
        List<String> providerOrder = configurationService.providerOrder(primaryProviderKey());
        providerOrder.forEach(provider -> integrationHealthService.clearInternalQuotaIfRecovered(WEATHER_INTEGRATION, provider));

        Instant now = Instant.now();
        if (providerOrder.isEmpty()) {
            Instant retryAt = recordConfigurationError(primaryProviderKey(), "No enabled and configured weather providers are available");
            log.info("Weather sample fetch skipped: no enabled configured weather providers; retryAt={}", retryAt);
            return 0;
        }
        if (allProvidersBlocked(providerOrder, now)) {
            log.info("Weather sample fetch skipped: {}", allProvidersBlockedReason(providerOrder, now));
            return 0;
        }

        stageStart = metricsStart();
        long claimablePendingTargets = targetRepository.countClaimablePendingTargets(now);
        if (claimablePendingTargets <= 0) {
            recordWeatherStage(stageStart, "count_claimable", "sample_fetch", "empty");
            log.info("Weather sample fetch skipped: zero claimable pending targets at {}", now);
            return 0;
        }
        recordWeatherStage(stageStart, "count_claimable", "sample_fetch", "success");

        long claimableBackfillTargets = targetRepository.countClaimablePendingBackfillTargets(now);
        if (backfillAllowed <= 0 && claimableBackfillTargets > 0 && claimableBackfillTargets >= claimablePendingTargets) {
            log.info("Weather sample fetch skipped: daily backfill reserve exhausted; requestsUsedToday={}, "
                            + "dailyLimit={}, ongoingReserve={}, claimablePendingTargets={}, claimableBackfillTargets={}",
                    usedBefore, dailyLimit, configurationService.ongoingReserve(), claimablePendingTargets, claimableBackfillTargets);
            return 0;
        }

        stageStart = metricsStart();
        List<WeatherSampleTargetClaim> targets = targetRepository.claimPendingTargetClaims(limit);
        recordWeatherStage(stageStart, "claim_targets", "sample_fetch", targets.isEmpty() ? "empty" : "success");
        if (targets.isEmpty()) {
            log.info("Weather sample fetch skipped: claimed zero targets from {} claimable pending targets; "
                    + "another worker may have claimed them", claimablePendingTargets);
            return 0;
        }
        countWeatherTargets(null, "claimed", targets.size());

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
                stageStart = metricsStart();
                if (fetchAndStoreTargetWithSslRetry(target)) {
                    recordWeatherStage(stageStart, "provider_fetch", target.source().name(), "success");
                    processed++;
                    countWeatherTargets(target.source(), "processed", 1);
                }
            } catch (WeatherProviderException e) {
                recordWeatherStage(stageStart, "provider_fetch", target.source().name(), e.getKind().name());
                countWeatherTargets(target.source(), "failed", 1);
                ProviderFailureDecision decision = handleProviderFailure(target, e);
                if (decision.stopBatch()) {
                    releaseRemainingClaimedTargets(targets, i + 1, decision.retryAt(), decision.reason());
                    break;
                }
            } catch (Exception e) {
                recordWeatherStage(stageStart, "provider_fetch", target.source().name(), "error");
                countWeatherTargets(target.source(), "failed", 1);
                log.error("Weather target {} failed for user {} at {}: {}",
                        target.id(), target.userId(), target.targetAt(), e.getMessage(), e);
                targetRepository.markFailedOrRetry(target.id(), e.getMessage());
            }
        }
        if (backfillDeferredForReserve > 0) {
            log.info("Weather sample fetch deferred {} backfill targets because the daily backfill reserve is exhausted; "
                            + "requestsUsedToday={}, dailyLimit={}, ongoingReserve={}, backfillAllowed={}",
                    backfillDeferredForReserve, usedBefore, dailyLimit, configurationService.ongoingReserve(), backfillAllowed);
            countWeatherTargets(WeatherTargetSource.HISTORICAL_BACKFILL, "deferred", backfillDeferredForReserve);
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
        long claimableBackfillTargets = targetRepository.countClaimablePendingBackfillTargets(now);
        String primaryProvider = primaryProviderKey();
        List<String> providerOrder = configurationService.providerOrder(primaryProvider);
        ExternalIntegrationHealthDto providerHealth = integrationHealthService.currentHealth(WEATHER_INTEGRATION, primaryProvider);
        return WeatherStatusResponse.builder()
                .enabled(enabled)
                .configured(configured)
                .provider(primaryProvider)
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
                        providerOrder,
                        now))
                .oldestPendingTargetAt(targetRepository.oldestPendingTargetAt())
                .newestPendingTargetAt(targetRepository.newestPendingTargetAt())
                .reconciliation(reconciliationStatus(now))
                .historicalDiscoveryInterval(historicalDiscoveryInterval)
                .sampleFetchInterval(sampleFetchInterval)
                .providerHealth(providerHealth)
                .build();
    }

    @Transactional
    public WeatherReconciliationStatus reconciliationStatus(Instant now) {
        backfillReconciliationRepository.normalizePendingRangesToTimelineBounds();
        Instant eligibleThrough = (now == null ? Instant.now() : now).minus(HISTORICAL_BACKFILL_DELAY);
        return WeatherReconciliationStatus.builder()
                .pendingUserRanges(backfillReconciliationRepository.countPendingUserRanges())
                .eligibleUserRanges(backfillReconciliationRepository.countEligibleUserRanges(eligibleThrough))
                .oldestRangeStart(backfillReconciliationRepository.oldestRangeStart())
                .oldestCursorAt(backfillReconciliationRepository.oldestCursorAt())
                .newestRangeEnd(backfillReconciliationRepository.newestRangeEnd())
                .eligibleThrough(eligibleThrough)
                .build();
    }

    public WeatherTestResponse testProviderConnection() {
        String providerKey = primaryProviderKey();
        WeatherTestResponse result = testProviderConnectionWithSslRetry();
        if (result.isSuccess()) {
            integrationHealthService.recordSuccess(WEATHER_INTEGRATION, providerKey);
            log.info("Weather provider connection test succeeded for {} using forecastUrl={} and archiveUrl={}",
                    providerKey,
                    result.getForecast() == null ? null : result.getForecast().getUrl(),
                    result.getArchive() == null ? null : result.getArchive().getUrl());
        } else {
            log.error("Weather provider connection test failed for {}: message={}, forecast={}, archive={}",
                    providerKey,
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
            recordConfigurationError(primaryProviderKey(), "Primary weather provider is not configured");
            return false;
        }

        long usedBefore = quotaService.requestsUsedToday();
        int dailyLimit = configurationService.dailyRequestLimit();
        if (dailyLimit <= 0 || usedBefore >= dailyLimit) {
            recordInternalQuotaExceeded("GeoPulse weather daily request limit reached");
            return false;
        }
        String providerKey = primaryProviderKey();
        integrationHealthService.clearInternalQuotaIfRecovered(WEATHER_INTEGRATION, providerKey);

        Instant now = Instant.now();
        if (!integrationHealthService.isProbeDue(WEATHER_INTEGRATION, providerKey, now)) {
            return false;
        }

        try {
            WeatherTestResponse result = testProviderConnectionWithSslRetry();
            if (!result.isSuccess()) {
                WeatherProviderException failure = providerExceptionFromTestFailure(result);
                recordProviderFailure(providerKey, failure);
                log.error("Weather provider health probe failed for {}: message={}, forecast={}, archive={}",
                        providerKey,
                        result.getMessage(),
                        endpointTestSummary(result.getForecast()),
                        endpointTestSummary(result.getArchive()));
                return false;
            }
            integrationHealthService.recordSuccess(WEATHER_INTEGRATION, providerKey);
            log.info("Weather provider health probe succeeded for {} using forecast and archive endpoints", providerKey);
            return true;
        } catch (WeatherProviderException e) {
            recordProviderFailure(providerKey, e);
            log.error("Weather provider health probe failed for {}: {}", providerKey, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            recordProviderFailure(providerKey, new WeatherProviderException(
                    WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                    providerKey + " health probe failed: " + e.getMessage(),
                    e
            ));
            log.error("Weather provider health probe failed for {}: {}", providerKey, e.getMessage(), e);
            return false;
        }
    }

    private WeatherTargetQueueResponse enqueueForRange(UUID userId, Instant startTime, Instant endTime,
                                                      WeatherTargetSource source, int priority) {
        if (entityManager.find(UserEntity.class, userId) == null) {
            throw new NotFoundException("User not found: " + userId);
        }

        int created = 0;
        int known = 0;
        int skipped = 0;

        Instant chunkStart = startTime;
        while (endTime.isAfter(chunkStart)) {
            Instant chunkEnd = minInstant(chunkStart.plus(HISTORICAL_RECONCILIATION_CHUNK), endTime);
            WeatherTargetQueueResponse response = enqueueForChunk(userId, chunkStart, chunkEnd, source, priority);
            created += response.getTargetsCreated();
            known += response.getTargetsAlreadyKnown();
            skipped += response.getTargetsSkipped();
            chunkStart = chunkEnd;
        }

        return WeatherTargetQueueResponse.builder()
                .targetsCreated(created)
                .targetsAlreadyKnown(known)
                .targetsSkipped(skipped)
                .build();
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

    private Instant minInstant(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private Instant maxInstant(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
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

        String providerKey = providerKey(e, target.provider());
        Instant retryAt = recordProviderFailure(providerKey, e);
        targetRepository.releaseUntil(targetId, retryAt, e.getMessage());
        log.error("Weather sample fetch stopping after provider failure: targetId={}, userId={}, source={}, "
                        + "targetAt={}, provider={}, kind={}, statusCode={}, retryAt={}, message={}",
                target.id(),
                target.userId(),
                target.source(),
                target.targetAt(),
                providerKey,
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

    private WeatherProviderException providerExceptionFromTestFailure(WeatherTestResponse result) {
        int statusCode = result == null ? 0 : result.getStatusCode();
        WeatherProviderErrorKind kind = statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 404
                ? WeatherProviderErrorKind.CONFIG_ERROR
                : WeatherProviderErrorKind.PROVIDER_UNAVAILABLE;
        String message = result == null || result.getMessage() == null || result.getMessage().isBlank()
                ? "Weather provider health probe failed"
                : result.getMessage();
        return new WeatherProviderException(kind, statusCode, null, message);
    }

    private WeatherTestResponse testProviderConnectionWithSslRetry() {
        int maxAttempts = Math.max(1, 1 + Math.max(0, sslHandshakeRetryAttempts));
        WeatherTestResponse lastResult = null;
        WeatherProviderClient client = providerClient(primaryProviderKey());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            WeatherTestResponse result = client.testConnection();
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
                                      List<String> providerOrder,
                                      Instant now) {
        if (!enabled) {
            return "Weather integration is disabled";
        }
        if (!configured) {
            return "Primary weather provider is not configured";
        }
        if (dailyLimit <= 0 || requestsUsedToday >= dailyLimit) {
            return "Daily weather request limit exhausted";
        }
        if (!providerOrder.isEmpty() && allProvidersBlocked(providerOrder, now)) {
            return allProvidersBlockedReason(providerOrder, now);
        }
        if (providerOrder.isEmpty() && providerHealthBlocksFetch(providerHealth, now)) {
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
        ProviderFetchResult fetchResult = fetchProviderSample(target);

        long storeStart = metricsStart();
        try {
            return requiringNew(() -> storeProviderSample(target, fetchResult));
        } catch (Exception e) {
            Instant observedAt = samplingPolicy.truncateToHour(fetchResult.sample().getObservedAt());
            if (markSkippedIfSampleExists(target, fetchResult.providerKey(), observedAt)) {
                return true;
            }
            throw e;
        } finally {
            recordWeatherStage(storeStart, "store_sample", target.source().name(), "complete");
        }
    }

    private ProviderFetchResult fetchProviderSample(WeatherSampleTargetClaim target) {
        List<String> providerOrder = configurationService.providerOrder(target.provider());
        if (providerOrder.isEmpty()) {
            throw providerExceptionWithProvider(
                    new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR,
                            "No enabled and configured weather providers are available"),
                    target.provider());
        }

        WeatherProviderException lastFailure = null;
        for (int i = 0; i < providerOrder.size(); i++) {
            String providerKey = providerOrder.get(i);
            if (integrationHealthService.isFetchBlocked(WEATHER_INTEGRATION, providerKey, Instant.now())) {
                lastFailure = providerExceptionWithProvider(
                        new WeatherProviderException(WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                                "Weather provider health blocks fetch for " + providerKey),
                        providerKey);
                continue;
            }

            long providerStart = metricsStart();
            try {
                WeatherProviderClient client = providerClient(providerKey);
                WeatherProviderSample sample = target.source() == WeatherTargetSource.ONGOING
                        ? client.fetchCurrent(target.latitude(), target.longitude())
                        : client.fetchHourly(target.latitude(), target.longitude(), target.targetAt());
                recordProviderRequest(providerKey, target.source(), "success");
                recordWeatherStage(providerStart, "provider_request", target.source().name(), "success");
                integrationHealthService.recordSuccess(WEATHER_INTEGRATION, providerKey);
                if (!providerKey.equals(target.provider())) {
                    log.info("Weather provider fallback succeeded: targetId={}, requestedProvider={}, actualProvider={}",
                            target.id(), target.provider(), providerKey);
                }
                return new ProviderFetchResult(providerKey, sample);
            } catch (WeatherProviderException e) {
                WeatherProviderException failure = providerExceptionWithProvider(e, providerKey);
                lastFailure = failure;
                recordProviderRequest(providerKey, target.source(), e.getKind().name());
                recordWeatherStage(providerStart, "provider_request", target.source().name(), e.getKind().name());
                if (i + 1 < providerOrder.size()) {
                    recordFallbackProviderFailure(providerKey, failure);
                    log.warn("Weather provider {} failed for target {}; trying fallback provider: kind={}, message={}",
                            providerKey, target.id(), failure.getKind(), failure.getMessage());
                    continue;
                }
                throw failure;
            } catch (Exception e) {
                recordProviderRequest(providerKey, target.source(), "error");
                recordWeatherStage(providerStart, "provider_request", target.source().name(), "error");
                throw e;
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw providerExceptionWithProvider(
                new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR,
                        "No weather provider client is available"),
                target.provider());
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
                .rawData(providerSample.getRawData())
                .build();

        sampleRepository.persist(sample);
        targetRepository.markCompleted(targetEntity);
        entityManager.flush();
        return true;
    }

    private boolean markSkippedIfSampleExists(WeatherSampleTargetClaim target, String providerKey, Instant observedAt) {
        return requiringNew(() -> {
            if (!sampleRepository.existsAtBucketHour(
                    target.userId(),
                    providerKey,
                    target.latitudeBucket(),
                    target.longitudeBucket(),
                    observedAt)) {
                return false;
            }

            targetRepository.markSkipped(target.id(), "Weather sample already exists");
            return true;
        });
    }

    protected <T> T requiringNew(Supplier<T> supplier) {
        return QuarkusTransaction.requiringNew().call(supplier::get);
    }

    private record ProviderFetchResult(String providerKey, WeatherProviderSample sample) {
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
                "endpoint", source == WeatherTargetSource.ONGOING ? "forecast" : "archive",
                "result", result);
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

    private record ProviderFailureDecision(boolean stopBatch, Instant retryAt, String reason) {
        static ProviderFailureDecision continueBatch() {
            return new ProviderFailureDecision(false, null, null);
        }

        static ProviderFailureDecision stopBatch(Instant retryAt, String reason) {
            return new ProviderFailureDecision(true, retryAt, reason);
        }
    }
}
