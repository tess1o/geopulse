-- Add user time format preference for UI time display (24h, 12h)

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS time_format VARCHAR(16);

UPDATE users
SET time_format = '24h'
WHERE time_format IS NULL
   OR BTRIM(time_format) = '';

ALTER TABLE users
    ALTER COLUMN time_format SET DEFAULT '24h';

ALTER TABLE users
    ALTER COLUMN time_format SET NOT NULL;
