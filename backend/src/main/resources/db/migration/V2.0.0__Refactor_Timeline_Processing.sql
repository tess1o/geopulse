-- V2.0.0: Refactor Timeline Processing
-- This migration supports the new stateless, batch-oriented timeline generation architecture.

-- 1. Add timeline_status to users table for concurrency control
ALTER TABLE users
    ADD COLUMN timeline_status VARCHAR(20) NOT NULL DEFAULT 'IDLE';

ALTER TABLE users
    ADD CONSTRAINT chk_timeline_status CHECK (timeline_status IN ('IDLE', 'PROCESSING', 'REGENERATING'));

CREATE INDEX idx_users_timeline_status ON users (timeline_status);

COMMENT ON COLUMN users.timeline_status IS 'Concurrency lock for timeline generation (IDLE, PROCESSING, REGENERATING).';

-- 2. Drop the obsolete timeline_user_state table
DROP TABLE IF EXISTS timeline_user_state;
