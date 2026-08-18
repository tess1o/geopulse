package org.github.tess1o.geopulse.weather.service;

import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.integration.dto.ExternalIntegrationHealthDto;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.service.ExternalIntegrationHealthService;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.weather.client.OpenMeteoWeatherClient;
import org.github.tess1o.geopulse.weather.client.WeatherProviderErrorKind;
import org.github.tess1o.geopulse.weather.client.WeatherProviderException;
import org.github.tess1o.geopulse.weather.client.WeatherProviderRegistry;
import org.github.tess1o.geopulse.weather.dto.WeatherEndpointTestResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherProviderSample;
import org.github.tess1o.geopulse.weather.dto.WeatherStatusResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherTestResponse;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherSampleTargetEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherBackfillReconciliationRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetClaim;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLHandshakeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WeatherServiceFailureHandlingTest {

    private static final String PROVIDER = WeatherConfigurationService.PROVIDER_OPEN_METEO;

    @Mock
    WeatherConfigurationService configurationService;

    @Mock
    WeatherSamplingPolicy samplingPolicy;

    @Mock
    WeatherQuotaService quotaService;

    @Mock
    ExternalIntegrationHealthService integrationHealthService;

    @Mock
    WeatherSampleTargetRepository targetRepository;

    @Mock
    WeatherSampleRepository sampleRepository;

    @Mock
    WeatherBackfillReconciliationRepository backfillReconciliationRepository;

    @Mock
    OpenMeteoWeatherClient weatherClient;

    @Mock
    OpenMeteoWeatherClient fallbackWeatherClient;

    @Mock
    WeatherProviderRegistry providerRegistry;

    @Mock
    EntityManager entityManager;

    @Mock
    GeoPulseWorkloadMetrics workloadMetrics;

    private WeatherService service;

    @BeforeEach
    void setUp() {
        service = new TestWeatherService();
        service.configurationService = configurationService;
        service.samplingPolicy = samplingPolicy;
        service.quotaService = quotaService;
        service.integrationHealthService = integrationHealthService;
        service.sampleRepository = sampleRepository;
        service.targetRepository = targetRepository;
        service.backfillReconciliationRepository = backfillReconciliationRepository;
        service.providerRegistry = providerRegistry;
        service.entityManager = entityManager;
        service.workloadMetrics = workloadMetrics;
        service.inProgressTimeoutMinutes = 60;
        service.sslHandshakeRetryAttempts = 2;

        lenient().when(configurationService.isEnabled()).thenReturn(true);
        lenient().when(configurationService.isConfigured()).thenReturn(true);
        lenient().when(configurationService.dailyRequestLimit()).thenReturn(5);
        lenient().when(configurationService.ongoingReserve()).thenReturn(0);
        lenient().when(configurationService.primaryProvider()).thenReturn(PROVIDER);
        lenient().when(configurationService.normalizeProviderKey(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(configurationService.providerOrder(anyString())).thenReturn(List.of(PROVIDER));
        lenient().when(providerRegistry.client(PROVIDER)).thenReturn(Optional.of(weatherClient));
        lenient().when(quotaService.requestsUsedToday()).thenReturn(0L);
        lenient().when(quotaService.tryReserve(any(), anyInt(), anyInt())).thenReturn(true);
        lenient().when(quotaService.tryReserveConnectionTest(anyInt(), anyInt())).thenReturn(true);
        lenient().when(targetRepository.resetStaleInProgressTargets(any(Instant.class))).thenReturn(0L);
        lenient().when(targetRepository.hasPendingTargets()).thenReturn(true);
        lenient().when(backfillReconciliationRepository.summary()).thenReturn(
                new WeatherBackfillReconciliationRepository.ReconciliationSummary(0, null, null, null));
        lenient().when(samplingPolicy.truncateToHour(any(Instant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void quotaFailureReleasesWholeClaimedBatchAndStopsFetching() {
        allowFetches();
        WeatherSampleTargetClaim first = target(1L);
        WeatherSampleTargetClaim second = target(2L);
        Instant retryAfter = Instant.now().plusSeconds(3600);
        WeatherProviderException quotaError = new WeatherProviderException(
                WeatherProviderErrorKind.QUOTA_EXCEEDED,
                429,
                retryAfter,
                "quota exhausted");

        when(targetRepository.claimNextTargetGroup(24)).thenReturn(List.of(first, second), List.of());
        when(weatherClient.fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList())).thenThrow(quotaError);
        when(integrationHealthService.recordQuotaExceeded(
                any(),
                eq(PROVIDER),
                eq(ExternalIntegrationHealthStatus.PROVIDER_QUOTA_EXCEEDED),
                eq("HTTP_429"),
                eq("quota exhausted"),
                eq(retryAfter),
                eq(retryAfter)))
                .thenReturn(retryAfter);

        int processed = service.fetchQueuedSamples();

        assertThat(processed).isZero();
        verify(targetRepository).markAttemptStarted(1L);
        verify(targetRepository).markAttemptStarted(2L);
        verify(weatherClient).fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList());
        verify(targetRepository).releaseUntil(1L, retryAfter, "quota exhausted");
        verify(targetRepository).releaseUntil(2L, retryAfter, "quota exhausted");
        verify(targetRepository, never()).markFailedOrRetry(anyLong(), anyString());
        verify(workloadMetrics).increment("geopulse.weather.quota.blocks",
                "component", "weather", "type", "provider", "provider", PROVIDER);
    }

    @Test
    void openProviderCircuitDoesNotClaimMoreTargets() {
        when(integrationHealthService.isFetchBlocked(any(), eq(PROVIDER), any(Instant.class))).thenReturn(true);

        int processed = service.fetchQueuedSamples();

        assertThat(processed).isZero();
        verify(targetRepository, never()).claimNextTargetGroup(anyInt());
        verifyNoInteractions(weatherClient);
    }

    @Test
    void providerUnavailableReleasesRemainingClaimedTargetsUntilBackoffProbe() {
        allowFetches();
        WeatherSampleTargetClaim first = target(1L);
        WeatherSampleTargetClaim second = target(2L);
        WeatherProviderException unavailable = new WeatherProviderException(
                WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                "timeout");

        when(targetRepository.claimNextTargetGroup(24)).thenReturn(List.of(first, second), List.of());
        when(weatherClient.fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList())).thenThrow(unavailable);
        when(integrationHealthService.currentHealth(any(), eq(PROVIDER)))
                .thenReturn(ExternalIntegrationHealthDto.builder().failureCount(0).build());
        when(integrationHealthService.recordFailure(
                any(),
                eq(PROVIDER),
                eq(ExternalIntegrationHealthStatus.PROVIDER_UNAVAILABLE),
                eq("PROVIDER_UNAVAILABLE"),
                eq("timeout"),
                any(Instant.class),
                any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(5));

        int processed = service.fetchQueuedSamples();

        assertThat(processed).isZero();
        ArgumentCaptor<Instant> retryAt = ArgumentCaptor.forClass(Instant.class);
        verify(targetRepository).releaseUntil(eq(1L), retryAt.capture(), eq("timeout"));
        verify(targetRepository).releaseUntil(2L, retryAt.getValue(), "timeout");
        verify(targetRepository).markAttemptStarted(2L);
        verify(weatherClient).fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList());
    }

    @Test
    void noDataFailureSkipsOnlyThatTargetAndContinuesBatch() {
        allowFetches();
        WeatherSampleTargetClaim first = target(1L);
        WeatherSampleTargetClaim second = target(2L);
        when(targetRepository.claimNextTargetGroup(24)).thenReturn(List.of(first, second), List.of());
        when(weatherClient.fetchHourlyBatch(anyDouble(), anyDouble(), anyList()))
                .thenThrow(new WeatherProviderException(WeatherProviderErrorKind.NO_DATA, "batch has no data"));

        int processed = service.fetchQueuedSamples();

        assertThat(processed).isZero();
        verify(targetRepository).markAttemptStarted(1L);
        verify(targetRepository).markAttemptStarted(2L);
        verify(targetRepository).markSkipped(1L, "Weather provider has no data: batch has no data");
        verify(targetRepository).markSkipped(2L, "Weather provider has no data: batch has no data");
        verify(targetRepository, never()).releaseUntil(anyLong(), any(Instant.class), anyString());
        verify(integrationHealthService, never()).recordFailure(any(), anyString(), any(), anyString(), anyString(), any(), any());
        verify(integrationHealthService, never()).recordQuotaExceeded(any(), anyString(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void providerFailureUsesConfiguredFallbackBeforeFailingTarget() {
        allowFetches();
        WeatherSampleTargetClaim first = target(1L);
        WeatherProviderException unavailable = new WeatherProviderException(
                WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                "primary timeout");
        WeatherProviderSample fallbackSample = WeatherProviderSample.builder()
                .requestedLatitude(first.latitude())
                .requestedLongitude(first.longitude())
                .observedAt(first.targetAt())
                .temperature(18.0)
                .build();
        WeatherSampleTargetEntity targetEntity = WeatherSampleTargetEntity.builder()
                .id(first.id())
                .provider(PROVIDER)
                .source(first.source())
                .build();

        when(configurationService.providerOrder(PROVIDER)).thenReturn(List.of(PROVIDER, "PIRATE_WEATHER"));
        when(providerRegistry.client("PIRATE_WEATHER")).thenReturn(Optional.of(fallbackWeatherClient));
        when(targetRepository.claimNextTargetGroup(24)).thenReturn(List.of(first), List.of());
        when(weatherClient.fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList())).thenThrow(unavailable);
        when(fallbackWeatherClient.fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList()))
                .thenReturn(Map.of(first.targetAt(), fallbackSample));
        when(integrationHealthService.currentHealth(any(), eq(PROVIDER)))
                .thenReturn(ExternalIntegrationHealthDto.builder().failureCount(0).build());
        when(integrationHealthService.recordFailure(
                any(),
                eq(PROVIDER),
                eq(ExternalIntegrationHealthStatus.PROVIDER_UNAVAILABLE),
                eq("PROVIDER_UNAVAILABLE"),
                eq("primary timeout"),
                any(Instant.class),
                any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(5));
        when(targetRepository.findById(first.id())).thenReturn(targetEntity);
        when(sampleRepository.existsAtBucketHour(first.userId(), "PIRATE_WEATHER", first.latitudeBucket(), first.longitudeBucket(), first.targetAt()))
                .thenReturn(false);

        int processed = service.fetchQueuedSamples();

        assertThat(processed).isEqualTo(1);
        verify(weatherClient).fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList());
        verify(fallbackWeatherClient).fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList());
        verify(sampleRepository).persist(any(WeatherSampleEntity.class));
        verify(targetRepository).markCompleted(targetEntity);
        verify(targetRepository, never()).markFailedOrRetry(anyLong(), anyString());
        verify(workloadMetrics).increment("geopulse.weather.provider.requests",
                "component", "weather", "provider", PROVIDER,
                "source", WeatherTargetSource.ONGOING.name(),
                "result", WeatherProviderErrorKind.PROVIDER_UNAVAILABLE.name());
        verify(workloadMetrics).increment("geopulse.weather.provider.requests",
                "component", "weather", "provider", "PIRATE_WEATHER",
                "source", WeatherTargetSource.ONGOING.name(),
                "result", "success");
    }

    @Test
    void sslHandshakeFailureRetriesBeforeProviderCircuitFlow() {
        allowFetches();
        WeatherSampleTargetClaim first = target(1L);
        WeatherProviderException sslFailure = new WeatherProviderException(
                WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                "Open-Meteo archive hourly weather request failed",
                new SSLHandshakeException("Failed to create SSL connection"));
        when(targetRepository.claimNextTargetGroup(24)).thenReturn(List.of(first), List.of());
        when(weatherClient.fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList()))
                .thenThrow(sslFailure)
                .thenThrow(new WeatherProviderException(WeatherProviderErrorKind.NO_DATA, "no archive data"));

        int processed = service.fetchQueuedSamples();

        assertThat(processed).isZero();
        verify(weatherClient, times(2)).fetchHourlyBatch(eq(first.latitude()), eq(first.longitude()), anyList());
        verify(targetRepository).markAttemptStarted(1L);
        verify(targetRepository).markSkipped(1L, "Weather provider has no data: no archive data");
        verify(integrationHealthService, never()).recordFailure(any(), anyString(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void successfulProviderConnectionTestClearsProviderHealth() {
        WeatherTestResponse response = WeatherTestResponse.builder()
                .success(true)
                .statusCode(200)
                .provider(PROVIDER)
                .message("Open-Meteo forecast and archive endpoints are reachable")
                .forecast(WeatherEndpointTestResponse.builder().success(true).statusCode(200).url("https://api.open-meteo.com").build())
                .archive(WeatherEndpointTestResponse.builder().success(true).statusCode(200).url("https://archive-api.open-meteo.com").build())
                .build();
        when(weatherClient.testConnection(any(BooleanSupplier.class))).thenReturn(response);

        WeatherTestResponse result = service.testProviderConnection();

        assertThat(result).isSameAs(response);
        verify(integrationHealthService).recordSuccess(any(), eq(PROVIDER));
    }

    @Test
    void successfulProviderConnectionTestRetriesTransientArchiveSslFailureBeforeClearingProviderHealth() {
        WeatherTestResponse sslFailure = WeatherTestResponse.builder()
                .success(false)
                .statusCode(0)
                .provider(PROVIDER)
                .message("Open-Meteo archive endpoint failed: javax.net.ssl.SSLHandshakeException")
                .forecast(WeatherEndpointTestResponse.builder().success(true).statusCode(200).url("https://api.open-meteo.com").build())
                .archive(WeatherEndpointTestResponse.builder()
                        .success(false)
                        .statusCode(0)
                        .url("https://archive-api.open-meteo.com")
                        .message("javax.net.ssl.SSLHandshakeException")
                        .build())
                .build();
        WeatherTestResponse success = WeatherTestResponse.builder()
                .success(true)
                .statusCode(200)
                .provider(PROVIDER)
                .message("Open-Meteo forecast and archive endpoints are reachable")
                .forecast(WeatherEndpointTestResponse.builder().success(true).statusCode(200).url("https://api.open-meteo.com").build())
                .archive(WeatherEndpointTestResponse.builder().success(true).statusCode(200).url("https://archive-api.open-meteo.com").build())
                .build();
        when(weatherClient.testConnection(any(BooleanSupplier.class))).thenReturn(sslFailure, success);

        WeatherTestResponse result = service.testProviderConnection();

        assertThat(result).isSameAs(success);
        verify(weatherClient, times(2)).testConnection(any(BooleanSupplier.class));
        verify(integrationHealthService).recordSuccess(any(), eq(PROVIDER));
    }

    @Test
    void archiveProviderConnectionFailureIsReportedWithoutClearingProviderHealth() {
        WeatherTestResponse response = WeatherTestResponse.builder()
                .success(false)
                .statusCode(0)
                .provider(PROVIDER)
                .message("Open-Meteo archive endpoint failed: javax.net.ssl.SSLHandshakeException")
                .forecast(WeatherEndpointTestResponse.builder().success(true).statusCode(200).url("https://api.open-meteo.com").build())
                .archive(WeatherEndpointTestResponse.builder()
                        .success(false)
                        .statusCode(0)
                        .url("https://archive-api.open-meteo.com")
                        .message("javax.net.ssl.SSLHandshakeException")
                        .build())
                .build();
        when(weatherClient.testConnection(any(BooleanSupplier.class))).thenReturn(response);

        WeatherTestResponse result = service.testProviderConnection();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("archive").contains("SSLHandshakeException");
        assertThat(result.getArchive().getMessage()).contains("SSLHandshakeException");
        verify(integrationHealthService, never()).recordSuccess(any(), eq(PROVIDER));
    }

    @Test
    void statusReportsClaimablePendingTargetsAndProviderHealthWithoutMutatingQueues() {
        Instant circuitOpenUntil = Instant.now().plusSeconds(300);
        ExternalIntegrationHealthDto providerHealth = ExternalIntegrationHealthDto.builder()
                .status(ExternalIntegrationHealthStatus.PROVIDER_UNAVAILABLE)
                .lastErrorCode("PROVIDER_UNAVAILABLE")
                .lastErrorMessage("Open-Meteo archive hourly weather request failed for https://archive-api.open-meteo.com: javax.net.ssl.SSLHandshakeException")
                .circuitOpenUntil(circuitOpenUntil)
                .nextProbeAt(circuitOpenUntil)
                .failureCount(1)
                .build();
        when(configurationService.dailyRequestLimit()).thenReturn(10_000);
        when(configurationService.ongoingReserve()).thenReturn(100);
        when(quotaService.requestsUsedToday()).thenReturn(203L);
        when(targetRepository.countByStatus()).thenReturn(Map.of("PENDING", 4525L));
        when(targetRepository.countClaimablePendingTargets(any(Instant.class))).thenReturn(4525L);
        when(integrationHealthService.currentHealth(any(), eq(PROVIDER))).thenReturn(providerHealth);

        WeatherStatusResponse status = service.status();

        assertThat(status.getClaimablePendingTargets()).isEqualTo(4525);
        assertThat(status.getProviderHealth()).isSameAs(providerHealth);
        verify(backfillReconciliationRepository).summary();
    }

    private WeatherSampleTargetClaim target(long id) {
        return new WeatherSampleTargetClaim(
                id,
                UUID.randomUUID(),
                PROVIDER,
                50.45 + id,
                30.52 + id,
                50.45 + id,
                30.52 + id,
                Instant.parse("2026-07-23T10:00:00Z").plusSeconds(id * 3600),
                WeatherTargetSource.ONGOING
        );
    }

    private void allowFetches() {
        when(integrationHealthService.isFetchBlocked(any(), eq(PROVIDER), any(Instant.class))).thenReturn(false);
    }

    private static class TestWeatherService extends WeatherService {
        @Override
        protected <T> T requiringNew(Supplier<T> supplier) {
            return supplier.get();
        }
    }
}
