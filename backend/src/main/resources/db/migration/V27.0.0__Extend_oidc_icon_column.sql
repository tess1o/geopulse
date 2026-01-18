-- Extend icon column to accommodate URLs (including selfh.st CDN URLs) and local image paths
-- Matches discovery_url column precedent for URL storage
ALTER TABLE oidc_providers ALTER COLUMN icon TYPE VARCHAR(500);
