CREATE TABLE weather_samples (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    source VARCHAR(40) NOT NULL,
    requested_latitude DOUBLE PRECISION NOT NULL,
    requested_longitude DOUBLE PRECISION NOT NULL,
    provider_latitude DOUBLE PRECISION,
    provider_longitude DOUBLE PRECISION,
    latitude_bucket DOUBLE PRECISION NOT NULL,
    longitude_bucket DOUBLE PRECISION NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    timezone VARCHAR(100),
    weather_code INTEGER,
    temperature DOUBLE PRECISION,
    apparent_temperature DOUBLE PRECISION,
    humidity DOUBLE PRECISION,
    precipitation DOUBLE PRECISION,
    rain DOUBLE PRECISION,
    snowfall DOUBLE PRECISION,
    cloud_cover DOUBLE PRECISION,
    wind_speed DOUBLE PRECISION,
    wind_gust DOUBLE PRECISION,
    wind_direction DOUBLE PRECISION,
    pressure DOUBLE PRECISION,
    raw_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_weather_samples_user_provider_bucket_time
        UNIQUE (user_id, provider, latitude_bucket, longitude_bucket, observed_at)
);

CREATE INDEX idx_weather_samples_user_observed_at
    ON weather_samples (user_id, observed_at);

CREATE INDEX idx_weather_samples_user_bucket_observed_at
    ON weather_samples (user_id, provider, latitude_bucket, longitude_bucket, observed_at);

CREATE INDEX idx_weather_samples_bbox
    ON weather_samples (user_id, latitude_bucket, longitude_bucket);

CREATE TABLE weather_sample_targets (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    latitude_bucket DOUBLE PRECISION NOT NULL,
    longitude_bucket DOUBLE PRECISION NOT NULL,
    target_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(40) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMPTZ,
    locked_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_weather_targets_user_provider_bucket_time
        UNIQUE (user_id, provider, latitude_bucket, longitude_bucket, target_at)
);

CREATE INDEX idx_weather_targets_status_next_attempt_priority
    ON weather_sample_targets (status, next_attempt_at, priority DESC, target_at DESC);

CREATE INDEX idx_weather_targets_user_target_at
    ON weather_sample_targets (user_id, target_at);

CREATE INDEX idx_weather_targets_last_attempt_at
    ON weather_sample_targets (last_attempt_at);
