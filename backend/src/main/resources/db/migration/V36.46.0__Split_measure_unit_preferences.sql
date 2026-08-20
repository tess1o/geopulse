ALTER TABLE users ADD COLUMN distance_unit VARCHAR(32) DEFAULT 'KILOMETERS';
ALTER TABLE users ADD COLUMN temperature_unit VARCHAR(32) DEFAULT 'CELSIUS';

UPDATE users
SET
    distance_unit = CASE
        WHEN measure_unit = 'IMPERIAL' THEN 'MILES'
        ELSE 'KILOMETERS'
    END,
    temperature_unit = CASE
        WHEN measure_unit = 'IMPERIAL' THEN 'FAHRENHEIT'
        ELSE 'CELSIUS'
    END;

INSERT INTO system_settings (key, value, value_type, category, description, updated_at, updated_by, encryption_key_id)
SELECT
    'system.user.default-distance-unit',
    CASE WHEN value = 'IMPERIAL' THEN 'MILES' ELSE 'KILOMETERS' END,
    value_type,
    category,
    'Default distance unit for newly created users',
    updated_at,
    updated_by,
    encryption_key_id
FROM system_settings
WHERE key = 'system.user.default-measure-unit'
ON CONFLICT (key) DO NOTHING;

INSERT INTO system_settings (key, value, value_type, category, description, updated_at, updated_by, encryption_key_id)
SELECT
    'system.user.default-temperature-unit',
    CASE WHEN value = 'IMPERIAL' THEN 'FAHRENHEIT' ELSE 'CELSIUS' END,
    value_type,
    category,
    'Default temperature unit for newly created users',
    updated_at,
    updated_by,
    encryption_key_id
FROM system_settings
WHERE key = 'system.user.default-measure-unit'
ON CONFLICT (key) DO NOTHING;

DELETE FROM system_settings WHERE key = 'system.user.default-measure-unit';

ALTER TABLE users DROP COLUMN measure_unit;
