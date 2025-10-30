-- Add measure_units support for UI (metric, imperial)
-- Allows users to configure what units they want to see in the UI

ALTER TABLE users ADD COLUMN measure_unit VARCHAR(20) DEFAULT 'METRIC';