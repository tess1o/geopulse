ALTER TABLE gps_source_config
ADD COLUMN payload_encryption_secret_encrypted TEXT,
ADD COLUMN payload_encryption_secret_key_id VARCHAR(50);
