-- Add encrypted columns to users table for AI settings
ALTER TABLE users ADD COLUMN ai_settings_encrypted TEXT;
ALTER TABLE users ADD COLUMN ai_settings_key_id VARCHAR(50);
