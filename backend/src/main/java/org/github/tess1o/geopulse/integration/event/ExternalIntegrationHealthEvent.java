package org.github.tess1o.geopulse.integration.event;

import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;

import java.time.Instant;

public record ExternalIntegrationHealthEvent(
        ExternalIntegrationHealthEventType eventType,
        ExternalIntegrationType integrationType,
        String providerKey,
        ExternalIntegrationHealthStatus previousStatus,
        ExternalIntegrationHealthStatus currentStatus,
        Instant incidentStartedAt,
        Instant occurredAt,
        String errorCode,
        String errorMessage,
        Instant circuitOpenUntil,
        Instant nextProbeAt
) {
}
