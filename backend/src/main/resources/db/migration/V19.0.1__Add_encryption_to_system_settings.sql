-- Add encryption key ID column for key rotation support
ALTER TABLE system_settings ADD COLUMN encryption_key_id VARCHAR(20);

-- Add index for encrypted settings queries
CREATE INDEX idx_system_settings_value_type ON system_settings(value_type);
