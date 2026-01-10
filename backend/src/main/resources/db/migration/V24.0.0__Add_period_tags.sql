-- Create period_tags table for user-defined time period tagging
-- Allows users to tag date ranges (vacations, trips, etc.) for organizing their timeline

CREATE TABLE period_tags (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tag_name VARCHAR(100) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    source VARCHAR(20) DEFAULT 'manual',
    is_active BOOLEAN DEFAULT false,
    color VARCHAR(7),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Ensure end_time is after start_time (or null for active tags)
    CONSTRAINT chk_period_tags_time_order CHECK (end_time IS NULL OR end_time > start_time)
);

-- Indexes for performance
CREATE INDEX idx_period_tags_user_id ON period_tags(user_id);
CREATE INDEX idx_period_tags_time_range ON period_tags(user_id, start_time, end_time);

-- Unique constraint: only one active tag per user
CREATE UNIQUE INDEX idx_period_tags_user_active ON period_tags(user_id) WHERE is_active = true;

-- Index for active tag lookups
CREATE INDEX idx_period_tags_active ON period_tags(user_id, is_active) WHERE is_active = true;