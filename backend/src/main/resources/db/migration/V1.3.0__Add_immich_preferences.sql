-- Add Immich integration support to users table
-- Adds immich_preferences JSONB column to store per-user Immich server configuration

-- Add immich_preferences column to users table
ALTER TABLE users 
    ADD COLUMN immich_preferences JSONB;

-- Add comment for documentation
COMMENT ON COLUMN users.immich_preferences IS 'JSON object containing Immich server configuration: serverUrl, apiKey, enabled';