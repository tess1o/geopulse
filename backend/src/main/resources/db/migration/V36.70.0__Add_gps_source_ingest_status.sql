-- Latest ingest activity per GPS source, shown on the Location Sources page to help users troubleshoot.
-- One row per source, upserted once per ingest request; kept apart from gps_source_config so the
-- config row is not rewritten on every point.
CREATE TABLE gps_source_ingest_status
(
    source_config_id           UUID        NOT NULL,
    last_received_at           TIMESTAMPTZ,
    last_saved_at              TIMESTAMPTZ,
    last_saved_point_timestamp TIMESTAMPTZ,
    last_rejected_at           TIMESTAMPTZ,
    last_rejection_code        VARCHAR(40),
    last_rejection_params      TEXT,
    last_auth_failure_at       TIMESTAMPTZ,
    last_auth_failure_code     VARCHAR(40),
    CONSTRAINT pk_gps_source_ingest_status PRIMARY KEY (source_config_id),
    CONSTRAINT fk_gps_source_ingest_status_config FOREIGN KEY (source_config_id)
        REFERENCES gps_source_config (id) ON DELETE CASCADE
);
