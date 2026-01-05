-- Add share_live_location column to user_friend_permissions table
ALTER TABLE user_friend_permissions
ADD COLUMN share_live_location BOOLEAN NOT NULL DEFAULT FALSE;

-- Migrate existing data: set share_live_location based on user's global share_location_with_friends setting
-- For each user_friend_permission record, check if the user has share_location_with_friends enabled
-- If yes, enable share_live_location for all their friends
UPDATE user_friend_permissions ufp
SET share_live_location = TRUE
FROM users u
WHERE ufp.user_id = u.id
  AND u.share_location_with_friends = TRUE;

-- Create index for performance on permission checks
CREATE INDEX idx_user_friend_permissions_live_location
ON user_friend_permissions(user_id, friend_id, share_live_location);

-- Note: The global share_location_with_friends field in users table is kept for backward compatibility
-- but the application will now use per-friend permissions from user_friend_permissions table
