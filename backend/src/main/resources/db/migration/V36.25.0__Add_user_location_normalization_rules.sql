CREATE TABLE IF NOT EXISTS user_location_normalization_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    source_country VARCHAR(100),
    source_city VARCHAR(200),
    target_country VARCHAR(100),
    target_city VARCHAR(200),
    source_country_norm VARCHAR(100),
    source_city_norm VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_location_normalization_rules_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_location_normalization_rules_type
        CHECK (rule_type IN ('COUNTRY', 'CITY'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_location_norm_rules_country
    ON user_location_normalization_rules (user_id, source_country_norm)
    WHERE rule_type = 'COUNTRY' AND source_country_norm IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_location_norm_rules_city
    ON user_location_normalization_rules (user_id, source_city_norm)
    WHERE rule_type = 'CITY' AND source_city_norm IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_location_norm_rules_user
    ON user_location_normalization_rules (user_id);

COMMENT ON TABLE user_location_normalization_rules IS
    'Per-user normalization rules for country and global city mapping';
