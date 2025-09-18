-- V5.0.0: Add user_badges table for persistent badge storage
-- This table stores all badges for all users including progress and completion status

CREATE TABLE user_badges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    badge_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    icon VARCHAR(10),
    earned BOOLEAN NOT NULL DEFAULT FALSE,
    earned_date TIMESTAMP,
    progress INTEGER DEFAULT 0,
    current_value INTEGER DEFAULT 0,
    target_value INTEGER,
    last_calculated TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT uk_user_badges_user_badge UNIQUE(user_id, badge_id)
);

CREATE INDEX idx_user_badges_user_id ON user_badges(user_id);
CREATE INDEX idx_user_badges_earned ON user_badges(earned);
CREATE INDEX idx_user_badges_last_calculated ON user_badges(last_calculated);

-- Add trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_user_badges_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER user_badges_updated_at_trigger
    BEFORE UPDATE ON user_badges
    FOR EACH ROW
    EXECUTE FUNCTION update_user_badges_updated_at();