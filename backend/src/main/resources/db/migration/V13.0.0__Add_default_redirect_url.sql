-- Add default_redirect_url support for UI
-- Allows users to configure to which URL they want to be redirected after login

ALTER TABLE users ADD COLUMN default_redirect_url VARCHAR(1000);
