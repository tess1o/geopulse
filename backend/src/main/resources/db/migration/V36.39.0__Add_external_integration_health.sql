CREATE TABLE external_integration_health (
    id BIGSERIAL PRIMARY KEY,
    integration_type VARCHAR(40) NOT NULL,
    provider_key VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'HEALTHY',
    incident_started_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    last_error_message VARCHAR(1000),
    circuit_open_until TIMESTAMPTZ,
    next_probe_at TIMESTAMPTZ,
    failure_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_external_integration_health_type_provider
        UNIQUE (integration_type, provider_key)
);

CREATE INDEX idx_external_integration_health_status_probe
    ON external_integration_health (status, next_probe_at);

CREATE INDEX idx_external_integration_health_circuit_open_until
    ON external_integration_health (circuit_open_until);
