-- Add share_location_with_friends column to users table
-- This allows users to temporarily disable sharing their location with all friends
ALTER TABLE users
    ADD COLUMN share_location_with_friends BOOLEAN NOT NULL DEFAULT TRUE;

-- Create index for better query performance when filtering by this column
CREATE INDEX idx_users_share_location ON users (share_location_with_friends);

-- Add comment for documentation
COMMENT
ON COLUMN users.share_location_with_friends IS 'Controls whether user shares their location with friends. Default is TRUE.';

update users
set share_location_with_friends = true;
