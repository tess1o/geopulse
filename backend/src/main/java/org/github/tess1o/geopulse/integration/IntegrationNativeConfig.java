package org.github.tess1o.geopulse.integration;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.integration.dto.ExternalIntegrationHealthDto;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEvent;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEventType;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthEntity;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;

@RegisterForReflection(targets = {
        ExternalIntegrationHealthEntity.class,
        ExternalIntegrationHealthStatus.class,
        ExternalIntegrationType.class,
        ExternalIntegrationHealthEvent.class,
        ExternalIntegrationHealthEventType.class,
        ExternalIntegrationHealthDto.class,
        ExternalIntegrationHealthDto.ExternalIntegrationHealthDtoBuilder.class
})
public class IntegrationNativeConfig {
}
