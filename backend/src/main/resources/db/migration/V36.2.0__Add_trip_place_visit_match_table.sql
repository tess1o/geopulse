-- Stores trip plan item to timeline stay matching outcomes for auditability and tuning.

CREATE TABLE trip_place_visit_match (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    plan_item_id BIGINT NOT NULL REFERENCES trip_plan_items(id) ON DELETE CASCADE,
    stay_id BIGINT REFERENCES timeline_stays(id) ON DELETE SET NULL,
    distance_meters DOUBLE PRECISION,
    dwell_seconds BIGINT,
    confidence DOUBLE PRECISION NOT NULL,
    decision VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_trip_place_visit_match_confidence CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE INDEX idx_trip_place_visit_match_trip_plan
    ON trip_place_visit_match(trip_id, plan_item_id, created_at DESC);

