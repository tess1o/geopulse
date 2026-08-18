CREATE TABLE weather_daily_request_usage (
    usage_date DATE PRIMARY KEY,
    request_count INTEGER NOT NULL DEFAULT 0,
    ongoing_request_count INTEGER NOT NULL DEFAULT 0,
    backfill_request_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_weather_daily_request_usage_non_negative
        CHECK (request_count >= 0 AND ongoing_request_count >= 0 AND backfill_request_count >= 0),
    CONSTRAINT chk_weather_daily_request_usage_total
        CHECK (request_count = ongoing_request_count + backfill_request_count)
);

