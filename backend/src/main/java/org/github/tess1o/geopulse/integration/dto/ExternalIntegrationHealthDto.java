package org.github.tess1o.geopulse.integration.dto;

import lombok.*;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalIntegrationHealthDto {
    private ExternalIntegrationType integrationType;
    private String providerKey;
    private ExternalIntegrationHealthStatus status;
    private Instant incidentStartedAt;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Instant circuitOpenUntil;
    private Instant nextProbeAt;
    private int failureCount;
}
