ALTER TABLE users
    ADD COLUMN IF NOT EXISTS language VARCHAR(16) NOT NULL DEFAULT 'en';

COMMENT ON COLUMN users.language IS
    'BCP 47 language subtag for the UI (en, uk). Stored server-side but applied client-side: the backend never formats anything with it.';
