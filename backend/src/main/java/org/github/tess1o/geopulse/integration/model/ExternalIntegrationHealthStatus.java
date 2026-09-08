package org.github.tess1o.geopulse.integration.model;

public enum ExternalIntegrationHealthStatus {
    HEALTHY,
    PROVIDER_QUOTA_EXCEEDED,
    INTERNAL_QUOTA_EXCEEDED,
    PROVIDER_UNAVAILABLE,
    CIRCUIT_OPEN,
    CONFIG_ERROR
}
