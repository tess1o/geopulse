-- Trip Workspace core schema
-- Introduces first-class trips and trip plan items while keeping Period Tags as reusable timeline labels.

CREATE TABLE trips (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_tag_id BIGINT REFERENCES period_tags(id) ON DELETE SET NULL,
    name VARCHAR(150) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
    color VARCHAR(7),
    notes VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_trips_time_order CHECK (end_time > start_time),
    CONSTRAINT chk_trips_status CHECK (status IN ('UPCOMING', 'ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_trips_user_status_dates
    ON trips(user_id, status, start_time, end_time);

CREATE INDEX idx_trips_user_start_desc
    ON trips(user_id, start_time DESC);

CREATE INDEX idx_trips_period_tag_id
    ON trips(period_tag_id);

CREATE UNIQUE INDEX uk_trips_period_tag_id
    ON trips(period_tag_id)
    WHERE period_tag_id IS NOT NULL;

CREATE TABLE trip_plan_items (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    notes VARCHAR(2000),
    poi_source VARCHAR(64),
    external_ref VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    planned_day DATE,
    priority VARCHAR(16) NOT NULL DEFAULT 'OPTIONAL',
    order_index INTEGER NOT NULL DEFAULT 0,
    is_visited BOOLEAN NOT NULL DEFAULT FALSE,
    visit_confidence DOUBLE PRECISION,
    visit_source VARCHAR(16),
    visited_at TIMESTAMP WITH TIME ZONE,
    manual_override_state VARCHAR(16),
    replacement_item_id BIGINT REFERENCES trip_plan_items(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_trip_plan_items_priority CHECK (priority IN ('OPTIONAL', 'MUST')),
    CONSTRAINT chk_trip_plan_items_visit_source CHECK (visit_source IS NULL OR visit_source IN ('AUTO', 'MANUAL')),
    CONSTRAINT chk_trip_plan_items_override_state CHECK (manual_override_state IS NULL OR manual_override_state IN ('CONFIRMED', 'REJECTED', 'REPLACED')),
    CONSTRAINT chk_trip_plan_items_confidence CHECK (visit_confidence IS NULL OR (visit_confidence >= 0.0 AND visit_confidence <= 1.0))
);

CREATE INDEX idx_trip_plan_items_trip_order
    ON trip_plan_items(trip_id, order_index, created_at);

CREATE INDEX idx_trip_plan_items_source_ref
    ON trip_plan_items(poi_source, external_ref);
