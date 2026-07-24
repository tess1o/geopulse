package org.github.tess1o.geopulse.weather.service;

import org.github.tess1o.geopulse.integration.dto.ExternalIntegrationHealthDto;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.service.ExternalIntegrationHealthService;
import org.github.tess1o.geopulse.weather.client.OpenMeteoWeatherClient;
import org.github.tess1o.geopulse.weather.client.WeatherProviderErrorKind;
import org.github.tess1o.geopulse.weather.client.WeatherProviderException;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetClaim;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    WeatherQuotaService quotaService;

    @Mock
    ExternalIntegrationHealthService integrationHealthService;

    @Mock
    WeatherSampleTargetRepository targetRepository;

    @Mock
    OpenMeteoWeatherClient weatherClient;

    private WeatherService service;

    @BeforeEach
    void setUp() {
        service = new WeatherService();
        service.configurationService = configurationService;
        service.quotaService = quotaService;
        service.integrationHealthService = integrationHealthService;
        service.targetRepository = targetRepository;
        service.weatherClient = weatherClient;
        service.inProgressTimeoutMinutes = 60;

        when(configurationService.isEnabled()).thenReturn(true);
        when(configurationService.isConfigured()).thenReturn(true);
        when(configurationService.dailyRequestLimit()).thenReturn(5);
        when(configurationService.ongoingReserve()).thenReturn(0);
        when(quotaService.requestsUsedToday()).thenReturn(0L);
        when(targetRepository.resetStaleInProgressTargets(any(Instant.class))).thenReturn(0L);
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

        when(targetRepository.claimPendingTargetClaims(5)).thenReturn(List.of(first, second));
        when(weatherClient.fetchCurrent(first.latitude(), first.longitude())).thenThrow(quotaError);
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
        verify(targetRepository, never()).markAttemptStarted(2L);
        verify(weatherClient).fetchCurrent(first.latitude(), first.longitude());
        verify(targetRepository).releaseUntil(1L, retryAfter, "quota exhausted");
        verify(targetRepository).releaseUntil(2L, retryAfter, "quota exhausted");
        verify(targetRepository, never()).markFailedOrRetry(anyLong(), anyString());
    }

    @Test
    void openProviderCircuitDoesNotClaimMoreTargets() {
        when(integrationHealthService.isFetchBlocked(any(), eq(PROVIDER), any(Instant.class))).thenReturn(true);

        int processed = service.fetchQueuedSamples();

        assertThat(processed).isZero();
        verify(targetRepository, never()).claimPendingTargetClaims(anyInt());
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

        when(targetRepository.claimPendingTargetClaims(5)).thenReturn(List.of(first, second));
        when(weatherClient.fetchCurrent(first.latitude(), first.longitude())).thenThrow(unavailable);
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
        verify(targetRepository, never()).markAttemptStarted(2L);
        verify(weatherClient).fetchCurrent(first.latitude(), first.longitude());
    }

    @Test
    void noDataFailureSkipsOnlyThatTargetAndContinuesBatch() {
        allowFetches();
        WeatherSampleTargetClaim first = target(1L);
        WeatherSampleTargetClaim second = target(2L);
        when(targetRepository.claimPendingTargetClaims(5)).thenReturn(List.of(first, second));
        when(weatherClient.fetchCurrent(anyDouble(), anyDouble()))
                .thenThrow(new WeatherProviderException(WeatherProviderErrorKind.NO_DATA, "first no data"))
                .thenThrow(new WeatherProviderException(WeatherProviderErrorKind.NO_DATA, "second no data"));

        int processed = service.fetchQueuedSamples();

        assertThat(processed).isZero();
        verify(targetRepository).markAttemptStarted(1L);
        verify(targetRepository).markAttemptStarted(2L);
        verify(targetRepository).markSkipped(1L, "Weather provider has no data: first no data");
        verify(targetRepository).markSkipped(2L, "Weather provider has no data: second no data");
        verify(targetRepository, never()).releaseUntil(anyLong(), any(Instant.class), anyString());
        verify(integrationHealthService, never()).recordFailure(any(), anyString(), any(), anyString(), anyString(), any(), any());
        verify(integrationHealthService, never()).recordQuotaExceeded(any(), anyString(), any(), anyString(), anyString(), any(), any());
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
}
