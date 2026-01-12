-- Remove redundant share_location_with_friends global setting

DROP INDEX IF EXISTS idx_users_share_location;
ALTER TABLE users DROP COLUMN IF EXISTS share_location_with_friends;