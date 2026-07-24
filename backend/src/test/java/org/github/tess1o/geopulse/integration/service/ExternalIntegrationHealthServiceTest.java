package org.github.tess1o.geopulse.integration.service;

import jakarta.enterprise.event.Event;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEvent;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEventType;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthEntity;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;
import org.github.tess1o.geopulse.integration.repository.ExternalIntegrationHealthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ExternalIntegrationHealthServiceTest {

    @Mock
    ExternalIntegrationHealthRepository healthRepository;

    @Mock
    Event<ExternalIntegrationHealthEvent> healthEvents;

    private ExternalIntegrationHealthService service;

    @BeforeEach
    void setUp() {
        service = new ExternalIntegrationHealthService();
        service.healthRepository = healthRepository;
        service.healthEvents = healthEvents;
    }

    @Test
    void recordQuotaExceededFiresGenericQuotaReachedEventOnlyForNewIncident() {
        ExternalIntegrationHealthEntity health = ExternalIntegrationHealthEntity.builder()
                .integrationType(ExternalIntegrationType.WEATHER)
                .providerKey("OPEN_METEO")
                .status(ExternalIntegrationHealthStatus.HEALTHY)
                .failureCount(0)
                .build();
        Instant retryAt = Instant.parse("2026-07-25T00:10:00Z");
        when(healthRepository.getOrCreate(ExternalIntegrationType.WEATHER, "OPEN_METEO")).thenReturn(health);

        service.recordQuotaExceeded(
                ExternalIntegrationType.WEATHER,
                "OPEN_METEO",
                ExternalIntegrationHealthStatus.PROVIDER_QUOTA_EXCEEDED,
                "HTTP_429",
                "quota exhausted",
                retryAt,
                retryAt
        );

        ArgumentCaptor<ExternalIntegrationHealthEvent> eventCaptor = ArgumentCaptor.forClass(ExternalIntegrationHealthEvent.class);
        verify(healthEvents).fire(eventCaptor.capture());
        ExternalIntegrationHealthEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ExternalIntegrationHealthEventType.QUOTA_REACHED);
        assertThat(event.integrationType()).isEqualTo(ExternalIntegrationType.WEATHER);
        assertThat(event.providerKey()).isEqualTo("OPEN_METEO");
        assertThat(event.previousStatus()).isEqualTo(ExternalIntegrationHealthStatus.HEALTHY);
        assertThat(event.currentStatus()).isEqualTo(ExternalIntegrationHealthStatus.PROVIDER_QUOTA_EXCEEDED);
        assertThat(event.errorCode()).isEqualTo("HTTP_429");
        assertThat(event.errorMessage()).isEqualTo("quota exhausted");
        assertThat(event.circuitOpenUntil()).isEqualTo(retryAt);
        assertThat(event.nextProbeAt()).isEqualTo(retryAt);
    }

    @Test
    void recordSuccessFiresGenericQuotaRestoredEventWhenQuotaIncidentWasOpen() {
        Instant incidentStartedAt = Instant.parse("2026-07-24T12:00:00Z");
        ExternalIntegrationHealthEntity health = ExternalIntegrationHealthEntity.builder()
                .integrationType(ExternalIntegrationType.WEATHER)
                .providerKey("OPEN_METEO")
                .status(ExternalIntegrationHealthStatus.PROVIDER_QUOTA_EXCEEDED)
                .incidentStartedAt(incidentStartedAt)
                .failureCount(3)
                .build();
        when(healthRepository.getOrCreate(ExternalIntegrationType.WEATHER, "OPEN_METEO")).thenReturn(health);

        service.recordSuccess(ExternalIntegrationType.WEATHER, "OPEN_METEO");

        ArgumentCaptor<ExternalIntegrationHealthEvent> eventCaptor = ArgumentCaptor.forClass(ExternalIntegrationHealthEvent.class);
        verify(healthEvents).fire(eventCaptor.capture());
        ExternalIntegrationHealthEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ExternalIntegrationHealthEventType.QUOTA_RESTORED);
        assertThat(event.integrationType()).isEqualTo(ExternalIntegrationType.WEATHER);
        assertThat(event.providerKey()).isEqualTo("OPEN_METEO");
        assertThat(event.previousStatus()).isEqualTo(ExternalIntegrationHealthStatus.PROVIDER_QUOTA_EXCEEDED);
        assertThat(event.currentStatus()).isEqualTo(ExternalIntegrationHealthStatus.HEALTHY);
        assertThat(event.incidentStartedAt()).isEqualTo(incidentStartedAt);
        assertThat(event.errorCode()).isNull();
        assertThat(event.errorMessage()).isNull();
    }
}
