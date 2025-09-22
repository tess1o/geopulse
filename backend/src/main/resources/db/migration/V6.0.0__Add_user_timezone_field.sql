-- Add timezone field to users table
-- Default to 'UTC' for all existing and new users
ALTER TABLE users ADD COLUMN timezone VARCHAR(255) NOT NULL DEFAULT 'UTC';

-- Add comment to document the field
COMMENT ON COLUMN users.timezone IS 'User timezone in IANA timezone format (e.g., America/New_York, Europe/London, UTC)';