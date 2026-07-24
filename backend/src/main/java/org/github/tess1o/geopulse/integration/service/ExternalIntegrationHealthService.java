package org.github.tess1o.geopulse.integration.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.integration.dto.ExternalIntegrationHealthDto;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEvent;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEventType;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthEntity;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;
import org.github.tess1o.geopulse.integration.repository.ExternalIntegrationHealthRepository;

import java.time.Instant;

@ApplicationScoped
public class ExternalIntegrationHealthService {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Inject
    ExternalIntegrationHealthRepository healthRepository;

    @Inject
    Event<ExternalIntegrationHealthEvent> healthEvents;

    @Transactional
    public boolean isFetchBlocked(ExternalIntegrationType integrationType, String providerKey, Instant now) {
        return healthRepository.findByIntegrationAndProvider(integrationType, providerKey)
                .map(health -> health.getStatus() != ExternalIntegrationHealthStatus.HEALTHY)
                .orElse(false);
    }

    @Transactional
    public boolean isProbeDue(ExternalIntegrationType integrationType, String providerKey, Instant now) {
        return healthRepository.findByIntegrationAndProvider(integrationType, providerKey)
                .filter(health -> health.getStatus() != ExternalIntegrationHealthStatus.HEALTHY)
                .map(health -> health.getNextProbeAt() == null || !health.getNextProbeAt().isAfter(now))
                .orElse(false);
    }

    @Transactional
    public ExternalIntegrationHealthDto currentHealth(ExternalIntegrationType integrationType, String providerKey) {
        return toDto(healthRepository.getOrCreate(integrationType, providerKey));
    }

    @Transactional
    public void recordSuccess(ExternalIntegrationType integrationType, String providerKey) {
        ExternalIntegrationHealthEntity health = healthRepository.getOrCreate(integrationType, providerKey);
        ExternalIntegrationHealthStatus previousStatus = health.getStatus();
        Instant previousIncidentStartedAt = health.getIncidentStartedAt();
        Instant now = Instant.now();

        health.setStatus(ExternalIntegrationHealthStatus.HEALTHY);
        health.setIncidentStartedAt(null);
        health.setLastSuccessAt(now);
        health.setCircuitOpenUntil(null);
        health.setNextProbeAt(null);
        health.setLastErrorCode(null);
        health.setLastErrorMessage(null);
        health.setFailureCount(0);

        if (isQuotaStatus(previousStatus) && previousIncidentStartedAt != null) {
            healthEvents.fire(new ExternalIntegrationHealthEvent(
                    ExternalIntegrationHealthEventType.QUOTA_RESTORED,
                    integrationType,
                    providerKey,
                    previousStatus,
                    ExternalIntegrationHealthStatus.HEALTHY,
                    previousIncidentStartedAt,
                    now,
                    null,
                    null,
                    null,
                    null
            ));
        }
    }

    @Transactional
    public void clearInternalQuotaIfRecovered(ExternalIntegrationType integrationType, String providerKey) {
        healthRepository.findByIntegrationAndProvider(integrationType, providerKey)
                .filter(health -> health.getStatus() == ExternalIntegrationHealthStatus.INTERNAL_QUOTA_EXCEEDED)
                .ifPresent(ignored -> recordSuccess(integrationType, providerKey));
    }

    @Transactional
    public Instant recordQuotaExceeded(ExternalIntegrationType integrationType,
                                       String providerKey,
                                       ExternalIntegrationHealthStatus quotaStatus,
                                       String errorCode,
                                       String errorMessage,
                                       Instant circuitOpenUntil,
                                       Instant nextProbeAt) {
        if (!isQuotaStatus(quotaStatus)) {
            throw new IllegalArgumentException("Quota status expected, got " + quotaStatus);
        }

        ExternalIntegrationHealthEntity health = healthRepository.getOrCreate(integrationType, providerKey);
        boolean newIncident = health.getStatus() != quotaStatus || health.getIncidentStartedAt() == null;
        Instant now = Instant.now();
        ExternalIntegrationHealthStatus previousStatus = health.getStatus();
        String limitedErrorCode = limit(errorCode, 80);
        String limitedErrorMessage = limit(errorMessage, MAX_ERROR_LENGTH);
        if (newIncident) {
            health.setIncidentStartedAt(now);
        }

        health.setStatus(quotaStatus);
        health.setLastFailureAt(now);
        health.setLastErrorCode(limitedErrorCode);
        health.setLastErrorMessage(limitedErrorMessage);
        health.setCircuitOpenUntil(circuitOpenUntil);
        health.setNextProbeAt(nextProbeAt);
        health.setFailureCount(health.getFailureCount() + 1);

        if (newIncident) {
            healthEvents.fire(new ExternalIntegrationHealthEvent(
                    ExternalIntegrationHealthEventType.QUOTA_REACHED,
                    integrationType,
                    providerKey,
                    previousStatus,
                    quotaStatus,
                    health.getIncidentStartedAt(),
                    now,
                    limitedErrorCode,
                    limitedErrorMessage,
                    circuitOpenUntil,
                    nextProbeAt
            ));
        }

        return circuitOpenUntil;
    }

    @Transactional
    public Instant recordFailure(ExternalIntegrationType integrationType,
                                 String providerKey,
                                 ExternalIntegrationHealthStatus status,
                                 String errorCode,
                                 String errorMessage,
                                 Instant circuitOpenUntil,
                                 Instant nextProbeAt) {
        ExternalIntegrationHealthEntity health = healthRepository.getOrCreate(integrationType, providerKey);
        Instant now = Instant.now();
        if (health.getStatus() != status || health.getIncidentStartedAt() == null) {
            health.setIncidentStartedAt(now);
        }
        health.setStatus(status);
        health.setLastFailureAt(now);
        health.setLastErrorCode(limit(errorCode, 80));
        health.setLastErrorMessage(limit(errorMessage, MAX_ERROR_LENGTH));
        health.setCircuitOpenUntil(circuitOpenUntil);
        health.setNextProbeAt(nextProbeAt);
        health.setFailureCount(health.getFailureCount() + 1);
        return circuitOpenUntil;
    }

    public ExternalIntegrationHealthDto toDto(ExternalIntegrationHealthEntity health) {
        if (health == null) {
            return null;
        }
        return ExternalIntegrationHealthDto.builder()
                .integrationType(health.getIntegrationType())
                .providerKey(health.getProviderKey())
                .status(health.getStatus())
                .incidentStartedAt(health.getIncidentStartedAt())
                .lastSuccessAt(health.getLastSuccessAt())
                .lastFailureAt(health.getLastFailureAt())
                .lastErrorCode(health.getLastErrorCode())
                .lastErrorMessage(health.getLastErrorMessage())
                .circuitOpenUntil(health.getCircuitOpenUntil())
                .nextProbeAt(health.getNextProbeAt())
                .failureCount(health.getFailureCount())
                .build();
    }

    private boolean isQuotaStatus(ExternalIntegrationHealthStatus status) {
        return status == ExternalIntegrationHealthStatus.PROVIDER_QUOTA_EXCEEDED
                || status == ExternalIntegrationHealthStatus.INTERNAL_QUOTA_EXCEEDED;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
